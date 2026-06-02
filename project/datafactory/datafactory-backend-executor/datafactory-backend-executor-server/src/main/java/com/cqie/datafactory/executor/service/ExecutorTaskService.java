package com.cqie.datafactory.executor.service;

import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskCreateDTO;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskUpdateDTO;
import com.cqie.datafactory.executor.controller.vo.ExecutorTaskVO;

public interface ExecutorTaskService {
    Long create(ExecutorTaskCreateDTO dto);
    void update(Long id, ExecutorTaskUpdateDTO dto);
    void delete(Long id);
    ExecutorTaskVO detail(Long id);
    PageResult<ExecutorTaskVO> page(long current, long size, String keyword, String status);
    void changeStatus(Long id, Integer status);
    String execute(Long id, java.util.Map<String, Object> params, String environment, String triggerType, Long scheduleJobId);
}
