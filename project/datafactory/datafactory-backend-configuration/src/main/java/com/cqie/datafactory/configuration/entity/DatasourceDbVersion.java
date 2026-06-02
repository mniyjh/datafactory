package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("datasource_db_version")
public class DatasourceDbVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dbId;
    private String version;
    private String environment;
    private String dslContent;
    private String dbType;
    private String dbName;
    private String jdbcUrl;
    private String username;
    private String password;
    private String changeLog;
    private Integer isCurrent;
    private Integer publishStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
