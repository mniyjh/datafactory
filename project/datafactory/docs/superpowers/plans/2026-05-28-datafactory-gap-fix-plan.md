# Data Factory 遗漏项修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 FilterPlugin 行过滤、Cron 调度、执行重试、线程池替换、IO参数历史补全、ComponentFeignClient 补全、启用Nacos配置中心，并补充全面集成测试。

**Architecture:** 在现有 ExecEngine 插件体系基础上增强，FilterPlugin 新增 Aviator 表达式过滤模式（复用 BranchPlugin 已有的 Aviator 依赖），Cron 调度使用 Spring 的 CronExpression 计算 nextFireTime，重试在 ExecEngine 循环中实现，Feign 参照已有 DatasourceFeignClient 模式。

**Tech Stack:** Java 21, Spring Boot 3.3.7, MyBatis-Plus 3.5.7, Aviator, Spring Cloud OpenFeign, JUnit 5, H2, Spring Scheduling

---

### Task 1: FilterPlugin — 行过滤增强（SIMPLE + EXPRESSION 模式）

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/FilterPlugin.java`
- Modify: `frontend/src/views/ComponentPage.vue`

- [ ] **Step 1: 重写 FilterPlugin.java 支持三种过滤模式**

将文件内容替换为：

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.aviator.AviatorEvaluator;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FilterPlugin implements ComponentPlugin {

    private static final Set<String> NUMERIC_OPS = Set.of("GT", "GTE", "LT", "LTE");

    @Override
    public Set<String> supportedTypes() { return Set.of("FILTER"); }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(PluginContext context) {
        JsonNode fieldValues = context.getNode().getFieldValues();
        String filterMode = readFieldValue(fieldValues, "filterMode", "mode");
        String sourceNodeId = readFieldValue(fieldValues, "sourceNodeId", "sourceNode");

        Map<String, Object> sourceData = resolveSourceData(context, sourceNodeId);
        Object rowsObj = sourceData.get("rows");
        if (!(rowsObj instanceof List)) {
            return sourceData;
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;

        // COLUMN 模式：列投影
        if ("COLUMN".equalsIgnoreCase(filterMode) || filterMode.isBlank()) {
            return applyColumnFilter(fieldValues, sourceData, rows);
        }

        // EXPRESSION 模式：Aviator 表达式
        if ("EXPRESSION".equalsIgnoreCase(filterMode)) {
            return applyExpressionFilter(fieldValues, sourceData, rows);
        }

        // SIMPLE 模式：简单条件匹配
        return applySimpleFilter(fieldValues, sourceData, rows);
    }

    private Map<String, Object> resolveSourceData(PluginContext context, String sourceNodeId) {
        Map<String, Object> sourceData = null;
        if (!sourceNodeId.isBlank()) {
            sourceData = context.getUpstreamOutputs().get(sourceNodeId);
        }
        if (sourceData == null) {
            for (Map<String, Object> v : context.getUpstreamOutputs().values()) {
                if (v.containsKey("rows")) { sourceData = v; break; }
            }
        }
        if (sourceData == null) {
            throw new BusinessException("FILTER组件未找到上游数据源");
        }
        return sourceData;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyColumnFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                   List<Map<String, Object>> rows) {
        String columnsStr = readFieldValue(fieldValues, "columnFilter", "columns");
        List<String> columns = Arrays.stream(columnsStr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        List<Map<String, Object>> filtered = rows.stream()
                .map(row -> {
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    for (String col : columns) {
                        newRow.put(col, row.getOrDefault(col, null));
                    }
                    return newRow;
                }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applySimpleFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                   List<Map<String, Object>> rows) {
        String condField = readFieldValue(fieldValues, "condition_field");
        String condOp = readFieldValue(fieldValues, "condition_op", "conditionOp").toUpperCase();
        String condValue = readFieldValue(fieldValues, "condition_value", "conditionValue");

        if (condField.isBlank() || condOp.isBlank()) {
            Map<String, Object> result = new HashMap<>(sourceData);
            result.put("rowCount", rows.size());
            return result;
        }

        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> matchCondition(row.get(condField), condOp, condValue))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyExpressionFilter(JsonNode fieldValues, Map<String, Object> sourceData,
                                                       List<Map<String, Object>> rows) {
        String expression = readFieldValue(fieldValues, "expression", "filterExpression");
        if (expression.isBlank()) {
            throw new BusinessException("FILTER组件表达式模式缺少expression字段");
        }

        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> {
                    try {
                        Map<String, Object> env = new HashMap<>();
                        for (Map.Entry<String, Object> col : row.entrySet()) {
                            Object val = col.getValue();
                            if (val instanceof String s) {
                                try { val = Long.parseLong(s); } catch (NumberFormatException e1) {
                                    try { val = Double.parseDouble(s); } catch (NumberFormatException e2) {}
                                }
                            }
                            env.put(col.getKey(), val);
                        }
                        Object evalResult = AviatorEvaluator.execute(expression, env);
                        return toBoolean(evalResult);
                    } catch (Exception e) {
                        throw new BusinessException("过滤表达式求值失败: " + expression + " — " + e.getMessage());
                    }
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>(sourceData);
        result.put("rows", filtered);
        result.put("rowCount", filtered.size());
        return result;
    }

    private boolean matchCondition(Object fieldValue, String op, String expectedStr) {
        switch (op) {
            case "IS_NULL":     return fieldValue == null;
            case "IS_NOT_NULL": return fieldValue != null;
            case "EQ":  return Objects.toString(fieldValue, "").equals(expectedStr);
            case "NEQ": return !Objects.toString(fieldValue, "").equals(expectedStr);
            case "CONTAINS": return Objects.toString(fieldValue, "").contains(expectedStr);
            case "IN": {
                Set<String> values = Arrays.stream(expectedStr.split(",")).map(String::trim).collect(Collectors.toSet());
                return values.contains(Objects.toString(fieldValue, ""));
            }
            case "GT": case "GTE": case "LT": case "LTE":
                return compareNumeric(fieldValue, op, expectedStr);
            default:
                return true;
        }
    }

    private boolean compareNumeric(Object fieldValue, String op, String expectedStr) {
        try {
            double fieldNum = Double.parseDouble(Objects.toString(fieldValue, "0"));
            double expectedNum = Double.parseDouble(expectedStr);
            return switch (op) {
                case "GT"  -> fieldNum > expectedNum;
                case "GTE" -> fieldNum >= expectedNum;
                case "LT"  -> fieldNum < expectedNum;
                case "LTE" -> fieldNum <= expectedNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean toBoolean(Object result) {
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.doubleValue() != 0;
        if (result == null) return false;
        return true;
    }

    private String readFieldValue(JsonNode fieldValues, String... keys) {
        if (fieldValues == null || fieldValues.isNull()) return "";
        for (String key : keys) {
            JsonNode val = fieldValues.get(key);
            if (val != null && !val.isNull() && !val.asText().isBlank()) return val.asText();
        }
        return "";
    }
}
```

