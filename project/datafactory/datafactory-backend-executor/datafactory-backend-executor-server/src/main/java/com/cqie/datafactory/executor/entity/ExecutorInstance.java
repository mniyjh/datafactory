package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("executor_instance")
public class ExecutorInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String instanceId;
    private String host;
    private Integer port;
    private String status;        // ONLINE / OFFLINE
    private LocalDateTime lastHeartbeat;
    private LocalDateTime startedAt;
    private Long tenantId;
}
