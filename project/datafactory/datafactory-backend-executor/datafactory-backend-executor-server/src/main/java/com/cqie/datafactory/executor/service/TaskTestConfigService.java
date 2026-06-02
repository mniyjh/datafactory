package com.cqie.datafactory.executor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.executor.entity.TaskTestConfig;
import java.util.List;

public interface TaskTestConfigService extends IService<TaskTestConfig> {
    List<TaskTestConfig> listByTaskIdAndVersion(Long taskId, Long versionId);

    TaskTestConfig saveConfig(TaskTestConfig config);

    void deleteConfig(Long id);
}
