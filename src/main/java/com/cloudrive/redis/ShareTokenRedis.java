package com.cloudrive.redis;

import com.cloudrive.common.constant.ShareConstants;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 分享令牌 Redis 操作类 
 */
@Component
public class ShareTokenRedis {
    
    private final RedissonClient redissonClient;

    public ShareTokenRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    private String getKey(String shareCode) {
        return ShareConstants.Redis.TOKEN_PREFIX + shareCode;
    }
    
    private RBucket<String> getBucket(String shareCode) {
        return redissonClient.getBucket(getKey(shareCode));
    }
    
    /**
     * 生成并存储分享令牌
     * @param shareCode 分享码
     * @return 生成的令牌
     */
    public String generateAndStoreToken(String shareCode) {
        String token = UUID.randomUUID().toString();
        getBucket(shareCode).set(token, Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return token;
    }
    
    /**
     * 验证分享令牌
     * @param shareCode 分享码
     * @param token 令牌
     * @return 是否有效
     */
    public boolean validateToken(String shareCode, String token) {
        RBucket<String> bucket = getBucket(shareCode);
        String storedToken = bucket.get();
        
        if (storedToken == null || !storedToken.equals(token)) {
            return false;
        }
        
        // 验证成功，延长令牌有效期
        bucket.expire(Duration.ofSeconds(ShareConstants.Token.EXPIRE_TIME));
        return true;
    }
    
    /**
     * 获取分享令牌
     * @param shareCode 分享码
     * @return 令牌
     */
    public String getToken(String shareCode) {
        return getBucket(shareCode).get();
    }
    
    /**
     * 删除分享令牌
     * @param shareCode 分享码
     */
    public void deleteToken(String shareCode) {
        getBucket(shareCode).delete();
    }
} 