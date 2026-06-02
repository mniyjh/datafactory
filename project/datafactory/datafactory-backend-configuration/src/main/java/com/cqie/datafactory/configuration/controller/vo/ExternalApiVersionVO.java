package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExternalApiVersionVO {
    private Long id;
    private Long apiId;
    private String version;
    private String environment;
    private String status; // 已发布/未发布
    private String current; // 是/否
    private String remark;
    private String creator;
    private LocalDateTime createdAt;
    private String dsl;
    private String dslContent;
}
