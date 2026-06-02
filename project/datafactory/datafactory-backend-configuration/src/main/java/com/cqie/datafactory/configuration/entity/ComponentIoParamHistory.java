package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component_io_param_history")
public class ComponentIoParamHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long componentId;
    private String version;
    private String changeType;
    private String paramSnapshot;
    private String changeLog;
    private String createdBy;
    private LocalDateTime createdTime;
}
