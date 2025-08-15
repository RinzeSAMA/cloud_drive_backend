package com.cloudrive.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.exception.RateLimitExceededException;
import com.cloudrive.common.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 限流切面
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitAspect {
    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RedissonClient redissonClient;

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("方法 {} 不在 Web 请求上下文中，跳过限流检查。", method.getName());
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        // --- 预取用户信息和 IP ---
        String userId = null;
        boolean isUserLoggedIn = false;
        String clientIp;

        // 获取登录状态和用户ID
        try {
            isUserLoggedIn = StpUtil.isLogin();
            if (isUserLoggedIn) {
                userId = StpUtil.getLoginIdAsString();
            }
        } catch (Exception e) {
            log.warn("获取登录状态或用户ID时出错. 方法: {}. 错误: {}", method.getName(), e.getMessage());
        }
        // 获取客户端IP
        try {
            clientIp = HttpUtil.getClientIp(request);
            if (!StringUtils.hasText(clientIp) || "unknown".equalsIgnoreCase(clientIp)) {
                clientIp = "unknown_ip";
            }
        } catch (Exception e) {
            clientIp = "unknown_ip";
        }

        // --- 确定实际生效维度 ---
        Set<RateLimit.Dimension> effectiveDimensions = new HashSet<>(Arrays.asList(rateLimit.dimensions()));

        // 未登录时采用 IP 维度限制
        if (effectiveDimensions.contains(RateLimit.Dimension.USER) && !isUserLoggedIn) {
            log.debug("用户未登录，跳过方法 {} 的 USER 维度限流检查。", method.getName());
            effectiveDimensions.remove(RateLimit.Dimension.USER);
            effectiveDimensions.add(RateLimit.Dimension.IP);
        }

        if (effectiveDimensions.isEmpty()) {
            log.warn("方法 {} 无生效的限流维度，直接放行。", method.getName());
            return joinPoint.proceed();
        }

        // --- 对每个生效维度进行限流检查 ---
        for (RateLimit.Dimension dimension : effectiveDimensions) {
            String rateLimitKey = generateKey(dimension, method, userId, clientIp);
            boolean acquired;

            try {
                double permitsPerSecond = rateLimit.permitsPerSecond();
                long rateLong = (long) permitsPerSecond;
                if (permitsPerSecond <= 0) {
                    rateLong = CommonConstants.RateLimit.DEFAULT_RATE;
                    log.error("方法 {} 配置了无效的限流速率 {}，使用默认值 {}", method.getName(), rateLimit.permitsPerSecond(), rateLong);
                } else if (rateLimit.permitsPerSecond() < 1.0) {
                    rateLong = 1L;
                    log.debug("方法 {} 配置了小于1的限流速率 {}，向上取整为 1/秒", method.getName(), rateLimit.permitsPerSecond());
                }
                RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimitKey);
                // 强制更新限流器配置
                rateLimiter.trySetRate(RateType.OVERALL, rateLong, 1L, RateIntervalUnit.SECONDS);
                // 尝试获取许可
                log.debug("尝试为 Key '{}' 获取许可. 超时: {} {}", rateLimitKey, rateLimit.timeout(), rateLimit.timeUnit());
                acquired = rateLimiter.tryAcquire(1, rateLimit.timeout(), rateLimit.timeUnit());

            } catch (RedisException e) {
                // 专门处理Redis相关异常
                log.error("执行限流检查时发生 Redis 错误 (Key: {}). 默认阻止请求.", rateLimitKey, e);
                throw new RuntimeException("限流服务异常，请稍后重试。", e);
            } catch (IllegalArgumentException e) {
                // 处理参数验证错误
                log.error("限流配置错误 (Key: {}): {}", rateLimitKey, e.getMessage());
                throw e; // 保留原始异常，允许上层处理
            } catch (Exception e) {
                // 处理所有其他错误
                log.error("执行限流检查时发生未知错误 (Key: {}). 默认阻止请求.", rateLimitKey, e);
                throw new RuntimeException("限流服务发生未知错误，请稍后重试。", e);
            }

            // 如果获取失败，处理限流超出情况
            if (!acquired) {
                return handleRateLimitExceeded(joinPoint, method, rateLimit, dimension);
            } else {
                log.debug("成功获取 Key '{}' 的许可", rateLimitKey);
            }
        }

        // 所有维度检查通过
        return joinPoint.proceed();
    }

    /**
     * 处理限流超出情况
     */
    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, Method method, RateLimit rateLimit, RateLimit.Dimension dimension) throws Throwable {
        log.warn("触发限流: 维度={}, 方法={}", dimension, method.getName()); // 记录更友好的日志

        // 如果有降级方法，则调用
        String fallbackMethodName = rateLimit.fallback();
        if (StringUtils.hasText(fallbackMethodName)) {
            try {
                // 查找与原方法参数类型完全匹配的降级方法
                Method fallbackMethod = joinPoint.getTarget().getClass().getMethod(fallbackMethodName, method.getParameterTypes());
                // 检查返回类型是否兼容 (可选但推荐)
                if (!method.getReturnType().isAssignableFrom(fallbackMethod.getReturnType()) && !fallbackMethod.getReturnType().equals(void.class)) {
                    log.error("降级方法 {} 的返回类型 {} 与原方法 {} 的返回类型 {} 不兼容。", fallbackMethodName, fallbackMethod.getReturnType().getName(), method.getName(), method.getReturnType().getName());
                    // 根据策略决定是抛异常还是继续尝试调用（可能在运行时失败）
                    // 此处选择抛出配置错误
                    throw new IllegalStateException("降级方法返回类型不兼容");
                }
                return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
            } catch (ReflectiveOperationException e) {
                log.error("降级方法调用失败: 方法={}, 错误={}", fallbackMethodName, e.getMessage());
                throw new IllegalStateException("降级方法调用失败，未找到对应的方法或方法访问异常", e);
            }
        }

        // 没有降级方法，或降级方法调用失败（已在上面抛异常），则抛出限流异常
        String message = "请求过于频繁，请稍后再试。[维度: " + dimension + "]";
        try {
            // 查找接受String参数的构造函数
            throw rateLimit.exception().getConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException e) {
            // 回退到默认异常
            throw new RateLimitExceededException(message);
        }
    }

    /**
     * 生成限流 key
     *
     * @param dimension 限流维度
     * @param method    被限流的方法
     * @param userId    用户ID，可能为 null
     * @param clientIp  客户端IP，可能为 "unknown_ip"
     * @return 限流键
     */
    private String generateKey(RateLimit.Dimension dimension, Method method, String userId, String clientIp) {
        StringBuilder keyBuilder = new StringBuilder(CommonConstants.RateLimit.KEY_PREFIX);
        keyBuilder.append(method.getDeclaringClass().getName()).append(":").append(method.getName());
        keyBuilder.append(switch (dimension) {
            case USER -> ":user:" + (userId != null ? userId : CommonConstants.RateLimit.ANONYMOUS_USER);
            case IP -> ":ip:" + (clientIp != null ? clientIp : CommonConstants.RateLimit.UNKNOWN_IP);
            case GLOBAL -> ":global";
            default -> ":global";
        });
        return keyBuilder.toString();
    }
}