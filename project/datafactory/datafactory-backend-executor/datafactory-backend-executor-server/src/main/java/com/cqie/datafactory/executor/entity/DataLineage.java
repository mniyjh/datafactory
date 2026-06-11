package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_lineage")
public class DataLineage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionId;
    private Long taskId;
    private String sourceNodeId;
    private String targetNodeId;
    private String sourceNodeName;
    private String targetNodeName;
    private String paramCode;
    private String sourceValue;
    private LocalDateTime createdTime;
}
