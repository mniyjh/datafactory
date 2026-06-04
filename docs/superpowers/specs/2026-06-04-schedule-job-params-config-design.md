# 定时任务参数配置功能 - 设计文档

**日期:** 2026-06-04
**作者:** Claude + mniyjh
**状态:** 已批准

---

## 1. 需求概述

在定时任务管理页面（SchedulePage）中，创建/编辑定时任务时，选择任务版本后，表单下方动态展示该版本所有节点的输入参数和输出参数，用户可直接填写参数值。参数配置存储在 `schedule_job` 表中，不影响原始任务版本的参数定义。

## 2. 数据库变更

### 2.1 schedule_job 表新增字段

在 `CREATE TABLE schedule_job` 中新增一列（位于 `alarm_email` 之后）：

```sql
params_config LONGTEXT DEFAULT NULL COMMENT '定时任务参数配置(JSON)',
```

### 2.2 params_config JSON 结构

```json
{
  "params": [
    {
      "nodeId": "n_abc123",
      "nodeName": "数据库查询",
      "ioType": "INPUT",
      "paramCode": "sql",
      "paramName": "SQL语句",
      "dataType": "STRING",
      "sourceType": "CONST",
      "sourceValue": "",
      "paramValue": "SELECT * FROM users WHERE status = 1"
    },
    {
      "nodeId": "n_def456",
      "nodeName": "结束节点",
      "ioType": "OUTPUT",
      "paramCode": "result",
      "paramName": "执行结果",
      "dataType": "JSON",
      "paramValue": ""
    }
  ]
}
```

- `paramValue` 是用户在定时任务中配置的实际参数值
- 其他字段（nodeId, paramCode, dataType 等）从任务版本的节点参数快照中读取，仅用于展示

## 3. 后端改动

### 3.1 实体变更

**文件:** `ScheduleJob.java`
- 新增字段 `private String paramsConfig;` 及其 getter/setter

### 3.2 新增 API

**端点:** `GET /task-dsl/{taskDslId}/all-io-params`

**功能:** 根据任务版本 ID（task_dsl.id）查询该版本下所有节点的输入/输出参数

**实现逻辑:**
1. 根据 `taskDslId` 查询 `node_instance` 表获取所有节点实例
2. 遍历节点实例，查询 `node_io_param_value` 表获取每个节点的 IO 参数
3. 组装成包含 `nodeId`, `nodeName`, `ioType`, `paramCode`, `paramName`, `dataType`, `sourceType`, `sourceValue`, `sortOrder` 等字段的列表返回

**Controller 位置:** `TaskDslController.java` 中新增方法

**响应示例:**
```json
{
  "code": 0,
  "data": [
    {
      "nodeId": "n_abc",
      "nodeName": "开始节点",
      "ioType": "INPUT",
      "paramCode": "userName",
      "paramName": "用户名",
      "dataType": "STRING",
      "sourceType": "CONST",
      "sourceValue": "",
      "sortOrder": 1
    },
    {
      "nodeId": "n_abc",
      "nodeName": "开始节点",
      "ioType": "INPUT",
      "paramCode": "age",
      "paramName": "年龄",
      "dataType": "NUMBER",
      "sourceType": "CONST",
      "sourceValue": "",
      "sortOrder": 2
    }
  ]
}
```

### 3.3 ScheduleJob 保存/更新

`ScheduleJobController.create()` 和 `update()` 方法已通过 `@RequestBody ScheduleJob` 接收参数，新增的 `paramsConfig` 字段会自动由 Jackson 反序列化并存储。无需额外改动。

## 4. 前端改动

### 4.1 API 层

**文件:** `scheduleApi.js`
- 新增方法: `fetchVersionIoParams(taskDslId)` — 调用 `GET /task-dsl/{taskDslId}/all-io-params`

### 4.2 页面改动

**文件:** `SchedulePage.vue`

在创建/编辑弹窗中，`a-divider`「高级设置」下方新增「参数配置」区域：

**UI 布局:**
```
┌─ 参数配置 ──────────────────────────────────────┐
│ 选择版本后自动加载参数                            │
│ ┌─ 节点: 开始节点 ────────────────────────────┐  │
│ │ 【输入参数】                                │  │
│ │ 参数编码   | 参数名称 | 数据类型 | 参数值    │  │
│ │ userName   | 用户名   | STRING  | [______]  │  │
│ │ age        | 年龄     | NUMBER  | [______]  │  │
│ │ 【输出参数】                                │  │
│ │ result     | 结果     | STRING  | [______]  │  │
│ └────────────────────────────────────────────┘  │
│ ┌─ 节点: 数据库查询 ─────────────────────────┐  │
│ │ ...                                         │  │
│ └────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

**实现要点:**
1. 使用 `a-collapse`（折叠面板）按节点分组
2. 每组的表头标注是「输入参数」还是「输出参数」
3. 参数编码、参数名称、数据类型列只读展示
4. 「参数值」列可编辑（使用 `a-input`）
5. 选择版本后自动调用 API 加载参数列表
6. 保存时将参数值序列化为 JSON 存入 `paramsConfig`

### 4.3 数据流

```
用户选择任务版本
  → onChange 触发
  → 调用 fetchVersionIoParams(taskVersionId)
  → 后端查询 node_instance + node_io_param_value
  → 返回参数列表
  → 前端按 nodeId 分组展示
  → 用户填写 paramValue
  → 保存时表单序列化 paramsConfig
  → 提交到 POST/PUT /schedule
```

## 5. 兼容性说明

- `paramsConfig` 字段为可空 LONGTEXT，不影响现有定时任务
- 前端参数配置区域在选择版本后才出现，不影响当前表单布局
- 如果版本没有任何 IO 参数，参数配置区域显示「该版本暂无参数配置」

## 6. 测试要点

- 新建定时任务时配置参数，保存后再次编辑能看到已配置的参数值
- 切换任务版本后，参数列表自动刷新为新版本的参数
- 定时任务执行时，配置的参数值能正确传递到执行引擎
- 无参数的版本不显示空表格，显示提示文字