- [ ] **Step 2: 更新前端 ComponentPage.vue 中 FILTER 默认字段**

找到 `FILTER: [` 块（约第 473-478 行），替换为：

```javascript
  FILTER: [
    { code: 'filterMode', name: '过滤模式', type: 'STRING', widget: 'MULTI_SELECT', required: true },
    { code: 'sourceNodeId', name: '数据源节点', type: 'STRING', widget: 'TEXTAREA', required: false },
    { code: 'columns', name: '保留列（COLUMN模式）', type: 'STRING', widget: 'TEXTAREA', required: false },
    { code: 'condition_field', name: '过滤字段（SIMPLE模式）', type: 'STRING', widget: 'TEXTAREA', required: false },
    { code: 'condition_op', name: '运算符（SIMPLE模式）', type: 'STRING', widget: 'MULTI_SELECT', required: false },
    { code: 'condition_value', name: '过滤值（SIMPLE模式）', type: 'STRING', widget: 'TEXTAREA', required: false },
    { code: 'expression', name: '过滤表达式（EXPRESSION模式）', type: 'STRING', widget: 'TEXTAREA', required: false },
    { code: 'result_var', name: '结果变量', type: 'STRING', widget: 'TEXTAREA', required: false }
  ],
```

- [ ] **Step 3: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/plugin/FilterPlugin.java frontend/src/views/ComponentPage.vue
git commit -m "feat: add SIMPLE and EXPRESSION row filter modes to FilterPlugin"
```

---

### Task 2: Cron 定时调度 — 解析 Cron 表达式计算 nextFireTime

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ScheduleJobService.java`
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/TaskScheduleExecutor.java`
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ScheduleJobController.java`

- [ ] **Step 1: 在 ScheduleJobService 中添加 Cron 计算逻辑**

将 `ScheduleJobService.java` 替换为：

```java
package com.cqie.datafactory.executor.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import com.cqie.datafactory.executor.schedule.mapper.ScheduleJobMapper;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduleJobService extends ServiceImpl<ScheduleJobMapper, ScheduleJob> {

    public List<ScheduleJob> listEnabledJobs() {
        return list(new LambdaQueryWrapper<ScheduleJob>()
                .eq(ScheduleJob::getStatus, 1));
    }

    @Override
    public boolean save(ScheduleJob job) {
        computeNextFireTime(job);
        return super.save(job);
    }

    @Override
    public boolean updateById(ScheduleJob job) {
        computeNextFireTime(job);
        return super.updateById(job);
    }

    public void computeNextFireTime(ScheduleJob job) {
        String cronExpr = job.getCronExpression();
        if (cronExpr == null || cronExpr.isBlank()) {
            return;
        }
        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            LocalDateTime next = cron.next(LocalDateTime.now());
            job.setNextFireTime(next);
        } catch (Exception e) {
            job.setNextFireTime(null);
        }
    }
}
```

