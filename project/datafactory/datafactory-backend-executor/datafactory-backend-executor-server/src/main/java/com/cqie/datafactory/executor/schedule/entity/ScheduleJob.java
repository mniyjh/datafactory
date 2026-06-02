package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("schedule_job")
public class ScheduleJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobCode;
    private Long taskId;
    private Long taskVersionId;
    private String cronExpression;
    private String environment;
    private Integer status;
    private String lastExecutionId;
    private LocalDateTime lastFireTime;
    private LocalDateTime nextFireTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getJobCode() { return jobCode; }
    public void setJobCode(String jobCode) { this.jobCode = jobCode; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getTaskVersionId() { return taskVersionId; }
    public void setTaskVersionId(Long taskVersionId) { this.taskVersionId = taskVersionId; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
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
