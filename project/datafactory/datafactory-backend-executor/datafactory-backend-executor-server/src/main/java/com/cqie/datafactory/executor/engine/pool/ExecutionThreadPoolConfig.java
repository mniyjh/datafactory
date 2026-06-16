package com.cqie.datafactory.executor.engine.pool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class ExecutionThreadPoolConfig {

    @Primary
    @Bean("dagExecutionExecutor")
    public ExecutorService dagExecutionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
