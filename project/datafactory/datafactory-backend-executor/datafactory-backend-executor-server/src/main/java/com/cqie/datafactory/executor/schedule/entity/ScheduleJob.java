package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@TableName("schedule_job")
public class ScheduleJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobCode;
    private String cronExpression;
    private String environment;
    private Integer status;

    // === 新增: 失败重试 ===
    private Integer retryCount;
    private Integer retryInterval;
    private Integer currentRetry;

    // === 新增: 执行超时 ===
    private Integer executorTimeout;

    // === 新增: 并发策略 ===
    private String blockStrategy;
    private Integer maxQueueSize;

    // === 新增: 错过触发策略 ===
    private String misfireStrategy;

    // === 新增: 时间窗口 ===
    private LocalTime windowStart;
    private LocalTime windowEnd;

    // === 新增: 任务依赖 ===
    private Long parentJobId;

    // === 新增: 告警 ===
    private Integer alarmOnFailure;
    private Integer alarmOnTimeout;
    private String alarmEmail;

    // === 参数配置 ===
    private String paramsConfig;

    /** 多任务关联列表（非数据库字段，需调用 loadJobTasks 后才有值） */
    @TableField(exist = false)
    private List<ScheduleJobTask> jobTasks;

    // === 原有: 执行追踪 ===
    private String lastExecutionId;
    private LocalDateTime lastFireTime;
    private LocalDateTime nextFireTime;

    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    // ========== Getters & Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getRetryInterval() { return retryInterval; }
    public void setRetryInterval(Integer retryInterval) { this.retryInterval = retryInterval; }

    public Integer getCurrentRetry() { return currentRetry; }
    public void setCurrentRetry(Integer currentRetry) { this.currentRetry = currentRetry; }

    public Integer getExecutorTimeout() { return executorTimeout; }
    public void setExecutorTimeout(Integer executorTimeout) { this.executorTimeout = executorTimeout; }

    public String getBlockStrategy() { return blockStrategy; }
    public void setBlockStrategy(String blockStrategy) { this.blockStrategy = blockStrategy; }

    public Integer getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(Integer maxQueueSize) { this.maxQueueSize = maxQueueSize; }

    public String getMisfireStrategy() { return misfireStrategy; }
    public void setMisfireStrategy(String misfireStrategy) { this.misfireStrategy = misfireStrategy; }

    public LocalTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalTime windowStart) { this.windowStart = windowStart; }

    public LocalTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalTime windowEnd) { this.windowEnd = windowEnd; }

    public Long getParentJobId() { return parentJobId; }
    public void setParentJobId(Long parentJobId) { this.parentJobId = parentJobId; }

    public Integer getAlarmOnFailure() { return alarmOnFailure; }
    public void setAlarmOnFailure(Integer alarmOnFailure) { this.alarmOnFailure = alarmOnFailure; }

    public Integer getAlarmOnTimeout() { return alarmOnTimeout; }
    public void setAlarmOnTimeout(Integer alarmOnTimeout) { this.alarmOnTimeout = alarmOnTimeout; }

    public String getAlarmEmail() { return alarmEmail; }
    public void setAlarmEmail(String alarmEmail) { this.alarmEmail = alarmEmail; }

    public String getParamsConfig() { return paramsConfig; }
    public void setParamsConfig(String paramsConfig) { this.paramsConfig = paramsConfig; }

    public List<ScheduleJobTask> getJobTasks() { return jobTasks; }
    public void setJobTasks(List<ScheduleJobTask> jobTasks) { this.jobTasks = jobTasks; }

    public String getLastExecutionId() { return lastExecutionId; }
    public void setLastExecutionId(String lastExecutionId) { this.lastExecutionId = lastExecutionId; }

    public LocalDateTime getLastFireTime() { return lastFireTime; }
    public void setLastFireTime(LocalDateTime lastFireTime) { this.lastFireTime = lastFireTime; }

    public LocalDateTime getNextFireTime() { return nextFireTime; }
    public void setNextFireTime(LocalDateTime nextFireTime) { this.nextFireTime = nextFireTime; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
