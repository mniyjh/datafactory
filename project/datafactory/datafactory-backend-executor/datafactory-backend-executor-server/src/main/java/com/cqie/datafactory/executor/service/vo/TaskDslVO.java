package com.cqie.datafactory.executor.service.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskDslVO {
    private Long id;
    private Long taskId;
    private String version;
    private String environment;
    private String dslContent;
    private String changeLog;
    private Integer isCurrent;
    private Integer envStatus;
    private Integer publishStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
