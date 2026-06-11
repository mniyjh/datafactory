package com.cqie.datafactory.executor.schedule.event;

import java.time.LocalDateTime;

/**
 * 任务执行成功事件。父任务成功后触发，供依赖监听器唤醒子任务。
 */
public class JobSuccessEvent {

    private final Long jobId;
    private final String environment;
    private final LocalDateTime fireTime;

    public JobSuccessEvent(Long jobId, String environment, LocalDateTime fireTime) {
        this.jobId = jobId;
        this.environment = environment;
        this.fireTime = fireTime;
    }

    public Long getJobId() { return jobId; }
    public String getEnvironment() { return environment; }
    public LocalDateTime getFireTime() { return fireTime; }
}