- [ ] **Step 2: 更新 TaskScheduleExecutor 在触发后计算下次执行时间**

将 `fireJob` 方法中设置 `nextFireTime` 为 null 的逻辑改为计算下一次：

```java
    private void fireJob(ScheduleJob job) {
        try {
            List<String> dslRows = jdbcTemplate.queryForList(
                    "SELECT dsl_content FROM task_dsl WHERE id=?", String.class, job.getTaskVersionId());
            if (dslRows.isEmpty()) return;

            execEngine.execute(dslRows.get(0), job.getEnvironment(), null, null);

            job.setLastFireTime(LocalDateTime.now());
            // 计算下一次执行时间，而非设为 null
            scheduleJobService.computeNextFireTime(job);
            scheduleJobService.updateById(job);
        } catch (Exception e) {
            // log and continue
        }
    }
```

使用 Edit 替换原有 fireJob 方法体中的相关行。

- [ ] **Step 3: 更新 ScheduleJobController.trigger 中手动触发也计算 nextFireTime**

将 `ScheduleJobController.java` 的 `trigger` 方法替换为：

```java
    @PostMapping("/{id}/trigger")
    public Result<?> trigger(@PathVariable("id") Long id) {
        ScheduleJob job = scheduleJobService.getById(id);
        if (job == null) return Result.fail("定时任务不存在");
        // 立即触发：设置 nextFireTime 为 now，让下一次轮询立即执行
        job.setNextFireTime(LocalDateTime.now());
        scheduleJobService.updateById(job);
        return Result.success();
    }
```

- [ ] **Step 4: 确认 CronExpression 不需要额外 Maven 依赖**

`CronExpression` 来自 `spring-context`（Spring Boot 自带），无需添加依赖。

- [ ] **Step 5: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ScheduleJobService.java datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/TaskScheduleExecutor.java datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/ScheduleJobController.java
git commit -m "feat: implement cron expression parsing for schedule job nextFireTime calculation"
```

---

### Task 3: 执行重试机制 — ExecEngine 节点执行添加重试循环

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/ExecEngine.java`

- [ ] **Step 1: 修改 ExecEngine.execute 节点执行循环添加重试**

将 `ExecEngine.java` 第 37-91 行的 for 循环体替换为（在 `resolvedInputs` 处理完成后，将 try/catch 改为重试循环）：

找到 `for (NodeDef node : sequence) {` 块（第 38-98 行），将其中 try/catch 部分替换为含重试逻辑的版本。完整替换 `for (NodeDef node : sequence) {` 到对应 `}` 的结束（第 98 行）：

关键改动在 48-91 行区域 —— 将 try/catch 包裹在 `for (int attempt = 1; attempt <= maxRetries; attempt++)` 循环中：

```java
            int maxRetries = readRetryCount(node);
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    if ("START".equalsIgnoreCase(node.getType())) {
                        resolvedInputs.putAll(currentParams);
                    } else {
                        for (IoParamDef def : node.getInputParams()) {
                            String code = def.getParamCode();
                            resolvedInputs.put(code, paramResolver.resolve(def, nodeOutputsMap));
                        }
                    }

                    if ("END".equalsIgnoreCase(node.getType()) && node.getInputParams().isEmpty()) {
                        for (Map<String, Object> upstream : nodeOutputsMap.values()) {
                            resolvedInputs.putAll(upstream);
                        }
                    }

                    Map<String, Object> rawResult;
                    if ("START".equalsIgnoreCase(node.getType()) || "END".equalsIgnoreCase(node.getType())) {
                        PluginContext ctx = new PluginContext(node, environment, resolvedInputs, nodeOutputsMap);
                        rawResult = pluginRegistry.get("START").execute(ctx);
                    } else {
                        PluginContext ctx = new PluginContext(node, environment, resolvedInputs, nodeOutputsMap);
                        rawResult = pluginRegistry.get(node.getType()).execute(ctx);
                    }

                    Map<String, Object> nodeOutputs = buildNodeOutputs(node, resolvedInputs, rawResult);
                    nodeOutputsMap.put(node.getId(), nodeOutputs);
                    if ("END".equalsIgnoreCase(node.getType())) {
                        finalOutput = nodeOutputs;
                    }

                    record.status = "SUCCESS";
                    record.outputs = rawResult;
                    record.retryCount = attempt - 1;
                    lastException = null;
                    break;
                } catch (Exception e) {
                    lastException = e;
                    record.retryCount = attempt;
                    if (attempt >= maxRetries) {
                        record.status = "FAILURE";
                        record.errorMessage = e.getMessage();
                        if (nodeCallback != null) {
                            record.endTime = System.currentTimeMillis();
                            record.durationMs = record.endTime - startMs;
                            nodeCallback.accept(record);
                        }
                        throw new BusinessException("节点[" + node.getDisplayName() + "]执行失败(已重试" + (attempt - 1) + "次): " + e.getMessage());
                    }
                }
            }
```

