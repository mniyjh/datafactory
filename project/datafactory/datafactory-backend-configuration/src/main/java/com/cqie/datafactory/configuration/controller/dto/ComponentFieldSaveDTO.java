package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentFieldSaveDTO {
    private Long id;
    private String fieldCode;
    private String fieldName;
    private String valueType;
    private String widgetType;
    private String widgetProps;
    private String defaultValue;
    private Integer requiredFlag;
    private Integer sortOrder;
    private String description;
}
