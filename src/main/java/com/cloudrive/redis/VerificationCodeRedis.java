package com.cloudrive.redis;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.cloudrive.common.constant.CommonConstants;

import java.time.Duration;

/**
 * 验证码 Redis 操作类
 */
@Component
public class VerificationCodeRedis {
    private final RedissonClient redissonClient;
    private static final String VERIFICATION_CODE_PREFIX = "verification_code:";

    public VerificationCodeRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private String getKey(String email) {
        return VERIFICATION_CODE_PREFIX + email;
    }

    private RBucket<String> getBucket(String email) {
        return redissonClient.getBucket(getKey(email));
    }

    public void saveVerificationCode(String email, String code) {
        getBucket(email).set(code, Duration.ofMillis(CommonConstants.Time.FIVE_MINUTES));
    }

    public String getVerificationCode(String email) {
        return getBucket(email).get();
    }

    public void deleteVerificationCode(String email) {
        getBucket(email).delete();
    }
}