package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_token_blacklist")
public class TokenBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jti;
    private Long userId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
