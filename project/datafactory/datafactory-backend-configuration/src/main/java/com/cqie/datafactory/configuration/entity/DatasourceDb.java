package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("datasource_db")
public class DatasourceDb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dbCode;
    private String dbName;
    private String dbType;
    private String description;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}