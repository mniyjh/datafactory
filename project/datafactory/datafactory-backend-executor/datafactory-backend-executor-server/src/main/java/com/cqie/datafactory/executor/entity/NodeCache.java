package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("node_cache")
public class NodeCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nodeHash;
    private Long taskId;
    private String nodeId;
    private String resultJson;
    private Long costMs;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private Long tenantId;
}
