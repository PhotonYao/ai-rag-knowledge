package top.kangyaocoding.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 描述:
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-17 15:33
 */
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolConfig {
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(properties.getCorePoolSize());
        // 最大线程数
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        // 队列容量
        executor.setQueueCapacity(properties.getBlockQueueSize());
        // 线程存活时间(秒)
        executor.setKeepAliveSeconds((int) (properties.getKeepAliveTime() / 1000));
        // 拒绝策略
        executor.setRejectedExecutionHandler(properties.getRejectedExecutionHandler());
        // 线程名前缀
        executor.setThreadNamePrefix("async-task-");
        // 初始化
        executor.initialize();
        return executor;
    }
}