同时在 `NodeExecutionRecord` 类中添加 `retryCount` 字段：

```java
    public static class NodeExecutionRecord {
        public String nodeId;
        public String nodeName;
        public String nodeType;
        public String componentCode;
        public String status;
        public long startTime;
        public long endTime;
        public long durationMs;
        public int retryCount;
        public Map<String, Object> outputs;
        public String errorMessage;
    }
```

最后在类末尾添加读取重试次数的私有方法：

```java
    private int readRetryCount(NodeDef node) {
        // 以下组件类型不重试：START, END, BRANCH, FILTER, COMMON_TASK
        String type = node.getType() != null ? node.getType().toUpperCase() : "";
        if (Set.of("START", "END", "BRANCH", "FILTER", "COMMON_TASK", "TASK").contains(type)) {
            return 1;
        }
        // 尝试从 fieldValues 读取 retryCount
        try {
            if (node.getFieldValues() != null && !node.getFieldValues().isNull()) {
                var retryNode = node.getFieldValues().get("retryCount");
                if (retryNode != null && !retryNode.isNull()) {
                    return Math.max(1, Integer.parseInt(retryNode.asText()));
                }
            }
        } catch (Exception ignore) {}
        return 1;
    }
```

注意：需要在 import 区域添加 `import java.util.Set;`（已存在则无需添加）。

- [ ] **Step 2: 在 ExecutorTaskServiceImpl 的日志回调中传递 retryCount**

在 `ExecutorTaskServiceImpl.java` 第 126 行的 lambda 回调中，增加 `retryCount` 的设置：

```java
                            nodeLog.setRetryCount(record.retryCount);
```

插入到 `nodeLog.setDurationMs(record.durationMs);` 之后。

- [ ] **Step 3: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/engine/ExecEngine.java datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java
git commit -m "feat: add retry mechanism for DB/API/SCRIPT plugin node execution"
```

---

### Task 4: 线程池替换 new Thread()

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java`

- [ ] **Step 1: 注入 ThreadPoolTaskExecutor 并替换 new Thread()**

在 `ExecutorTaskServiceImpl` 类的字段声明区添加：

```java
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor;
```

注意：`@RequiredArgsConstructor` 已存在，Lombok 会自动为所有 `final` 字段生成构造函数参数，所以只需添加 final 字段即可。

- [ ] **Step 2: 替换 new Thread() 为线程池提交**

将第 84 行的：
```java
        new Thread(() -> {
```

替换为：
```java
        taskExecutor.execute(() -> {
```

同时将第 157 行的 `.start()` 删除，将 `}).start();` 改为 `});`。

- [ ] **Step 3: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java
git commit -m "refactor: replace new Thread() with ThreadPoolTaskExecutor"
```

---

### Task 5: component_io_param_history 补全

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/ComponentIoParamHistory.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/ComponentIoParamHistoryMapper.java`
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/ComponentServiceImpl.java`

- [ ] **Step 1: 创建 ComponentIoParamHistory 实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("component_io_param_history")
public class ComponentIoParamHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long componentId;
    private String version;
    private String changeType;
    private String paramSnapshot;
    private String changeLog;
    private String createdBy;
    private LocalDateTime createdTime;
}
```

- [ ] **Step 2: 创建 ComponentIoParamHistoryMapper**

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.ComponentIoParamHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ComponentIoParamHistoryMapper extends BaseMapper<ComponentIoParamHistory> {
}
```

- [ ] **Step 3: 在 ComponentServiceImpl 中注入 Mapper 并在 saveComponentFields 记录 IO 参数历史**

在 `ComponentServiceImpl` 中添加注入：

```java
    @Autowired
    private com.cqie.datafactory.configuration.mapper.ComponentIoParamHistoryMapper componentIoParamHistoryMapper;
