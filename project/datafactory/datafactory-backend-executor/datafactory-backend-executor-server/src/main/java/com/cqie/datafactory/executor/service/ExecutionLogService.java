package com.cqie.datafactory.executor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;

import java.util.List;
import java.util.Map;

public interface ExecutionLogService extends IService<ExecutionLog> {
    void sendExecutionLog(ExecutionLog log);
    void sendNodeLog(NodeExecutionLog log);
    List<NodeExecutionLog> listNodeLogs(String executionId);
    Page<ExecutionLog> pageLogs(Long taskId, String status, String startTime, String endTime, int current, int size);
    Map<String, Object> getStatistics();
    Map<String, Object> getTaskStatistics(Long taskId);
    List<Map<String, Object>> getNodeTimeRanking(String executionId);
    int cleanupOldLogs(int retentionDays);
}
