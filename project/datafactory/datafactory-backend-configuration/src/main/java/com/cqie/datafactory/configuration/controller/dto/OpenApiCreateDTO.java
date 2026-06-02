package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OpenApiCreateDTO {
    private String code;
    private String name;
    private String path;
    private String method;
    private Long taskId;
    private String inputSchema;
    private String outputSchema;
    private String authType;
    private Integer limit;
    private String status;
    private String desc;
}
