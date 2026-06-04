# 定时任务参数配置功能 - 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 SchedulePage 创建/编辑定时任务时，选择任务版本后动态展示该版本所有节点的 IO 参数，用户可填写参数值并随定时任务一起保存。

**Architecture:** 后端新增 API 聚合查询版本下所有节点的 IO 参数；schedule_job 实体新增 paramsConfig 字段存储参数值 JSON；前端 SchedulePage 表单中嵌入折叠面板展示参数配置。

**Tech Stack:** Java/Spring Boot/MyBatis-Plus, Vue 3/Ant Design Vue, MySQL

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `project/datafactory/datafactory.sql` | 已修改 | schedule_job 新增 params_config 列 |
| `.../schedule/entity/ScheduleJob.java` | 修改 | 新增 paramsConfig 字段 + getter/setter |
| `.../controller/TaskDslController.java` | 修改 | 新增 GET /task-dsl/{taskDslId}/all-io-params |
| `frontend/src/api/scheduleApi.js` | 修改 | 新增 fetchVersionIoParams 方法 |
| `frontend/src/views/SchedulePage.vue` | 修改 | 表单中新增参数配置区域 |

---

### Task 1: ScheduleJob 实体新增 paramsConfig 字段

**Files:**
- Modify: `project/datafactory/datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/entity/ScheduleJob.java`

**Approach:** 在现有字段下方添加 `paramsConfig` 字段及对应的 getter/setter。

- [ ] **Step 1: 在 ScheduleJob.java 中新增字段**

在 `alarmEmail` 字段之后、`lastExecutionId` 字段之前，添加：

```java
// === 参数配置 ===
private String paramsConfig;
```

- [ ] **Step 2: 新增 getter/setter**

在其他 getter/setter 区域（`setAlarmEmail` 之后，`getLastExecutionId` 之前），添加：

```java
public String getParamsConfig() { return paramsConfig; }
public void setParamsConfig(String paramsConfig) { this.paramsConfig = paramsConfig; }
```

- [ ] **Step 3: 验证编译通过**

```bash
cd "d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory" && mvn compile -pl datafactory-backend-executor/datafactory-backend-executor-server -am -q
```

- [ ] **Step 4: Commit**

```bash
git add project/datafactory/datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/schedule/entity/ScheduleJob.java
git commit -m "feat: add paramsConfig field to ScheduleJob entity"
```

---

### Task 2: TaskDslController 新增聚合 IO 参数 API

**Files:**
- Modify: `project/datafactory/datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/controller/TaskDslController.java`

**Dependencies:** 需要在 TaskDslController 中注入 `NodeInstanceMapper` 和 `NodeIoParamValueMapper`。

**API:** `GET /task-dsl/{taskDslId}/all-io-params`
- 1. 根据 taskDslId 查询 node_instance 表获取所有节点实例
- 2. 遍历节点实例，查询 node_io_param_value 表获取每个节点的 IO 参数
- 3. 为每条参数附加 nodeId 和 nodeName，返回扁平列表

- [ ] **Step 1: 在 TaskDslController 中注入 Mapper 依赖**

修改构造函数，新增 `NodeInstanceMapper` 和 `NodeIoParamValueMapper` 参数：

```java
package com.cqie.datafactory.executor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.executor.entity.NodeInstance;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.mapper.NodeInstanceMapper;
import com.cqie.datafactory.executor.mapper.NodeIoParamValueMapper;
import com.cqie.datafactory.executor.service.TaskDslService;
import com.cqie.datafactory.executor.service.dto.TaskDslCreateDTO;
import com.cqie.datafactory.executor.service.dto.TaskDslPromoteDTO;
import com.cqie.datafactory.executor.service.vo.TaskDslVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/task-dsl")
public class TaskDslController {

    private final TaskDslService taskDslService;
    private final NodeInstanceMapper nodeInstanceMapper;
    private final NodeIoParamValueMapper nodeIoParamValueMapper;

    public TaskDslController(TaskDslService taskDslService,
                              NodeInstanceMapper nodeInstanceMapper,
                              NodeIoParamValueMapper nodeIoParamValueMapper) {
        this.taskDslService = taskDslService;
        this.nodeInstanceMapper = nodeInstanceMapper;
        this.nodeIoParamValueMapper = nodeIoParamValueMapper;
    }
```

