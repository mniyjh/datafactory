package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_io_param_value")
public class NodeIoParamValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeInstanceId;
    private String ioType;
    private String paramCode;
    private String paramName;
    private String dataType;
    private String sourceType;
    private String sourceValue;
    private String paramValue;
    private String paramSpace;
    private String paramSnapshot;
    private Integer sortOrder;
    private Integer deprecatedFlag;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
