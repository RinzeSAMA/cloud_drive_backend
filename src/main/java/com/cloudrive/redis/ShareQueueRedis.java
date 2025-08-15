package com.cloudrive.redis;

import com.cloudrive.common.constant.ShareConstants;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 分享延迟队列 Redis 操作类
 */
@Component
public class ShareQueueRedis {
    
    private final RedissonClient redissonClient;

    public ShareQueueRedis(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void addToDelayedQueue(Long shareId, LocalDateTime expireTime) {
        RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        
        long delay = expireTime.toEpochSecond(java.time.ZoneOffset.UTC) - 
                    LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC);
        
        delayedQueue.offer(shareId, delay, TimeUnit.SECONDS);
    }

    public void removeFromDelayedQueue(Long shareId) {
        RBlockingQueue<Long> blockingQueue = redissonClient.getBlockingQueue(ShareConstants.Redis.SHARE_QUEUE);
        RDelayedQueue<Long> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        delayedQueue.remove(shareId);
    }
} 