- [ ] **Step 2: 新增 allIoParams 方法**

在类末尾（最后一个 `}` 之前）添加新方法：

```java
    @GetMapping("/{taskDslId}/all-io-params")
    public Result<List<Map<String, Object>>> allIoParams(@PathVariable("taskDslId") Long taskDslId) {
        // 1. 查该版本下所有节点实例
        List<NodeInstance> instances = nodeInstanceMapper.selectList(
                new LambdaQueryWrapper<NodeInstance>()
                        .eq(NodeInstance::getTaskDslId, taskDslId));

        // 2. 构建 nodeInstanceId -> NodeInstance 映射
        Map<Long, NodeInstance> instanceMap = new HashMap<>();
        for (NodeInstance inst : instances) {
            instanceMap.put(inst.getId(), inst);
        }

        // 3. 收集所有 nodeInstanceId
        List<Long> instanceIds = new ArrayList<>(instanceMap.keySet());
        if (instanceIds.isEmpty()) {
            return Result.success(List.of());
        }

        // 4. 批量查询所有 IO 参数
        List<NodeIoParamValue> allParams = nodeIoParamValueMapper.selectList(
                new LambdaQueryWrapper<NodeIoParamValue>()
                        .in(NodeIoParamValue::getNodeInstanceId, instanceIds)
                        .orderByAsc(NodeIoParamValue::getSortOrder, NodeIoParamValue::getId));

        // 5. 组装返回数据，附加 nodeId 和 nodeName
        List<Map<String, Object>> result = new ArrayList<>();
        for (NodeIoParamValue param : allParams) {
            NodeInstance inst = instanceMap.get(param.getNodeInstanceId());
            if (inst == null) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("nodeId", inst.getNodeId());
            item.put("nodeName", inst.getNodeName());
            item.put("ioType", param.getIoType());
            item.put("paramCode", param.getParamCode());
            item.put("paramName", param.getParamName());
            item.put("dataType", param.getDataType());
            item.put("sourceType", param.getSourceType());
            item.put("sourceValue", param.getSourceValue());
            item.put("sortOrder", param.getSortOrder());
            result.add(item);
        }

        return Result.success(result);
    }
```

- [ ] **Step 3: 验证编译通过**

```bash
cd "d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory" && mvn compile -pl datafactory-backend-executor/datafactory-backend-executor-server -am -q
```

- [ ] **Step 4: Commit**

```bash
git add project/datafactory/datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/controller/TaskDslController.java
git commit -m "feat: add GET /task-dsl/{taskDslId}/all-io-params endpoint"
```

---

### Task 3: 前端 scheduleApi.js 新增 API 方法

**Files:**
- Modify: `project/datafactory/frontend/src/api/scheduleApi.js`

- [ ] **Step 1: 新增 fetchVersionIoParams 方法**

在 `statsSummary` 方法之后、`};` 之前添加：

```javascript
  // 获取任务版本下所有节点的IO参数
  fetchVersionIoParams(taskDslId) {
    return api.get(`/task-dsl/${taskDslId}/all-io-params`);
  }
```

完整方法位置（在 `statsSummary(id)` 之后）：

```javascript
export const scheduleApi = {
  list() {
    return api.get('/schedule');
  },
  get(id) {
    return api.get(`/schedule/${id}`);
  },
  create(data) {
    return api.post('/schedule', data);
  },
  update(id, data) {
    return api.put(`/schedule/${id}`, data);
  },
  remove(id) {
    return api.delete(`/schedule/${id}`);
  },
  toggle(id) {
    return api.post(`/schedule/${id}/toggle`);
  },
  trigger(id) {
    return api.post(`/schedule/${id}/trigger`);
  },
  executions(id) {
    return api.get(`/schedule/${id}/executions`);
  },
  auditLogs(id) {
    return api.get(`/schedule/${id}/audit-logs`);
  },
  dailyStats(id, days = 7) {
    return api.get(`/schedule/${id}/daily-stats`, { params: { days } });
  },
  statsSummary(id) {
    return api.get(`/schedule/${id}/stats-summary`);
  },
  fetchVersionIoParams(taskDslId) {
    return api.get(`/task-dsl/${taskDslId}/all-io-params`);
  }
};
```

