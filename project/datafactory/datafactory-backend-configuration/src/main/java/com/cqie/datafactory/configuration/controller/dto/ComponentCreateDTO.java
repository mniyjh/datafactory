package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ComponentCreateDTO {
    private String code;
    private String name;
    private String type;
    private String category;
    private String version;
    private String status;
    private String desc;
}
