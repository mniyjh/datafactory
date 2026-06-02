package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExternalApiCreateDTO {
    private String code;
    private String name;
    private String type;
    private String status;
    private String desc;
}