- [ ] **Step 2: Commit**

```bash
git add project/datafactory/frontend/src/api/scheduleApi.js
git commit -m "feat: add fetchVersionIoParams to scheduleApi"
```

---

### Task 4: SchedulePage.vue 表单中嵌入参数配置区域

**Files:**
- Modify: `project/datafactory/frontend/src/views/SchedulePage.vue`

**Approach:** 在创建/编辑弹窗的表单中，「高级设置」分隔线之后、「a-form」结束之前，新增参数配置区域。数据流：选择版本 → 调用 API → 按节点分组 → 折叠面板展示。

- [ ] **Step 1: 新增响应式状态变量**

在 `<script setup>` 中，`const statsError = ref('');` 之后添加：

```javascript
// 参数配置
const ioParamsList = ref([]);
const paramsLoading = ref(false);
const paramsConfig = ref({});
```

- [ ] **Step 2: 新增加载参数的方法**

在 `loadVersionList` 函数之后添加：

```javascript
const loadIoParams = async () => {
  if (!form.value.taskVersionId) return;
  paramsLoading.value = true;
  ioParamsList.value = [];
  try {
    const res = await scheduleApi.fetchVersionIoParams(form.value.taskVersionId);
    const allParams = res.data?.data || [];
    ioParamsList.value = allParams;
    // 初始化 paramsConfig（按 nodeId -> ioType -> paramCode 组织参数值）
    if (editingId.value && form.value.paramsConfig) {
      // 编辑模式：从已有的 paramsConfig 恢复参数值
      try {
        const saved = typeof form.value.paramsConfig === 'string'
          ? JSON.parse(form.value.paramsConfig) : form.value.paramsConfig;
        paramsConfig.value = saved?.paramsConfig || saved || {};
      } catch (e) {
        paramsConfig.value = {};
      }
    } else {
      paramsConfig.value = {};
    }
  } catch (e) {
    ioParamsList.value = [];
  } finally {
    paramsLoading.value = false;
  }
};
```

- [ ] **Step 3: 新增按节点分组的计算属性**

在 `blockColor` 函数之后添加：

```javascript
// 按节点分组 IO 参数
const groupedIoParams = computed(() => {
  const groups = {};
  ioParamsList.value.forEach(param => {
    const key = param.nodeId;
    if (!groups[key]) {
      groups[key] = { nodeId: key, nodeName: param.nodeName, inputParams: [], outputParams: [] };
    }
    if (param.ioType === 'INPUT') {
      groups[key].inputParams.push(param);
    } else {
      groups[key].outputParams.push(param);
    }
  });
  return Object.values(groups);
});
```

- [ ] **Step 4: 模板中新增参数配置区域**

在表单模板中，`a-divider`「高级设置」下方的 `a-row` 时间窗口/告警邮箱行之后，`</a-form>` 之前，插入：

```html
        <!-- 参数配置 -->
        <template v-if="form.taskVersionId">
          <a-divider orientation="left" style="font-size:13px;">参数配置</a-divider>
          <a-spin :spinning="paramsLoading">
            <template v-if="groupedIoParams.length === 0 && !paramsLoading">
              <a-empty description="该版本暂无参数配置" style="margin: 12px 0;" />
            </template>
            <a-collapse v-else accordion>
              <a-collapse-panel
                v-for="group in groupedIoParams"
                :key="group.nodeId"
                :header="`节点: ${group.nodeName} (${group.nodeId})`"
              >
                <!-- 输入参数 -->
                <template v-if="group.inputParams.length > 0">
                  <div style="font-weight:600;margin:8px 0 4px;color:#1677ff;">输入参数</div>
                  <a-table
                    :columns="ioParamTableColumns"
                    :data-source="group.inputParams"
                    :pagination="false"
                    row-key="paramCode"
                    size="small"
                    :scroll="{ x: 'max-content' }"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.dataIndex === 'paramValue'">
                        <a-input
                          v-model:value="paramsConfig[record.nodeId + '|' + record.ioType + '|' + record.paramCode]"
                          :placeholder="'请输入' + (record.paramName || record.paramCode)"
                          size="small"
                        />
                      </template>
                    </template>
                  </a-table>
                </template>
                <!-- 输出参数 -->
                <template v-if="group.outputParams.length > 0">
                  <div style="font-weight:600;margin:12px 0 4px;color:#52c41a;">输出参数</div>
                  <a-table
                    :columns="ioParamTableColumns"
                    :data-source="group.outputParams"
                    :pagination="false"
                    row-key="paramCode"
                    size="small"
                    :scroll="{ x: 'max-content' }"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.dataIndex === 'paramValue'">
                        <a-input
                          v-model:value="paramsConfig[record.nodeId + '|' + record.ioType + '|' + record.paramCode]"
                          :placeholder="'请输入' + (record.paramName || record.paramCode)"
                          size="small"
                        />
                      </template>
                    </template>
                  </a-table>
                </template>
              </a-collapse-panel>
            </a-collapse>
          </a-spin>
        </template>
```