```

在 `saveComponentFields` 方法末尾（第 240 行 `}` 之前，即 `componentFieldHistoryMapper.insert(history);` 之后），添加 IO 参数历史记录逻辑。当前方法签名为 `saveComponentFields(Long componentId, List<ComponentFieldSaveDTO> fields)` —— 它处理的是字段配置而非 IO 参数。

需要在合适的位置（例如新增一个 `saveComponentIoParams` 或在现有的 IO 参数修改逻辑中）添加历史记录。查看 Controller 确认是否有 IO 参数保存端点。目前 ComponentController 没有显式的 IO 参数端点（IO 参数是随组件创建时一起设定的）。最合适的做法是在 `saveComponentFields` 方法记录字段历史的同时检查 IO 参数是否也有变更。

但根据现有代码模式，IO 参数随组件创建/更新时由前端通过 ComponentCreateDTO 传递。在 Controller 中的创建和更新方法使用的是 `ComponentCreateDTO`，而 `ComponentServiceImpl` 的 `createComponent` 和 `updateComponent` 只处理 Component 基础信息。IO 参数实际上是通过 Feign 的 `ComponentFeignController` 或画布编辑器保存的。

参照 `component_field_history` 的模式，I/O 参数的历史应当在任何 I/O 参数变更时记录。当前最合适的接入点是在 `saveComponentFields` 方法中同时检查 I/O 参数是否变更。但由于 `saveComponentFields` 目前不接收 I/O 参数……

考虑到这一限制，我们在 `saveComponentFields` 方法的字段历史记录部分之后添加 IO 参数的快照记录。当前已经通过 `componentFieldHistoryMapper.insert(history)` 记录了字段快照。与其改造方法签名，不如在 `saveComponentFields` 之后补充对当前组件 IO 参数进行快照记录：

在 `saveComponentFields` 方法末尾（`componentFieldHistoryMapper.insert(history)` 之后，方法闭合 `}` 之前），添加：

```java
            // 同时记录当前 IO 参数快照
            java.util.List<com.cqie.datafactory.configuration.entity.ComponentIoParam> ioParams =
                jdbcTemplate.query(
                    "SELECT io_type, param_code, param_name, data_type, source_type, source_value, default_value, required_flag, param_space, sort_order FROM component_io_param WHERE component_id = ? ORDER BY sort_order",
                    (rs, rowNum) -> {
                        com.cqie.datafactory.configuration.entity.ComponentIoParam p = new com.cqie.datafactory.configuration.entity.ComponentIoParam();
                        p.setIoType(rs.getString("io_type"));
                        p.setParamCode(rs.getString("param_code"));
                        p.setParamName(rs.getString("param_name"));
                        p.setDataType(rs.getString("data_type"));
                        p.setSourceType(rs.getString("source_type"));
                        p.setSourceValue(rs.getString("source_value"));
                        p.setDefaultValue(rs.getString("default_value"));
                        p.setRequiredFlag(rs.getInt("required_flag"));
                        p.setParamSpace(rs.getString("param_space"));
                        p.setSortOrder(rs.getInt("sort_order"));
                        return p;
                    },
                    componentId
                );

            com.cqie.datafactory.configuration.entity.ComponentIoParamHistory ioHistory =
                new com.cqie.datafactory.configuration.entity.ComponentIoParamHistory();
            ioHistory.setComponentId(componentId);
            ioHistory.setVersion(newVersion);
            ioHistory.setChangeType("UPDATE");
            ioHistory.setParamSnapshot(toJson(ioParams));
            ioHistory.setChangeLog("字段配置变更同步IO参数快照");
            ioHistory.setCreatedBy("admin");
            ioHistory.setCreatedTime(java.time.LocalDateTime.now());
            componentIoParamHistoryMapper.insert(ioHistory);
```

- [ ] **Step 4: 提交**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/ComponentIoParamHistory.java datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/ComponentIoParamHistoryMapper.java datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/ComponentServiceImpl.java
git commit -m "feat: add ComponentIoParamHistory entity and record IO param snapshots on field changes"
```

---

### Task 6: ComponentFeignClient 补全

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/ComponentFeignClient.java`

- [ ] **Step 1: 创建 ComponentFeignClient**

参照 `DatasourceFeignClient` 模式：

```java
package com.cqie.datafactory.executor.feign;

import com.cqie.datafactory.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "datafactory-backend-configuration", contextId = "componentFeignClient", path = "/feign/component")
public interface ComponentFeignClient {
    @GetMapping("/{id}/validate")
    Result<Map<String, Object>> validateAndGet(@PathVariable("id") Long id);
}
```

- [ ] **Step 2: 验证 ExecutorApplication 的 Feign 扫描路径**

`@EnableFeignClients(basePackages = "com.cqie.datafactory.executor.feign")` 已配置，新接口会自动被扫描到，无需修改。

- [ ] **Step 3: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-feign/src/main/java/com/cqie/datafactory/executor/feign/ComponentFeignClient.java
git commit -m "feat: add ComponentFeignClient for executor-to-configuration component queries"
```

---

### Task 7: 全面集成测试

**Files:**
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/engine/plugin/FilterPluginTest.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/engine/plugin/BranchPluginTest.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/engine/ExecEngineRetryTest.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/schedule/TaskScheduleExecutorTest.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/schedule/ScheduleJobControllerTest.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/test/java/com/cqie/datafactory/executor/service/ExecutionLogServiceTest.java`
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/pom.xml` (添加 H2 依赖)

- [ ] **Step 1: 添加 H2 和测试依赖到 executor-server pom.xml**

在 `<dependencies>` 中添加：

