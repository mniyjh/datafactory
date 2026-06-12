package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("execution_log")
public class ExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionId;
    private Long taskId;
    private String taskName;
    private String taskVersion;
    private String environment;
    private String status;
    private String triggerType;
    private Long scheduleJobId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String inputParams;
    private String outputResult;
    private String errorMessage;
    private String idempotencyKey;
    private String createdBy;
    private LocalDateTime createdTime;
}
