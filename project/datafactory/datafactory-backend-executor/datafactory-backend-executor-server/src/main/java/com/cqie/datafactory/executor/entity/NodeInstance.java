package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("node_instance")
public class NodeInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskDslId;
    private String nodeId;
    private String nodeName;
    private Long componentId;
    private String componentCode;
    private String componentVersion;
    private Integer syncStatus;
    private String deprecatedConfig;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private String nodeType;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
