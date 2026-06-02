package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.entity.TaskTestConfig;
import com.cqie.datafactory.executor.mapper.TaskTestConfigMapper;
import com.cqie.datafactory.executor.service.TaskTestConfigService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TaskTestConfigServiceImpl extends ServiceImpl<TaskTestConfigMapper, TaskTestConfig>
        implements TaskTestConfigService {

    @Override
    public List<TaskTestConfig> listByTaskIdAndVersion(Long taskId, Long versionId) {
        // 兼容老库：task_test_config 还未执行 version_id 升级脚本
        Long hasVersionId = baseMapper.countVersionIdColumn();
        if (hasVersionId == null || hasVersionId == 0) {
            return baseMapper.listLegacyByTaskId(taskId);
        }

        LambdaQueryWrapper<TaskTestConfig> qw = new LambdaQueryWrapper<TaskTestConfig>()
                .eq(TaskTestConfig::getTaskId, taskId);
        if (versionId != null) {
            qw.eq(TaskTestConfig::getVersionId, versionId);
        }
        return list(qw.orderByDesc(TaskTestConfig::getIsDefault)
                .orderByDesc(TaskTestConfig::getCreatedTime));
    }

    @Override
    public TaskTestConfig saveConfig(TaskTestConfig config) {
        Long hasVersionId = baseMapper.countVersionIdColumn();

        // 名称唯一性校验
        LambdaQueryWrapper<TaskTestConfig> query = new LambdaQueryWrapper<TaskTestConfig>()
                .eq(TaskTestConfig::getTaskId, config.getTaskId())
                .eq(TaskTestConfig::getName, config.getName());
        if (hasVersionId != null && hasVersionId > 0) {
            query.eq(TaskTestConfig::getVersionId, config.getVersionId());
        }
        if (config.getId() != null) {
            query.ne(TaskTestConfig::getId, config.getId());
        }
        if (count(query) > 0) {
            throw new BusinessException("配置名称已存在");
        }

        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            // 如果设置为默认，取消该任务其他配置的默认状态
            var resetDefault = update().set("is_default", 0)
                    .eq("task_id", config.getTaskId());
            if (hasVersionId != null && hasVersionId > 0) {
                resetDefault.eq("version_id", config.getVersionId());
            }
            resetDefault.update();
        }
        saveOrUpdate(config);
        return config;
    }

    @Override
    public void deleteConfig(Long id) {
        removeById(id);
    }
}
