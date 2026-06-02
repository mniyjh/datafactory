package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OpenApiVO {
    private Long id;
    private String code;
    private String name;
    private String path;
    private String method;
    private Long taskId;
    private String taskName;
    private String inputSchema;
    private String outputSchema;
    private String authType;
    private Integer limit;
    private String status;
    private String desc;
    private String appSecret;
    private LocalDateTime createdAt;
}
