package com.cqie.datafactory.executor.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExecutorTaskVO {
    private Long id;
    private String taskCode;
    private String taskName;
    private String description;
    private Integer status;
    private String statusText;
    private String version;
    private Long currentVersionId;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
