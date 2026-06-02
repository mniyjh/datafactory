package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component_io_param")
public class ComponentIoParam {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long componentId;
    private String ioType;
    private String paramCode;
    private String paramName;
    private String dataType;
    private Integer requiredFlag;
    private String sourceType;
    private String sourceValue;
    private String defaultValue;
    private String paramSpace;
    private Integer sortOrder;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
