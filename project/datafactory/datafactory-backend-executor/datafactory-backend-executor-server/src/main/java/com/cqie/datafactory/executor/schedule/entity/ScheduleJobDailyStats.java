package com.cqie.datafactory.executor.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("schedule_job_daily_stats")
public class ScheduleJobDailyStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private LocalDate statDate;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Integer timeoutCount;
    private Integer skipCount;
    private Long avgDurationMs;
    private Long maxDurationMs;
    private Long minDurationMs;
    private LocalDateTime createdTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }

    public Integer getTimeoutCount() { return timeoutCount; }
    public void setTimeoutCount(Integer timeoutCount) { this.timeoutCount = timeoutCount; }

    public Integer getSkipCount() { return skipCount; }
    public void setSkipCount(Integer skipCount) { this.skipCount = skipCount; }

    public Long getAvgDurationMs() { return avgDurationMs; }
    public void setAvgDurationMs(Long avgDurationMs) { this.avgDurationMs = avgDurationMs; }

    public Long getMaxDurationMs() { return maxDurationMs; }
    public void setMaxDurationMs(Long maxDurationMs) { this.maxDurationMs = maxDurationMs; }

    public Long getMinDurationMs() { return minDurationMs; }
    public void setMinDurationMs(Long minDurationMs) { this.minDurationMs = minDurationMs; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