```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 创建 FilterPluginTest**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FilterPluginTest {

    private FilterPlugin plugin;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        plugin = new FilterPlugin();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldFilterRowsBySimpleEqCondition() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "status");
        fieldValues.put("condition_op", "EQ");
        fieldValues.put("condition_value", "1");

        NodeDef node = new NodeDef();
        node.setId("filter1");
        node.setType("FILTER");
        node.setName("Test Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", "1", "status", "1", "name", "Alice"));
        rows.add(Map.of("id", "2", "status", "0", "name", "Bob"));
        rows.add(Map.of("id", "3", "status", "1", "name", "Charlie"));
        upstream.put("rows", rows);
        upstream.put("rowCount", 3);
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(2, result.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(2, resultRows.size());
        assertEquals("Alice", resultRows.get(0).get("name"));
        assertEquals("Charlie", resultRows.get(1).get("name"));
    }

    @Test
    void shouldFilterRowsByExpressionMode() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "EXPRESSION");
        fieldValues.put("expression", "age > 25 && status == 'active'");

        NodeDef node = new NodeDef();
        node.setId("filter2");
        node.setType("FILTER");
        node.setName("Expr Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("name", "Alice", "age", "30", "status", "active")));
        rows.add(new LinkedHashMap<>(Map.of("name", "Bob", "age", "20", "status", "active")));
        rows.add(new LinkedHashMap<>(Map.of("name", "Charlie", "age", "35", "status", "inactive")));
        upstream.put("rows", rows);
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(1, result.get("rowCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(1, resultRows.size());
        assertEquals("Alice", resultRows.get(0).get("name"));
    }

    @Test
    void shouldSupportColumnMode() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "COLUMN");
        fieldValues.put("columns", "id,name");

        NodeDef node = new NodeDef();
        node.setId("filter3");
        node.setType("FILTER");
        node.setName("Col Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("rows", List.of(Map.of("id", "1", "name", "Alice", "status", "1")));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs);
        Map<String, Object> result = plugin.execute(ctx);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultRows = (List<Map<String, Object>>) result.get("rows");
        Map<String, Object> row = resultRows.get(0);
        assertTrue(row.containsKey("id"));
        assertTrue(row.containsKey("name"));
        assertFalse(row.containsKey("status"));
    }

    @Test
    void shouldSupportContainsOperator() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "name");
        fieldValues.put("condition_op", "CONTAINS");
        fieldValues.put("condition_value", "Al");

        NodeDef node = new NodeDef();
        node.setId("filter4");
        node.setType("FILTER");
        node.setName("Contains Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        upstream.put("rows", List.of(
                Map.of("name", "Alice"),
                Map.of("name", "Bob"),
                Map.of("name", "Alex")
        ));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(2, result.get("rowCount"));
    }

    @Test
    void shouldSupportNullCheckOperators() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("filterMode", "SIMPLE");
        fieldValues.put("condition_field", "optional");
        fieldValues.put("condition_op", "IS_NOT_NULL");

        NodeDef node = new NodeDef();
        node.setId("filter5");
        node.setType("FILTER");
        node.setName("Null Filter");
        node.setFieldValues(fieldValues);

        Map<String, Map<String, Object>> upstreamOutputs = new HashMap<>();
        Map<String, Object> upstream = new HashMap<>();
        Map<String, Object> row1 = new HashMap<>(); row1.put("id", "1"); row1.put("optional", "yes");
        Map<String, Object> row2 = new HashMap<>(); row2.put("id", "2"); row2.put("optional", null);
        upstream.put("rows", List.of(row1, row2));
        upstreamOutputs.put("node1", upstream);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), upstreamOutputs);
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(1, result.get("rowCount"));
    }
}
```

- [ ] **Step 3: 创建 BranchPluginTest**

```java
package com.cqie.datafactory.executor.engine.plugin;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BranchPluginTest {

    private BranchPlugin plugin;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        plugin = new BranchPlugin();
        mapper = new ObjectMapper();
    }

    @Test
    void shouldEvaluateSimpleExpressionToTrue() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");
        fieldValues.put("trueTargetNodeId", "nodeA");
        fieldValues.put("falseTargetNodeId", "nodeB");

        NodeDef node = new NodeDef();
        node.setId("branch1");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 10);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(true, result.get("conditionResult"));
        assertEquals("nodeA", result.get("nextNodeId"));
    }

    @Test
    void shouldEvaluateSimpleExpressionToFalse() {
        ObjectNode fieldValues = mapper.createObjectNode();
        fieldValues.put("expression", "${count} > 5");
        fieldValues.put("trueTargetNodeId", "nodeA");
        fieldValues.put("falseTargetNodeId", "nodeB");

        NodeDef node = new NodeDef();
        node.setId("branch2");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        Map<String, Object> resolvedInputs = new HashMap<>();
        resolvedInputs.put("count", 3);

        PluginContext ctx = new PluginContext(node, "TEST", resolvedInputs, new HashMap<>());
        Map<String, Object> result = plugin.execute(ctx);

        assertEquals(false, result.get("conditionResult"));
        assertEquals("nodeB", result.get("nextNodeId"));
    }

    @Test
    void shouldThrowExceptionOnMissingExpression() {
        ObjectNode fieldValues = mapper.createObjectNode();
        NodeDef node = new NodeDef();
        node.setId("branch3");
        node.setType("BRANCH");
        node.setFieldValues(fieldValues);

        PluginContext ctx = new PluginContext(node, "TEST", new HashMap<>(), new HashMap<>());
        assertThrows(BusinessException.class, () -> plugin.execute(ctx));
    }
}
```

