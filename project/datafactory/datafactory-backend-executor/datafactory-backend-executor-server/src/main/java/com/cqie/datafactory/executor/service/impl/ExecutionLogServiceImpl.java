package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;
import com.cqie.datafactory.executor.mapper.ExecutionLogMapper;
import com.cqie.datafactory.executor.mapper.NodeExecutionLogMapper;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionLogServiceImpl extends ServiceImpl<ExecutionLogMapper, ExecutionLog>
        implements ExecutionLogService {

    private final NodeExecutionLogMapper nodeExecutionLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void sendExecutionLog(ExecutionLog logData) {
        log.info("Saving execution log to DB: executionId={}, status={}", logData.getExecutionId(),
                logData.getStatus());
        try {
            ExecutionLog existing = this.baseMapper.selectOne(new LambdaQueryWrapper<ExecutionLog>()
                    .eq(ExecutionLog::getExecutionId, logData.getExecutionId()));
            if (existing != null) {
                logData.setId(existing.getId());
                this.baseMapper.updateById(logData);
            } else {
                this.baseMapper.insert(logData);
            }
        } catch (Exception e) {
            log.error("Failed to save execution log to DB", e);
        }
    }

    @Override
    public void sendNodeLog(NodeExecutionLog logData) {
        log.info("Saving node log to DB: executionId={}, nodeId={}, status={}",
                logData.getExecutionId(), logData.getNodeId(), logData.getStatus());
        try {
            nodeExecutionLogMapper.insert(logData);
        } catch (Exception e) {
            log.error("Failed to save node log to DB", e);
        }
    }

    @Override
    public List<NodeExecutionLog> listNodeLogs(String executionId) {
        return nodeExecutionLogMapper.selectList(new LambdaQueryWrapper<NodeExecutionLog>()
                .eq(NodeExecutionLog::getExecutionId, executionId)
                .orderByAsc(NodeExecutionLog::getStartTime));
    }

    @Override
    public Page<ExecutionLog> pageLogs(Long taskId, String status, String startTime, String endTime, int current,
            int size) {
        Page<ExecutionLog> page = new Page<>(current, size);
        LambdaQueryWrapper<ExecutionLog> query = new LambdaQueryWrapper<>();
        if (taskId != null)
            query.eq(ExecutionLog::getTaskId, taskId);
        if (StringUtils.hasText(status))
            query.eq(ExecutionLog::getStatus, status);
        if (StringUtils.hasText(startTime))
            query.ge(ExecutionLog::getStartTime, startTime);
        if (StringUtils.hasText(endTime))
            query.le(ExecutionLog::getStartTime, endTime);
        query.orderByDesc(ExecutionLog::getStartTime);
        return this.page(page, query);
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        long total = this.count();
        long success = this.count(new LambdaQueryWrapper<ExecutionLog>().eq(ExecutionLog::getStatus, "SUCCESS"));
        long failure = this.count(new LambdaQueryWrapper<ExecutionLog>().eq(ExecutionLog::getStatus, "FAILURE"));
        long running = this.count(new LambdaQueryWrapper<ExecutionLog>().eq(ExecutionLog::getStatus, "RUNNING"));

        stats.put("total", total);
        stats.put("success", success);
        stats.put("failure", failure);
        stats.put("running", running);
        stats.put("rate", total > 0 ? (double) success / total * 100 : 0);
        return stats;
    }

    @Override
    public Map<String, Object> getTaskStatistics(Long taskId) {
        Map<String, Object> stats = new HashMap<>();
        LambdaQueryWrapper<ExecutionLog> taskWrapper = new LambdaQueryWrapper<ExecutionLog>()
                .eq(ExecutionLog::getTaskId, taskId);
        long total = this.count(taskWrapper);
        long success = this.count(taskWrapper.clone().eq(ExecutionLog::getStatus, "SUCCESS"));
        long failure = this.count(taskWrapper.clone().eq(ExecutionLog::getStatus, "FAILURE"));

        stats.put("taskId", taskId);
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failure", failure);
        stats.put("rate", total > 0 ? (double) success / total * 100 : 0);
        return stats;
    }

    @Override
    public List<Map<String, Object>> getNodeTimeRanking(String executionId) {
        List<NodeExecutionLog> nodes = listNodeLogs(executionId);
        return nodes.stream()
                .sorted((a, b) -> Long.compare(
                        b.getDurationMs() != null ? b.getDurationMs() : 0,
                        a.getDurationMs() != null ? a.getDurationMs() : 0))
                .map(n -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("nodeId", n.getNodeId());
                    item.put("nodeName", n.getNodeName());
                    item.put("nodeType", n.getNodeType());
                    item.put("status", n.getStatus());
                    item.put("durationMs", n.getDurationMs());
                    return item;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public int cleanupOldLogs(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int nodeDeleted = nodeExecutionLogMapper.delete(new LambdaQueryWrapper<NodeExecutionLog>()
                .lt(NodeExecutionLog::getStartTime, cutoff));
        int execDeleted = this.baseMapper.delete(new LambdaQueryWrapper<ExecutionLog>()
                .lt(ExecutionLog::getStartTime, cutoff));
        log.info("日志清理完成: 删除执行日志 {} 条, 节点日志 {} 条, 保留天数={}", execDeleted, nodeDeleted, retentionDays);
        return execDeleted + nodeDeleted;
    }
}