- [ ] **Step 5: 新增 IO 参数表格列定义**

在 `columns` 定义之后添加：

```javascript
const ioParamTableColumns = [
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 140 },
  { title: '数据类型', dataIndex: 'dataType', width: 100 },
  { title: '参数值', dataIndex: 'paramValue', width: 240 }
];
```

- [ ] **Step 6: 版本选择变更时触发参数加载**

修改 `onTaskChange` 方法（已经调用 `loadVersionList`），修改 `loadVersionList` 的末尾，加载版本列表完成后自动加载参数：

找到 `loadVersionList` 函数，在 `finally { versionLoading.value = false; }` 之前添加：

```javascript
    // 自动加载选中版本的IO参数
    if (form.value.taskVersionId) {
      loadIoParams();
    }
```

实际上应该在版本选择变更时加载。修改 `form.value.taskVersionId` 的 watch 或直接在 `onTaskChange` 和 `onEnvironmentChange` 中处理。

更好的做法：在模板中给版本选择添加 `@change` 事件，或者在已有的 `loadVersionList` 逻辑末尾处理。

修改模板中「选择版本」的 `a-select`，添加 `@change` 事件：

找到：
```html
          <a-select v-model:value="form.taskVersionId" placeholder="请先选择任务和环境" :loading="versionLoading"
            :disabled="!form.taskId || !form.environment">
```

替换为：
```html
          <a-select v-model:value="form.taskVersionId" placeholder="请先选择任务和环境" :loading="versionLoading"
            :disabled="!form.taskId || !form.environment" @change="loadIoParams">
```

- [ ] **Step 7: 保存时将 paramsConfig 序列化到 payload**

修改 `handleSave` 方法中的 payload 构建，在 `if (editingId.value)` 之前添加序列化逻辑：

```javascript
    // 序列化参数配置
    if (Object.keys(paramsConfig.value).length > 0) {
      const configArray = ioParamsList.value
        .filter(p => paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode])
        .map(p => ({
          nodeId: p.nodeId,
          nodeName: p.nodeName,
          ioType: p.ioType,
          paramCode: p.paramCode,
          paramName: p.paramName,
          dataType: p.dataType,
          paramValue: paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode]
        }));
      payload.paramsConfig = JSON.stringify({ params: configArray });
    } else {
      payload.paramsConfig = null;
    }
```

完整位置（在 `handleSave` 函数内时间转换代码之后）：

