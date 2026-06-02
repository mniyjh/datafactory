package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ComponentDefinitionVO {
    private Long id;
    private String componentCode;
    private String componentName;
    private String componentType;
    private Integer status;
    private Integer isSystem;
    private String defaultConfig;
    private String description;
    private LocalDateTime createdTime;
}
