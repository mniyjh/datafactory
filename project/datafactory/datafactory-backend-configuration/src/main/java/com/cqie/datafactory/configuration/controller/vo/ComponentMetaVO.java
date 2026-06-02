package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ComponentMetaVO {
    private Long componentId;
    private String componentCode;
    private String componentName;
    private List<ComponentFieldVO> fields = new ArrayList<>();
    private List<ComponentIoParamVO> inputParams = new ArrayList<>();
    private List<ComponentIoParamVO> outputParams = new ArrayList<>();
}