- [ ] **Step 4: 创建 ExecEngineRetryTest**

```java
package com.cqie.datafactory.executor.engine;

import com.cqie.datafactory.executor.engine.core.model.DslModel;
import com.cqie.datafactory.executor.engine.core.model.NodeDef;
import com.cqie.datafactory.executor.engine.plugin.ComponentPlugin;
import com.cqie.datafactory.executor.engine.plugin.PluginContext;
import com.cqie.datafactory.executor.engine.plugin.PluginRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExecEngineRetryTest {

    private ExecEngine execEngine;
    private PluginRegistry pluginRegistry;

    @BeforeEach
    void setUp() {
        pluginRegistry = new PluginRegistry(List.of(new FlakyDbPlugin()));
        execEngine = new ExecEngine(pluginRegistry);
    }

    @Test
    void shouldRetryOnFailureAndSucceed() {
        String dsl = """
        {
            "graph": {
                "nodes": [
                    {"id":"start","type":"START","name":"Start"},
                    {"id":"db1","type":"DB","name":"DB Step","fieldValues":{"retryCount":"3"}},
                    {"id":"end","type":"END","name":"End"}
                ],
                "edges": [
                    {"source":{"nodeId":"start"},"target":{"nodeId":"db1"}},
                    {"source":{"nodeId":"db1"},"target":{"nodeId":"end"}}
                ]
            }
        }""";

        List<ExecEngine.NodeExecutionRecord> records = new ArrayList<>();
        Map<String, Object> result = execEngine.execute(dsl, "TEST", null, records::add);

        assertEquals(2, records.stream().filter(r -> "SUCCESS".equals(r.status)).count());
        assertEquals(1, records.stream().filter(r -> "DB".equals(r.nodeType)).count());
        ExecEngine.NodeExecutionRecord dbRecord = records.stream()
                .filter(r -> "DB".equals(r.nodeType)).findFirst().orElseThrow();
        assertEquals("SUCCESS", dbRecord.status);
        assertTrue(dbRecord.retryCount >= 0);
    }

    /**
     * Fails first 2 times, succeeds on 3rd attempt.
     */
    static class FlakyDbPlugin implements ComponentPlugin {
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public Set<String> supportedTypes() { return Set.of("DB", "MYSQL", "DATABASE", "JDBC"); }

        @Override
        public Map<String, Object> execute(PluginContext context) {
            int count = callCount.incrementAndGet();
            if (count <= 2) {
                throw new RuntimeException("Simulated DB failure #" + count);
            }
            return Map.of("rows", List.of(Map.of("result", "ok")));
        }
    }
}
```

- [ ] **Step 5: 创建 TaskScheduleExecutorTest**

```java
package com.cqie.datafactory.executor.schedule;

import com.cqie.datafactory.executor.schedule.entity.ScheduleJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TaskScheduleExecutorTest {

    @Test
    void shouldCalculateNextFireTimeFromCronExpression() {
        String cronExpr = "0 0 9 * * ?"; // Every day at 9:00 AM
        CronExpression cron = CronExpression.parse(cronExpr);
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 8, 0);
        LocalDateTime next = cron.next(now);
        assertNotNull(next);
        assertEquals(9, next.getHour());
        assertEquals(0, next.getMinute());
    }

    @Test
    void shouldHandleInvalidCronGracefully() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("invalid cron"));
    }

    @Test
    void shouldReturnNullForBlankCron() {
        ScheduleJobService service = new ScheduleJobService();
        ScheduleJob job = new ScheduleJob();
        job.setCronExpression(null);
        service.computeNextFireTime(job);
        assertNull(job.getNextFireTime());

        job.setCronExpression("");
        service.computeNextFireTime(job);
        assertNull(job.getNextFireTime());
    }

    @Test
    void shouldSetNextFireTimeForValidCron() {
        ScheduleJobService service = new ScheduleJobService();
        ScheduleJob job = new ScheduleJob();
        job.setCronExpression("0 0 12 * * ?");
        service.computeNextFireTime(job);
        assertNotNull(job.getNextFireTime());
    }
}
```

