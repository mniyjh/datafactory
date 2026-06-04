package com.cqie.datafactory.executor.schedule.event;

import java.time.LocalDateTime;

/**
 * 任务执行失败事件。由 TaskScheduleExecutor / HighFrequencyScheduler
 * 在重试耗尽后发布，供告警监听器消费。
 */
public class JobFailureEvent {

    private final Long jobId;
    private final Long taskId;
    private final String taskName;
    private final String executionId;
    private final String errorMessage;
    private final String environment;
    private final LocalDateTime fireTime;

    public JobFailureEvent(Long jobId, Long taskId, String taskName,
                           String executionId, String errorMessage,
                           String environment, LocalDateTime fireTime) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.taskName = taskName;
        this.executionId = executionId;
        this.errorMessage = errorMessage;
        this.environment = environment;
        this.fireTime = fireTime;
    }

    public Long getJobId() { return jobId; }
    public Long getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public String getExecutionId() { return executionId; }
    public String getErrorMessage() { return errorMessage; }
    public String getEnvironment() { return environment; }
    public LocalDateTime getFireTime() { return fireTime; }
}
