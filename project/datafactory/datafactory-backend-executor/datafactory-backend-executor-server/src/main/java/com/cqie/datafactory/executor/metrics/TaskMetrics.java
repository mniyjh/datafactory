package com.cqie.datafactory.executor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** 任务执行指标 — Prometheus 采集 */
@Component
public class TaskMetrics {
    private final Counter executionTotal;
    private final Counter executionSuccess;
    private final Counter executionFailure;
    private final Timer executionDuration;

    public TaskMetrics(MeterRegistry registry) {
        this.executionTotal = Counter.builder("datafactory_executions_total")
                .description("任务执行总次数")
                .tag("type", "task")
                .register(registry);
        this.executionSuccess = Counter.builder("datafactory_executions_success")
                .description("任务执行成功次数")
                .register(registry);
        this.executionFailure = Counter.builder("datafactory_executions_failure")
                .description("任务执行失败次数")
                .register(registry);
        this.executionDuration = Timer.builder("datafactory_execution_duration")
                .description("任务执行耗时")
                .register(registry);
    }

    public void recordSuccess(long durationMs) {
        executionTotal.increment();
        executionSuccess.increment();
        executionDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailure(long durationMs) {
        executionTotal.increment();
        executionFailure.increment();
        executionDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
