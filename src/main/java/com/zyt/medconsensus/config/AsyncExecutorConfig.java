package com.zyt.medconsensus.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncExecutorConfig {

    private static final int CORE_POOL_SIZE = 6;
    private static final int MAX_POOL_SIZE = 12;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 50;

    @Bean(name = "reviewerExecutor", destroyMethod = "shutdown")
    public ExecutorService reviewerExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "reviewer-worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
