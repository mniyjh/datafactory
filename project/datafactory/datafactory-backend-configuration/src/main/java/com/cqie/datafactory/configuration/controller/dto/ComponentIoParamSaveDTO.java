package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentIoParamSaveDTO {
    private Long id;
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
