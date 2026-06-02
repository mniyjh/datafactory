package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

@Data
public class ComponentIoParamVO {
    private Long id;
    private Long componentId;
    private String paramCode;
    private String paramName;
    private String dataType;
    private Integer requiredFlag;
    private String sourceType;
    private String sourceValue;
    private String defaultValue;
    private String paramSpace;
    private Integer sortOrder;
    private String description;
}
