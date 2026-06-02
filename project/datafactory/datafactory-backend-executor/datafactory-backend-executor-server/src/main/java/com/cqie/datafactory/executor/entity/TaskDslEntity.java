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
@TableName("task_dsl")
public class TaskDslEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("task_id")
    private Long taskId;
    private String version;
    private String environment;
    @TableField("dsl_content")
    private String dslContent;
    @TableField("change_log")
    private String changeLog;
    @TableField("is_current")
    private Integer isCurrent;
    @TableField("env_status")
    private Integer envStatus;
    @TableField("publish_status")
    private Integer publishStatus;
    @TableField("created_by")
    private String createdBy;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_by")
    private String updatedBy;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
