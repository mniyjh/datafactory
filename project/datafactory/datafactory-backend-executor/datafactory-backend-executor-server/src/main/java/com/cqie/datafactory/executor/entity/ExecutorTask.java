package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("task")
public class ExecutorTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("task_code")
    private String taskCode;
    @TableField("task_name")
    private String taskName;
    private String description;
    private String version;
    private Integer status;
    @TableField("created_by")
    private String createdBy;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_by")
    private String updatedBy;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