```javascript
const handleSave = async () => {
  try {
    const payload = { ...form.value };
    if (payload.windowStart && typeof payload.windowStart === 'object') {
      payload.windowStart = payload.windowStart.format('HH:mm:ss');
    }
    if (payload.windowEnd && typeof payload.windowEnd === 'object') {
      payload.windowEnd = payload.windowEnd.format('HH:mm:ss');
    }
    // 序列化参数配置
    if (Object.keys(paramsConfig.value).length > 0) {
      const filledParams = ioParamsList.value
        .filter(p => paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode])
        .map(p => ({
          nodeId: p.nodeId,
          nodeName: p.nodeName,
          ioType: p.ioType,
          paramCode: p.paramCode,
          paramName: p.paramName,
          dataType: p.dataType,
          paramValue: paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode]
        }));
      if (filledParams.length > 0) {
        payload.paramsConfig = JSON.stringify({ params: filledParams });
      } else {
        payload.paramsConfig = null;
      }
    } else {
      payload.paramsConfig = null;
    }
    if (editingId.value) {
      await scheduleApi.update(editingId.value, payload);
    } else {
      await scheduleApi.create(payload);
    }
    modalVisible.value = false;
    message.success('保存成功');
    fetchJobs();
  } catch (e) {
    message.error('保存失败: ' + e.message);
  }
};
```

- [ ] **Step 8: 编辑时恢复参数配置**

修改 `editJob` 函数，在设置表单数据后加载参数：

在 `editJob` 函数的 `modalVisible.value = true;` 之前添加：

```javascript
  // 加载版本参数
  if (form.value.taskVersionId) {
    await loadIoParams();
  }
```

并在已有 `paramsConfig` 时恢复配置：

修改 `loadIoParams` 中去掉编辑模式的判断——实际上 Step 2 的 loadIoParams 已经处理了。只需确保 `editJob` 中在 loadVersionList 之后调用 loadIoParams。

由于 `editJob` 中已有 `await loadVersionList()`，在它之后添加 `await loadIoParams()` 即可。参数值的恢复已在 Step 2 的 `loadIoParams` 中处理（检查 `form.value.paramsConfig`）。

修改 `editJob` 函数：

```javascript
const editJob = async (record) => {
  editingId.value = record.id;
  form.value = {
    ...defaultForm(),
    ...record,
    taskCode: record.taskCode || ''
  };
  await loadTaskList();
  if (!form.value.taskCode && form.value.taskId) {
    const t = taskList.value.find(item => item.id === form.value.taskId);
    form.value.taskCode = t?.taskCode || '';
  }
  if (form.value.taskId) {
    await loadVersionList();
    await loadIoParams();   // ← 新增
  }
  modalVisible.value = true;
};
```

- [ ] **Step 9: 新建时重置参数配置**

修改 `showCreateModal` 函数，重置参数相关的状态：

```javascript
const showCreateModal = () => {
  editingId.value = null;
  form.value = defaultForm();
  versionList.value = [];
  ioParamsList.value = [];   // ← 新增
  paramsConfig.value = {};   // ← 新增
  loadTaskList();
  modalVisible.value = true;
};
```

- [ ] **Step 10: 验证前端构建**

```bash
cd "d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory/frontend" && npm run build
```

- [ ] **Step 11: Commit**

```bash
git add project/datafactory/frontend/src/views/SchedulePage.vue
git commit -m "feat: add IO params configuration area in schedule job form"
```

---

### Task 5: 端到端功能验证

- [ ] **Step 1: 启动后端和前端服务**（按项目启动方式）

- [ ] **Step 2: 验证流程**
  1. 打开定时任务管理页面
  2. 点击「新建定时任务」
  3. 选择任务 → 选择环境 → 选择版本
  4. 确认参数配置区域出现，展示所有节点的输入参数和输出参数
  5. 填写部分参数值
  6. 保存定时任务
  7. 再次编辑该定时任务，确认之前填写的参数值已恢复
  8. 切换版本，确认参数列表刷新

- [ ] **Step 3: 验证数据库**

```sql
SELECT params_config FROM schedule_job WHERE id = <新建的job id>;
```

确认 JSON 格式正确且包含填写的参数值。

---

## Plan Self-Review

1. **Spec coverage:** 
   - ✅ 数据库变更（Task 0, 已在 SQL 中完成）
   - ✅ ScheduleJob 实体字段（Task 1）
   - ✅ 聚合 IO 参数 API（Task 2）
   - ✅ 前端 API 调用（Task 3）
   - ✅ 前端 UI 参数配置区域（Task 4）
2. **Placeholder scan:** ✅ 无 TBD/TODO，所有步骤包含完整代码
3. **Type consistency:** ✅ 前后端字段名一致（paramsConfig, nodeId, ioType, paramCode 等）
