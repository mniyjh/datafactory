package com.cqie.datafactory.executor.service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExecutorTaskUpdateDTO {
    private String taskName;
    private String description;
    private Integer status;
    private String currentEnv;
    private Long currentVersionId;
}
