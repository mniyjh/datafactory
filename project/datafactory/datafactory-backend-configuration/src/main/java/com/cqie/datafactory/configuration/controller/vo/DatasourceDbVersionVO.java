package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DatasourceDbVersionVO {
    private Long id;
    private Long dbId;
    private String version;
    private String environment;
    private String dslContent;
    private String remark;
    private String current;
    private String status;
    private String creator;
    private LocalDateTime createdAt;
}