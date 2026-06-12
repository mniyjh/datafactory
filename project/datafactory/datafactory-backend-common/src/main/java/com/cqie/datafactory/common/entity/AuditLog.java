package com.cqie.datafactory.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    private String url;
    private String params;
    private String ip;
    private Integer status;       // 1=成功 0=失败
    private String errorMsg;
    private Long costMs;
    private LocalDateTime createdTime;
}
