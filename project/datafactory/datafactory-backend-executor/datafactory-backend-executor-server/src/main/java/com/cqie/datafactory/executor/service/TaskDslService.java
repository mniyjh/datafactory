package com.cqie.datafactory.executor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.executor.entity.TaskDslEntity;
import com.cqie.datafactory.executor.service.dto.TaskDslCreateDTO;
import com.cqie.datafactory.executor.service.dto.TaskDslPromoteDTO;
import com.cqie.datafactory.executor.service.vo.TaskDslVO;

import java.util.List;

public interface TaskDslService extends IService<TaskDslEntity> {
    Long createVersion(TaskDslCreateDTO dto);

    void updateVersion(Long versionId, TaskDslCreateDTO dto);

    void publish(Long versionId);

    void delete(Long versionId);

    void rollbackToPrev(Long versionId);

    void promote(Long taskId, TaskDslPromoteDTO dto);

    List<TaskDslVO> listByTaskAndEnv(Long taskId, String environment);

    TaskDslVO current(Long taskId, String environment);

    PageResult<TaskDslVO> page(Long taskId, String environment, long current, long size);

    void setCurrent(Long versionId);

    String outdatedNodes(Long taskId, String environment);
}
