package com.cqie.datafactory.executor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_test_config")
public class TaskTestConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long versionId;
    private String name;
    private String configMode;
    private String configData;
    private Integer isDefault;

    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
