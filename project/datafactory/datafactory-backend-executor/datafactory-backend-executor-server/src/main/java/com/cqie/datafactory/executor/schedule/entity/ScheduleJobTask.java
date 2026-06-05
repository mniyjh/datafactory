package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("schedule_job_task")
public class ScheduleJobTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scheduleJobId;
    private Long taskId;
    private Long taskVersionId;
    private String environment;
    private Integer sortOrder;
    private String paramsConfig;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    // 非数据库字段，用于传输
    @TableField(exist = false)
    private String taskName;
    @TableField(exist = false)
    private String taskCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScheduleJobId() { return scheduleJobId; }
    public void setScheduleJobId(Long scheduleJobId) { this.scheduleJobId = scheduleJobId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getTaskVersionId() { return taskVersionId; }
    public void setTaskVersionId(Long taskVersionId) { this.taskVersionId = taskVersionId; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getParamsConfig() { return paramsConfig; }
    public void setParamsConfig(String paramsConfig) { this.paramsConfig = paramsConfig; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
}
