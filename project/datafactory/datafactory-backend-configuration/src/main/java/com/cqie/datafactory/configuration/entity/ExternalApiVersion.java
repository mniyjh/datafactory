package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("external_api_version")
public class ExternalApiVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long apiId;
    private String version;
    private String environment;
    private String dslContent;
    private String requestMethod;
    private String requestUrl;
    private String contentType;
    private String requestHeaders;
    private String queryParams;
    private String requestBody;
    private String authType;
    private String authConfig;
    private Integer timeout;
    private Integer retryCount;
    private String changeLog;
    private Integer isCurrent;
    private Integer publishStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
