package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("external_api")
public class ExternalApi {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String apiCode;
    private String apiName;
    private String apiType;
    private String description;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
