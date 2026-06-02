package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component")
public class Component {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String componentCode;
    private String componentName;
    private String componentType;
    private String category;
    private String version;
    private String description;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
