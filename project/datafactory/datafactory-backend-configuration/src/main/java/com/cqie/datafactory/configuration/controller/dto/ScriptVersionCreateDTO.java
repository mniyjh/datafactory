package com.cqie.datafactory.configuration.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ScriptVersionCreateDTO {
    private Long scriptId;
    private String environment;
    private String version;
    private String dslContent;
    private String scriptCode;
    private String scriptCodeContent;
    private Integer timeout;
    private Integer retryCount;
    private String dependencies;
    private String envVars;
    private String workDir;
    private String interpreterPath;
    private Integer maxMemory;
    private BigDecimal cpuLimit;
    private String changeLog;
}
