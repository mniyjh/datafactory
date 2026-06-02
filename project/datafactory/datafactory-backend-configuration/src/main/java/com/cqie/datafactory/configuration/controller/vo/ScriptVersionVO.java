package com.cqie.datafactory.configuration.controller.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ScriptVersionVO {
    private Long id;
    private Long scriptId;
    private String version;
    private String status;       // 已发布/未发布
    private String current;      // 是/否 (PROD环境下用于判断是否为当前执行版本)
    private String changeLog;
    private String createdBy;
    private LocalDateTime createdTime;

    // 详细配置
    private String scriptCode;
    private Integer timeout;
    private Integer retryCount;
    private String dependencies;
    private String envVars;
    private String workDir;
    private String interpreterPath;
    private Integer maxMemory;
    private BigDecimal cpuLimit;
}
