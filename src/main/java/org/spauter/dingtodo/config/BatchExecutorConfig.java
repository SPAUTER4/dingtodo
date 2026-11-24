package org.spauter.dingtodo.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 批量发待办/通知的专用线程池。
 *
 * <p>有界队列 + CallerRuns 拒绝策略：队列满时由提交线程自己执行，宁慢不丢。
 * 并发度默认 5：钉钉接口有限流且 access_token 全局共享，勿盲目调大。
 */
@Configuration
public class BatchExecutorConfig {

    @Bean
    @Qualifier("dingTodoBatchExecutor")
    public Executor dingTodoBatchExecutor(DingTodoProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.batchConcurrency());
        executor.setMaxPoolSize(props.batchConcurrency());
        executor.setQueueCapacity(props.batchQueueCapacity());
        executor.setThreadNamePrefix("dingtodo-batch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
