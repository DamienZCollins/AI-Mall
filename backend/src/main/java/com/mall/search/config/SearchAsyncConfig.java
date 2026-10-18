package com.mall.search.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 搜索模块线程池：索引同步与双路召回各用一个有界池，避免占用请求线程
 */
@Configuration
@EnableAsync
public class SearchAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(SearchAsyncConfig.class);

    /** 索引同步线程池，单任务耗时以 ES 写入为主，线程数不需要大 */
    @Bean("productIndexExecutor")
    public ThreadPoolTaskExecutor productIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("product-index-");
        executor.setRejectedExecutionHandler(abortOnReject("product-index"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * 检索召回线程池，一次搜索占用两个任务
     *
     * <p>拒绝策略使用 CallerRunsPolicy：队列满时由请求线程执行该路召回，检索请求不会失败，
     * 代价是并发峰值下退化为串行召回。
     */
    @Bean("searchRecallExecutor")
    public ThreadPoolTaskExecutor searchRecallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("search-recall-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * 容器内出现多个 Executor 后，Spring Boot 不再自动创建 applicationTaskExecutor，
     * 无参 @Async 与异步 MVC 会失去默认执行器；这里补回同名 bean，
     * 线程模型跟随 spring.threads.virtual.enabled，与 Boot 默认行为一致
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor(
            @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreads) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("task-");
        executor.setVirtualThreads(virtualThreads);
        return executor;
    }

    /**
     * 索引任务拒绝时记录错误并丢弃，不阻塞请求线程；漏掉的文档由全量重建接口兜底
     */
    private RejectedExecutionHandler abortOnReject(String poolName) {
        return (task, executor) -> log.error("{} 线程池已满（活跃线程 {}，队列 {}），本次索引同步任务被丢弃，"
                        + "可调用重建接口恢复", poolName, executor.getActiveCount(), executor.getQueue().size());
    }
}
