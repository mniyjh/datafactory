package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;          // resource:action 如 task:write
    private String resource;      // 资源: task, datasource, script, schedule, user, monitor, log
    private String action;        // 操作: read, write, execute, delete
    private String description;
    private LocalDateTime createdTime;
}
