package com.cqie.datafactory.executor.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskDslPromoteDTO {
    private Long sourceVersionId;
    @NotBlank(message = "来源环境不能为空")
    private String fromEnvironment;
    @NotBlank(message = "目标环境不能为空")
    private String toEnvironment;
    private String changeLog;
}