- [ ] **Step 6: 创建 ExecutionLogServiceTest**

```java
package com.cqie.datafactory.executor.service;

import com.cqie.datafactory.executor.entity.ExecutionLog;
import com.cqie.datafactory.executor.entity.NodeExecutionLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionLogServiceTest {

    @Test
    void shouldCreateValidExecutionLog() {
        ExecutionLog log = new ExecutionLog();
        log.setExecutionId("exec_test_001");
        log.setTaskId(1L);
        log.setTaskName("Test Task");
        log.setTaskVersion("1.0.0");
        log.setEnvironment("TEST");
        log.setStatus("RUNNING");
        log.setTriggerType("MANUAL");
        log.setStartTime(LocalDateTime.now());

        assertEquals("exec_test_001", log.getExecutionId());
        assertEquals("RUNNING", log.getStatus());
        assertNotNull(log.getStartTime());
    }

    @Test
    void shouldCreateValidNodeExecutionLog() {
        NodeExecutionLog nodeLog = new NodeExecutionLog();
        nodeLog.setExecutionId("exec_test_001");
        nodeLog.setNodeId("node1");
        nodeLog.setNodeName("DB Query");
        nodeLog.setNodeType("DB");
        nodeLog.setStatus("SUCCESS");
        nodeLog.setRetryCount(0);
        nodeLog.setDurationMs(150L);

        assertEquals("SUCCESS", nodeLog.getStatus());
        assertEquals(0, nodeLog.getRetryCount());
        assertEquals(150L, nodeLog.getDurationMs());
    }

    @Test
    void shouldHandleFailureStatus() {
        ExecutionLog log = new ExecutionLog();
        log.setExecutionId("exec_test_002");
        log.setStatus("FAILURE");
        log.setErrorMessage("Connection timeout");

        assertEquals("FAILURE", log.getStatus());
        assertEquals("Connection timeout", log.getErrorMessage());
    }
}
```

- [ ] **Step 7: 运行测试确认全部通过**

```bash
cd datafactory-backend-executor/datafactory-backend-executor-server
mvn test -pl . -am
```

Expected: All new tests pass along with existing tests.

- [ ] **Step 8: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/pom.xml datafactory-backend-executor/datafactory-backend-executor-server/src/test/
git commit -m "test: add integration tests for FilterPlugin, BranchPlugin, ExecEngine retry, Cron, and ExecutionLog"
```

---

### Task 8: Nacos 配置中心启用

**Files:**
- Modify: `datafactory-backend-configuration/src/main/resources/application.yml`
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml`

- [ ] **Step 1: 启用 Configuration 服务的 Nacos Config**

将 `datafactory-backend-configuration/src/main/resources/application.yml` 中的：

```yaml
      config:
        enabled: false
        import-check:
          enabled: false
```

改为：

```yaml
      config:
        enabled: true
        import-check:
          enabled: true
```

- [ ] **Step 2: 启用 Executor 服务的 Nacos Config**

将 `datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml` 中的：

```yaml
      config:
        enabled: false
        import-check:
          enabled: false
```

改为：

```yaml
      config:
        enabled: true
        import-check:
          enabled: true
```

注意：启用后需要在 Nacos 控制台（`127.0.0.1:8848/nacos`）中创建对应的配置项（Data ID 为服务名，如 `datafactory-backend-configuration.yml` 和 `datafactory-backend-executor-server.yml`），否则服务启动时会因为找不到配置而可能报错。如暂时不想在 Nacos 中管理配置，可以在各服务的 `application.yml` 中保留现有配置作为默认值，同时添加 `spring.cloud.nacos.config.refresh-enabled: true` 以支持动态刷新。

- [ ] **Step 3: 提交**

```bash
git add datafactory-backend-configuration/src/main/resources/application.yml datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml
git commit -m "feat: enable Nacos config center for configuration and executor services"
```

---

### Task 9: 清理调试日志

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java`

- [ ] **Step 1: 移除 page 方法中的 System.out.println 调试语句**

删除 `page` 方法（约第 439-484 行）中的以下行：

```java
        System.out.println("DEBUG: [Page Query Start] current=" + current + ", size=" + size + ", keyword=" + keyword
                + ", status=" + status);
        System.out.println("DEBUG: [DB Check] Total records in 'task' table: " + totalCount);
        System.out.println("DEBUG: [Query Result] total=" + resultPage.getTotal() + ", recordsSize="
                + resultPage.getRecords().size());
        System.out.println(
                "DEBUG: [Warning] Found total records but page result is empty. Check if pagination interceptor is working or if filters are too strict.");
```

同时移除不再使用的 `totalCount` 变量声明。

- [ ] **Step 2: 提交**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/service/impl/ExecutorTaskServiceImpl.java
git commit -m "chore: remove debug println statements from ExecutorTaskServiceImpl"
```

---
