package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_execution_log")
public class NodeExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private Integer retryCount;
    private String inputData;
    private String outputData;
    private String errorMessage;

    // 标准化调试字段
    private String fieldSnapshot;
    private String ioSchema;
    private String edgeFrom;
    private String edgeTo;
    private String componentCode;
    private Long componentId;
    private String componentVersion;
}
