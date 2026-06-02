package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("open_api")
public class OpenApi {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String apiCode;
    private String apiName;
    private String apiPath;
    private String apiMethod;
    private Long taskId;
    private String inputSchema;
    private String outputSchema;
    private String authType;
    private Integer limitCount;
    private String appSecret;
    private Integer status;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
