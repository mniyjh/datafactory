package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

@Data
public class ComponentFieldVO {
    private Long id;
    private Long componentId;
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
