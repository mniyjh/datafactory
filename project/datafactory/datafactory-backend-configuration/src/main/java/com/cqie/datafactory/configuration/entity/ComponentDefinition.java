package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component_definition")
public class ComponentDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String componentCode;
    private String componentName;
    private String componentType;
    private Integer status;
    private Integer isSystem;
    private String defaultConfig;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
