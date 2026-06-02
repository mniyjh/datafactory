package com.cqie.datafactory.executor.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskDslCreateDTO {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;
    @NotBlank(message = "环境不能为空")
    private String environment;
    private String version;
    @NotBlank(message = "DSL内容不能为空")
    private String dslContent;
    private String changeLog;
}
