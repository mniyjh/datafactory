package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class ComponentIoParamsSaveRequest {
    private List<ComponentIoParamSaveDTO> inputParams = new ArrayList<>();
    private List<ComponentIoParamSaveDTO> outputParams = new ArrayList<>();
}
