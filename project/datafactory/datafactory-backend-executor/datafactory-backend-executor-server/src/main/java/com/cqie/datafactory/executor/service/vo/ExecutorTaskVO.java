package com.cqie.datafactory.executor.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutorTaskVO {
    private Long id;
    private String taskCode;
    private String taskName;
    private String description;
    private String status;
    private String currentEnv;
    private Long currentVersionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
