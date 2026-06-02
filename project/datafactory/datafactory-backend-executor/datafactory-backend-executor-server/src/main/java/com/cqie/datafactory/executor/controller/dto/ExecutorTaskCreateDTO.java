package com.cqie.datafactory.executor.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExecutorTaskCreateDTO {
    @NotBlank(message = "任务编码不能为空")
    private String taskCode;
    @NotBlank(message = "任务名称不能为空")
    private String taskName;
    private String description;
}
