package com.cloudrive.common.annotation;

import com.cloudrive.common.exception.RateLimitExceededException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)// 作用在方法上，限流注解就是要用在controller里面的方法上
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * 限流维度
     */
    enum Dimension {
        USER,    // 用户维度
        IP,      // IP维度
        GLOBAL   // 全局维度
    }

    /**
     * 限流维度
     */
    Dimension[] dimensions() default {Dimension.GLOBAL};

    /**
     * 每秒允许的请求数
     */
    double permitsPerSecond();

    /**
     * 获取令牌的超时时间 (毫秒)
     */
    long timeout() default 0;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 降级方法
     */
    String fallback() default "";

    /**
     * 异常类
     */
    Class<? extends Exception> exception() default RateLimitExceededException.class;
} 