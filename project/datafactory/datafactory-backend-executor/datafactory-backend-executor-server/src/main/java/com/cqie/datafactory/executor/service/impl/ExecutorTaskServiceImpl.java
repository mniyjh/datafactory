package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.util.CascadeDeleteHelper;
import com.cqie.datafactory.executor.controller.vo.ExecutorTaskVO;
import com.cqie.datafactory.executor.engine.ExecEngine;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;
import com.cqie.datafactory.executor.entity.ExecutorTask;
import com.cqie.datafactory.executor.entity.TaskDslEntity;
import com.cqie.datafactory.executor.mapper.ExecutorTaskMapper;
import com.cqie.datafactory.executor.mapper.DataLineageMapper;
import com.cqie.datafactory.executor.entity.DataLineage;
import com.cqie.datafactory.executor.service.ExecutionLogService;
import com.cqie.datafactory.executor.service.ExecutorTaskService;
import com.cqie.datafactory.executor.service.TaskDslService;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskCreateDTO;
import com.cqie.datafactory.executor.service.dto.ExecutorTaskUpdateDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorTaskServiceImpl extends ServiceImpl<ExecutorTaskMapper, ExecutorTask>
        implements ExecutorTaskService {

    private final ExecutionLogService executionLogService;
    private final TaskDslService taskDslService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ExecEngine execEngine;
    private final DataLineageMapper dataLineageMapper;
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor;

    @Override
    public String execute(Long id, Map<String, Object> params, String environment, String triggerType, Long scheduleJobId) {
        // 1. 获取任务和 DSL 配置
        ExecutorTask task = requireTask(id);
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new BusinessException("任务已禁用，无法执行");
        }

        TaskDslEntity dsl = resolveDslForExecution(id, environment, params);

        // 2. 解析并验证必填参数
        JsonNode dslNode;
        try {
            dslNode = objectMapper.readTree(dsl.getDslContent());
            JsonNode nodesNode = dslNode.get("nodes");
            if (nodesNode != null && nodesNode.isArray()) {
                validateRequiredParameters(nodesNode, params);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("DSL 解析失败: " + e.getMessage());
        }

        // 2.5. 测试环境自动注入测试配置
        final Map<String, Object> mergedParams;
        if ("TEST".equalsIgnoreCase(environment)) {
            Map<String, Object> testConfig = loadTestConfigParams(id);
            if (params != null && !params.isEmpty()) {
                testConfig.putAll(params);
            }
            // 拍平按节点分组的参数 {nodeId:{a:"5",b:"3"}} → {a:"5",b:"3"}
            Map<String, Object> raw = testConfig.isEmpty() ? (params != null ? params : new HashMap<>()) : testConfig;
            mergedParams = flattenNodeParams(raw);
        } else {
            mergedParams = params;
        }

        // 3. 生成执行ID并准备异步执行
        String executionId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        taskExecutor.execute(() -> {
            try {
                // 创建并保存开始执行日志
                ExecutionLog logData = new ExecutionLog();
                logData.setExecutionId(executionId);
                logData.setTaskId(task.getId());
                logData.setTaskName(task.getTaskName());
                logData.setTaskVersion(dsl.getVersion());
                logData.setEnvironment(environment);
                logData.setStatus("RUNNING");
                logData.setTriggerType(triggerType);
                if (scheduleJobId != null) {
                    logData.setScheduleJobId(scheduleJobId);
                }
                logData.setStartTime(LocalDateTime.now());
                logData.setCreatedBy("admin");
                try {
                    logData.setInputParams(objectMapper.writeValueAsString(mergedParams));
                } catch (Exception ignored) {
                }

                executionLogService.sendExecutionLog(logData);

                // 构建节点连线索引（用于标准化 edgeFrom/edgeTo）
                JsonNode dslNodeForEdges = objectMapper.readTree(dsl.getDslContent());
                JsonNode edgesNode = dslNodeForEdges.get("edges");
                Map<String, List<String>> incomingEdgeMap = new HashMap<>();
                Map<String, List<String>> outgoingEdgeMap = new HashMap<>();
                if (edgesNode != null && edgesNode.isArray()) {
                    edgesNode.forEach(e -> {
                        String source = resolveEdgeNodeId(e, "source", "from");
                        String target = resolveEdgeNodeId(e, "target", "to");
                        if (source != null && !source.isBlank() && target != null && !target.isBlank()) {
                            outgoingEdgeMap.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                            incomingEdgeMap.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
                        }
                    });
                }

                // 委托 ExecEngine 执行所有节点
                Map<String, Object> finalOutput = execEngine.execute(
                        dsl.getDslContent(),
                        environment,
                        mergedParams,
                        record -> {
                            NodeExecutionLog nodeLog = new NodeExecutionLog();
                            nodeLog.setExecutionId(executionId);
                            nodeLog.setNodeId(record.nodeId);
                            nodeLog.setNodeName(record.nodeName);
                            nodeLog.setNodeType(record.nodeType);
                            nodeLog.setComponentCode(record.componentCode);
                            nodeLog.setStatus(record.status);
                            nodeLog.setStartTime(LocalDateTime.now());
                            nodeLog.setEndTime(LocalDateTime.now());
                            nodeLog.setDurationMs(record.durationMs);
                            nodeLog.setRetryCount(record.retryCount);
                            nodeLog.setEdgeFrom(safeWriteJson(incomingEdgeMap.getOrDefault(record.nodeId, Collections.emptyList())));
                            nodeLog.setEdgeTo(safeWriteJson(outgoingEdgeMap.getOrDefault(record.nodeId, Collections.emptyList())));
                            if (record.errorMessage != null) {
                                nodeLog.setErrorMessage(record.errorMessage);
                            }
                            try {
                                nodeLog.setOutputData(objectMapper.writeValueAsString(record.outputs));
                            } catch (Exception ignored) {
                            }
                            executionLogService.sendNodeLog(nodeLog);
                        }
                );

                logData.setStatus("SUCCESS");
                logData.setOutputResult(safeWriteJson(finalOutput));
                finalizeLog(logData);

                // 记录数据血缘
                recordDataLineage(executionId, id, dsl.getDslContent());

            } catch (Exception e) {
                log.error("Async execution failed for executionId: {}", executionId, e);
                markExecutionFailed(executionId, e);
            }
        });

        return executionId;
    }

    /**
     * 校验必填参数
     */
    private void validateRequiredParameters(JsonNode nodes, Map<String, Object> params) {
        nodes.forEach(node -> {
            if ("START".equals(node.get("type").asText())) {
                String nodeId = node.get("id").asText();
                String nodeName = node.has("name") ? node.get("name").asText() : node.get("id").asText();

                JsonNode inputsToCheck = null;
                if (node.has("inputParams") && node.get("inputParams").isArray()) {
                    inputsToCheck = node.get("inputParams");
                } else if (node.has("ioParams") && node.get("ioParams").isArray()) {
                    inputsToCheck = node.get("ioParams");
                }
                if (inputsToCheck != null) {
                    inputsToCheck.forEach(input -> {
                        String paramType = input.has("paramType") ? input.get("paramType").asText("") : "INPUT";
                        boolean isInput = "INPUT".equalsIgnoreCase(paramType);
                        boolean required = input.has("requiredFlag") && input.get("requiredFlag").asInt(0) == 1;
                        if (isInput && required) {
                            String paramName = input.has("paramCode") ? input.get("paramCode").asText() : input.path("paramName").asText();
                            boolean hasValue = false;

                            if (params != null && params.containsKey(nodeId)) {
                                Object nodeParamsObj = params.get(nodeId);
                                if (nodeParamsObj instanceof Map) {
                                    Map<?, ?> nodeParams = (Map<?, ?>) nodeParamsObj;
                                    Object val = nodeParams.get(paramName);
                                    if (val != null && !val.toString().trim().isEmpty()) {
                                        hasValue = true;
                                    }
                                }
                            }

                            if (!hasValue && params != null) {
                                Object val = params.get(paramName);
                                if (val != null && !val.toString().trim().isEmpty()) {
                                    hasValue = true;
                                }
                            }

                            if (!hasValue) {
                                throw new BusinessException("节点 [" + nodeName + "] 的必填参数 [" + paramName + "] 缺失");
                            }
                        }
                    });
                }
            }
        });
    }

    private String resolveEdgeNodeId(JsonNode edge, String primaryField, String legacyField) {
        if (edge == null) return null;
        JsonNode nested = edge.get(primaryField);
        if (nested != null && nested.isObject()) {
            JsonNode nodeId = nested.get("nodeId");
            if ((nodeId == null || nodeId.isNull() || nodeId.asText().isBlank()) && nested.has("id")) {
                nodeId = nested.get("id");
            }
            if (nodeId != null && !nodeId.isNull() && !nodeId.asText().isBlank()) {
                return nodeId.asText();
            }
        }
        JsonNode legacy = edge.get(legacyField);
        if (legacy == null || legacy.isNull()) return null;
        return legacy.asText();
    }

    private void finalizeLog(ExecutionLog logData) {
        logData.setEndTime(LocalDateTime.now());
        long startMs = logData.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        logData.setDurationMs(System.currentTimeMillis() - startMs);
        executionLogService.sendExecutionLog(logData);
    }

    private void markExecutionFailed(String executionId, Exception e) {
        try {
            ExecutionLog existing = executionLogService.getOne(new LambdaQueryWrapper<ExecutionLog>()
                    .eq(ExecutionLog::getExecutionId, executionId));
            if (existing == null) {
                return;
            }
            existing.setStatus("FAILURE");
            existing.setErrorMessage(buildExecutionErrorMessage(e));
            if (existing.getOutputResult() == null || existing.getOutputResult().isBlank()) {
                existing.setOutputResult(buildFailureOutputResult(executionId, e));
            }
            if (existing.getEndTime() == null) {
                existing.setEndTime(LocalDateTime.now());
            }
            if (existing.getStartTime() != null) {
                long startMs = existing.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                existing.setDurationMs(Math.max(1L, System.currentTimeMillis() - startMs));
            }
            executionLogService.sendExecutionLog(existing);
        } catch (Exception updateEx) {
            log.error("Failed to mark execution as FAILURE, executionId={}", executionId, updateEx);
        }
    }

    private String buildExecutionErrorMessage(Exception e) {
        if (e == null) {
            return "执行异常";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message;
    }

    private String buildFailureOutputResult(String executionId, Exception e) {
        Map<String, Object> failureResult = new LinkedHashMap<>();
        failureResult.put("status", "FAILURE");
        failureResult.put("executionId", executionId);
        failureResult.put("errorMessage", buildExecutionErrorMessage(e));
        failureResult.put("exceptionType", e == null ? null : e.getClass().getName());
        return safeWriteJson(failureResult);
    }

    private String safeWriteJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String safeSourceValueText(JsonNode sourceValueNode) {
        if (sourceValueNode == null || sourceValueNode.isNull()) {
            return "null";
        }
        String txt = sourceValueNode.toString();
        return txt.length() > 400 ? txt.substring(0, 400) + "..." : txt;
    }

    private String buildNodeErrorMessage(String nodeId, Exception e) {
        String msg = e == null ? "执行异常" : e.getMessage();
        return "nodeId=" + nodeId + ", " + msg;
    }

    private Map<String, Object> loadNodeInstanceSnapshot(Long taskDslId, String nodeId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, component_id, component_code, component_version, sync_status FROM node_instance WHERE task_dsl_id = ? AND node_id = ?",
                    taskDslId, nodeId);
            if (rows.isEmpty()) return null;

            Map<String, Object> instance = rows.get(0);
            Long instanceId = ((Number) instance.get("id")).longValue();

            // 加载字段值
            List<Map<String, Object>> fieldValues = jdbcTemplate.queryForList(
                    "SELECT field_code, field_name, value_type, widget_type, widget_props, default_value, field_value, required_flag, sort_order, description, field_snapshot FROM node_field_value WHERE node_instance_id = ? ORDER BY sort_order",
                    instanceId);
            com.fasterxml.jackson.databind.node.ObjectNode fieldValuesJson = objectMapper.createObjectNode();
            for (Map<String, Object> fv : fieldValues) {
                String code = (String) fv.get("field_code");
                String value = (String) fv.get("field_value");
                if (value != null) fieldValuesJson.put(code, value);
            }

            // 加载IO参数
            List<Map<String, Object>> ioParams = jdbcTemplate.queryForList(
                    "SELECT io_type, param_code, param_name, data_type, source_type, source_value, param_value, required_flag, sort_order FROM node_io_param_value WHERE node_instance_id = ? ORDER BY sort_order",
                    instanceId);
            com.fasterxml.jackson.databind.node.ArrayNode ioParamsJson = objectMapper.createArrayNode();
            for (Map<String, Object> io : ioParams) {
                com.fasterxml.jackson.databind.node.ObjectNode item = objectMapper.createObjectNode();
                item.put("paramType", (String) io.get("io_type"));
                item.put("paramCode", (String) io.get("param_code"));
                item.put("paramName", (String) io.get("param_name"));
                item.put("dataType", (String) io.get("data_type"));
                item.put("sourceType", io.get("source_type") != null ? (String) io.get("source_type") : "CONST");
                item.put("sourceValue", io.get("source_value") != null ? (String) io.get("source_value") : null);
                ioParamsJson.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("instanceId", instanceId);
            result.put("componentVersion", instance.get("component_version"));
            result.put("syncStatus", instance.get("sync_status") != null ? ((Number) instance.get("sync_status")).intValue() : 0);
            result.put("fieldValuesJson", fieldValuesJson);
            result.put("ioParamsJson", ioParamsJson);
            return result;
        } catch (Exception e) {
            log.warn("加载节点实例快照失败 nodeId={}: {}", nodeId, e.getMessage());
            return null;
        }
    }

    private String generateTaskCode() {
        return "TASK_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    @Override
    public Long create(ExecutorTaskCreateDTO dto) {
        String taskCode = dto.getTaskCode();
        if (taskCode == null || taskCode.isBlank()) {
            taskCode = generateTaskCode();
        }

        long exists = baseMapper.countByTaskCode(taskCode);
        if (exists > 0) {
            throw new BusinessException("任务编码已存在（含已删除记录），请更换后重试");
        }
        ExecutorTask e = new ExecutorTask();
        e.setTaskCode(taskCode);
        e.setTaskName(dto.getTaskName());
        e.setDescription(dto.getDescription());
        e.setVersion("1.0.0");
        e.setStatus(1);
        e.setCreatedBy("admin");
        e.setCreatedTime(LocalDateTime.now());
        e.setUpdatedBy("admin");
        e.setUpdatedTime(LocalDateTime.now());
        save(e);
        return e.getId();
    }

    @Override
    public void update(Long id, ExecutorTaskUpdateDTO dto) {
        ExecutorTask e = requireTask(id);
        if (dto.getTaskName() != null)
            e.setTaskName(dto.getTaskName());
        if (dto.getDescription() != null)
            e.setDescription(dto.getDescription());
        if (dto.getStatus() != null)
            e.setStatus(dto.getStatus());
        e.setUpdatedBy("admin");
        e.setUpdatedTime(LocalDateTime.now());
        updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ExecutorTask e = requireTask(id);

        // 仅允许删除"未关联任何组件、未被任何开放接口绑定"的任务
        Integer dslComponentRefCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM task_dsl WHERE task_id = ? AND dsl_content IS NOT NULL " +
                        "AND (dsl_content LIKE '%\"componentId\"%' OR dsl_content LIKE '%\"componentCode\"%')",
                Integer.class,
                e.getId());
        if (dslComponentRefCount != null && dslComponentRefCount > 0) {
            throw new BusinessException("当前任务已关联组件，无法删除，请先解除组件关联后重试");
        }

        Integer openApiRefCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM open_api WHERE task_id = ?",
                Integer.class,
                e.getId());
        if (openApiRefCount != null && openApiRefCount > 0) {
            throw new BusinessException("当前任务已被开放接口引用，无法删除，请先解除接口关联后重试");
        }

        final int[] affected = new int[]{0};
        new CascadeDeleteHelper()
                // task_dsl 存在 task_id 外键，必须先物理删除子表记录
                .addChildDelete(() -> jdbcTemplate.update("DELETE FROM task_dsl WHERE task_id = ?", e.getId()))
                // task_test_config 也按 task_id 关联，删除任务前一并清理
                .addChildDelete(() -> jdbcTemplate.update("DELETE FROM task_test_config WHERE task_id = ?", e.getId()))
                .setParentDelete(() -> affected[0] = removeById(e.getId()) ? 1 : 0)
                .execute();
        if (affected[0] <= 0) {
            throw new BusinessException("删除失败，请稍后重试");
        }
    }

    @Override
    public ExecutorTaskVO detail(Long id) {
        return toVO(requireTask(id));
    }

    @Override
    public PageResult<ExecutorTaskVO> page(long current, long size, String keyword, String status) {

        LambdaQueryWrapper<ExecutorTask> wrapper = new LambdaQueryWrapper<>();

        // 关键字搜索
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ExecutorTask::getTaskName, keyword).or().like(ExecutorTask::getTaskCode, keyword));
        }

        // 状态过滤
        if (status != null && !status.isBlank() && !"全部".equals(status)) {
            Integer statusInt = null;
            if ("启用".equals(status) || "1".equals(status) || "发布".equals(status)) {
                statusInt = 1;
            } else if ("禁用".equals(status) || "0".equals(status) || "停运".equals(status)) {
                statusInt = 0;
            }
            if (statusInt != null) {
                wrapper.eq(ExecutorTask::getStatus, statusInt);
            }
        }

        wrapper.orderByDesc(ExecutorTask::getCreatedTime);

        Page<ExecutorTask> pageParam = new Page<>(current, size);
        Page<ExecutorTask> resultPage = this.page(pageParam, wrapper);

        List<ExecutorTaskVO> records = resultPage.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(resultPage.getTotal(), resultPage.getSize(), resultPage.getCurrent(), records);
    }

    @Override
    public long count() {
        return baseMapper.selectCount(null);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        ExecutorTask e = requireTask(id);
        e.setStatus(status);
        e.setUpdatedBy("admin");
        e.setUpdatedTime(LocalDateTime.now());
        updateById(e);
    }

    private TaskDslEntity resolveDslForExecution(Long taskId, String environment, Map<String, Object> params) {
        String env = environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
        if (env.isBlank()) {
            throw new BusinessException("执行环境不能为空");
        }
        Long explicitVersionId = extractVersionId(params);

        // 约束：
        // 1) 小火箭(生产执行)必须执行当前任务在生产环境"选中版本"
        // 2) 测试页面执行必须执行当前选择的测试版本
        if ("TEST".equals(env) || "PROD".equals(env)) {
            if (explicitVersionId == null) {
                throw new BusinessException(("TEST".equals(env) ? "测试" : "生产") + "任务必须显式选择版本后执行");
            }
            TaskDslEntity explicit = taskDslService.getOne(new LambdaQueryWrapper<TaskDslEntity>()
                    .eq(TaskDslEntity::getId, explicitVersionId)
                    .eq(TaskDslEntity::getTaskId, taskId)
                    .eq(TaskDslEntity::getEnvironment, env));
            if (explicit == null) {
                throw new BusinessException(("TEST".equals(env) ? "测试" : "生产") + "环境未找到所选版本");
            }
            return explicit;
        }

        if (explicitVersionId != null) {
            TaskDslEntity explicit = taskDslService.getOne(new LambdaQueryWrapper<TaskDslEntity>()
                    .eq(TaskDslEntity::getId, explicitVersionId)
                    .eq(TaskDslEntity::getTaskId, taskId)
                    .eq(TaskDslEntity::getEnvironment, env));
            if (explicit == null) {
                throw new BusinessException("未找到所选版本");
            }
            return explicit;
        }

        TaskDslEntity current = taskDslService.getOne(new LambdaQueryWrapper<TaskDslEntity>()
                .eq(TaskDslEntity::getTaskId, taskId)
                .eq(TaskDslEntity::getEnvironment, env)
                .eq(TaskDslEntity::getIsCurrent, 1));
        if (current == null) {
            throw new BusinessException("该环境下未找到已发布的当前版本");
        }
        return current;
    }

    private Long extractVersionId(Map<String, Object> params) {
        if (params == null) return null;
        Object val = params.get("versionId");
        if (val == null) {
            val = params.get("taskVersionId");
        }
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(val).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private ExecutorTask requireTask(Long id) {
        ExecutorTask e = getById(id);
        if (e == null) {
            throw new BusinessException("任务不存在");
        }
        return e;
    }

    /**
     * 拍平按节点分组的触发参数：{nodeId: {a:"5", b:"3"}} → {a:"5", b:"3"}
     * 过滤掉未解析的引用对象 {a: {nodeId, paramCode}} → 跳过
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenNodeParams(Map<String, Object> params) {
        if (params == null) return new HashMap<>();
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> inner = (Map<String, Object>) entry.getValue();
                // inner 是 {nodeId, paramCode} → 未解析的引用，跳过
                // inner 是 {a:"5", b:"3"} → 真正的测试值，拍平
                if (inner.containsKey("nodeId") && inner.containsKey("paramCode") && inner.size() <= 2) {
                    continue;  // 跳过未解析的 sourceValue 引用对象
                }
                flat.putAll(inner);
            } else {
                flat.put(entry.getKey(), entry.getValue());
            }
        }
        return flat;
    }

    private Map<String, Object> loadTestConfigParams(Long taskId) {
        try {
            List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                    "SELECT config_data FROM task_test_config WHERE task_id = ? AND is_default = 1 LIMIT 1",
                    taskId);
            if (!configs.isEmpty()) {
                String configData = Objects.toString(configs.get(0).get("config_data"), "{}");
                return objectMapper.readValue(configData, Map.class);
            }
        } catch (Exception e) {
            log.warn("加载测试配置失败: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    private ExecutorTaskVO toVO(ExecutorTask e) {
        ExecutorTaskVO vo = new ExecutorTaskVO();
        vo.setId(e.getId());
        vo.setTaskCode(e.getTaskCode());
        vo.setTaskName(e.getTaskName());
        vo.setDescription(e.getDescription());
        vo.setStatus(e.getStatus());
        vo.setStatusText(e.getStatus() != null && e.getStatus() == 1 ? "发布" : "停运");
        vo.setVersion(e.getVersion());
        vo.setCurrentVersionId(e.getId());
        vo.setCreatedTime(e.getCreatedTime());
        vo.setUpdatedTime(e.getUpdatedTime());
        return vo;
    }

    /** 从 DSL 中解析节点间的参数引用关系，记录数据血缘 */
    private void recordDataLineage(String executionId, Long taskId, String dslContent) {
        try {
            JsonNode dsl = objectMapper.readTree(dslContent);
            JsonNode nodes = dsl.get("nodes");
            if (nodes == null || !nodes.isArray()) return;

            Map<String, JsonNode> nodeMap = new HashMap<>();
            for (JsonNode n : nodes) {
                nodeMap.put(n.get("id").asText(), n);
            }
            LocalDateTime now = LocalDateTime.now();
            List<DataLineage> batch = new ArrayList<>();

            for (JsonNode node : nodes) {
                String targetId = node.get("id").asText();
                String targetName = node.path("name").asText(targetId);
                JsonNode inputParams = node.get("inputParams");
                if (inputParams == null || !inputParams.isArray()) continue;

                for (JsonNode ip : inputParams) {
                    String sourceType = ip.path("sourceType").asText("");
                    if (!"UPSTREAM_OUTPUT".equalsIgnoreCase(sourceType)) continue;
                    JsonNode sourceValue = ip.get("sourceValue");
                    if (sourceValue == null || sourceValue.isNull()) continue;

                    String sv = sourceValue.isTextual() ? sourceValue.asText() : sourceValue.toString();
                    String[] parts = sv.contains(".") ? sv.split("\\.", 2) : new String[]{sv, ""};
                    String sourceNodeId = parts[0];
                    String paramCode = parts.length > 1 ? parts[1] : ip.path("paramCode").asText("");

                    JsonNode sourceNode = nodeMap.get(sourceNodeId);
                    String sourceNodeName = sourceNode != null ? sourceNode.path("name").asText(sourceNodeId) : sourceNodeId;

                    DataLineage dl = new DataLineage();
                    dl.setExecutionId(executionId);
                    dl.setTaskId(taskId);
                    dl.setSourceNodeId(sourceNodeId);
                    dl.setTargetNodeId(targetId);
                    dl.setSourceNodeName(sourceNodeName);
                    dl.setTargetNodeName(targetName);
                    dl.setParamCode(paramCode);
                    dl.setSourceValue(sv);
                    dl.setCreatedTime(now);
                    batch.add(dl);
                }
            }
            if (!batch.isEmpty()) {
                for (DataLineage dl : batch) {
                    dataLineageMapper.insert(dl);
                }
                log.info("血缘记录完成: executionId={}, 条数={}", executionId, batch.size());
            }
        } catch (Exception e) {
            log.warn("血缘记录失败(不影响执行): {}", e.getMessage());
        }
    }
}
