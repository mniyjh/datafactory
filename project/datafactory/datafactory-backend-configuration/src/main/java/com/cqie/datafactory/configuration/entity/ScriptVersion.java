package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("script_version")
public class ScriptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scriptId;
    private String version;
    private String environment;
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
    private Integer isCurrent;
    private Integer publishStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
