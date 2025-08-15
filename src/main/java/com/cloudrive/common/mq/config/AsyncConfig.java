package com.cloudrive.common.mq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 消息队列--生产者发送线程池
 */
@Configuration
public class AsyncConfig {
    @Bean("mqSendExecutor")
    public ThreadPoolTaskExecutor mqSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mq-send-");
        executor.initialize();
        return executor;
    }
}
