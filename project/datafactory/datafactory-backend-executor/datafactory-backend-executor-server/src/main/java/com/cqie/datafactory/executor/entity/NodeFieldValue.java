package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_field_value")
public class NodeFieldValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeInstanceId;
    private String fieldCode;
    private String fieldName;
    private String valueType;
    private String widgetType;
    private String widgetProps;
    private String defaultValue;
    private String fieldValue;
    private String fieldSnapshot;
    private Integer sortOrder;
    private Integer requiredFlag;
    private Integer deprecatedFlag;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
