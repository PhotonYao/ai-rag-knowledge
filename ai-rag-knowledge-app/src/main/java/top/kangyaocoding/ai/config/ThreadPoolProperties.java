package top.kangyaocoding.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 描述:
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-17 15:31
 */
@Data
@ConfigurationProperties(prefix = "thread.pool.executor.config", ignoreInvalidFields = true)
public class ThreadPoolProperties {
    private int corePoolSize;
    private int maxPoolSize;
    private long keepAliveTime;
    private int blockQueueSize;
    private String policy;

    // 获取拒绝策略，转换为RejectedExecutionHandler
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return switch (this.policy) {
            case "AbortPolicy" -> new ThreadPoolExecutor.AbortPolicy();
            case "CallerRunsPolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "DiscardOldestPolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            case "DiscardPolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
    }
}
