package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentDefinitionCreateDTO {
    private String componentCode;
    private String componentName;
    private String componentType;
    private Integer status;
    private Integer isSystem;
    private String defaultConfig;
    private String description;
}
