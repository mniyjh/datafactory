package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component_field")
public class ComponentField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long componentId;
    private String fieldCode;
    private String fieldName;
    private String valueType;
    private String widgetType;
    private String widgetProps;
    private String defaultValue;
    private Integer requiredFlag;
    private Integer sortOrder;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
