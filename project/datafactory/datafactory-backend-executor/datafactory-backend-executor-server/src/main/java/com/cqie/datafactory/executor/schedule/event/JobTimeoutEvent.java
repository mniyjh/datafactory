package com.cqie.datafactory.executor.schedule.event;

import java.time.LocalDateTime;

/**
 * 任务执行超时事件。
 */
public class JobTimeoutEvent {

    private final Long jobId;
    private final Long taskId;
    private final String taskName;
    private final String executionId;
    private final String environment;
    private final int timeoutSeconds;
    private final LocalDateTime startTime;

    public JobTimeoutEvent(Long jobId, Long taskId, String taskName,
                           String executionId, String environment,
                           int timeoutSeconds, LocalDateTime startTime) {
        this.jobId = jobId;
        this.taskId = taskId;
        this.taskName = taskName;
        this.executionId = executionId;
        this.environment = environment;
        this.timeoutSeconds = timeoutSeconds;
        this.startTime = startTime;
    }

    public Long getJobId() { return jobId; }
    public Long getTaskId() { return taskId; }
    public String getTaskName() { return taskName; }
    public String getExecutionId() { return executionId; }
    public String getEnvironment() { return environment; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public LocalDateTime getStartTime() { return startTime; }
}
