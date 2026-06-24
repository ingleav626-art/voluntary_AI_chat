package com.voluntary.chat.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * <p>
 * 为 AI 对话等耗时操作提供独立线程池，避免阻塞 WebSocket 消息处理线程。
 * </p>
 */
@Configuration
public class AsyncConfig {

    /**
     * AI 任务执行器
     *
     * <p>
     * 用于异步执行 AI 对话调用、记忆摘要生成等耗时操作。
     * </p>
     *
     * @return AI 专用线程池
     */
    @Bean("aiTaskExecutor")
    public TaskExecutor aiTaskExecutor() {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
