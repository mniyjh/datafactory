# 脚本与过滤组件验证任务 — 详细配置过程

## 前置准备

确保以下服务已启动：
- **Configuration 服务** (端口 8080，Swagger: `http://localhost:8080/doc.html`)
- **Executor 服务** (端口 8082)
- **前端** (端口 5173) — 可选，也可全部用 API 操作
- **MySQL** 数据库（datafactory 主库已正常运行即可，无需额外初始化）

---

## 第一步：注册 Python 脚本

### 2.1 创建脚本

**API 方式：**
```bash
curl -X POST http://localhost:8080/script \
  -H "Content-Type: application/json" \
  -d '{
    "code": "verify_script_filter",
    "name": "验证脚本-订单数据生成",
    "type": "PYTHON",
    "status": "启用",
    "desc": "接收dataCount/minAmount/maxAmount/categoryList参数，生成模拟订单数据供FILTER组件过滤"
  }'
```

返回示例：`{"code": 0, "data": {"id": 10, "code": "verify_script_filter", ...}}`  
记下返回的脚本 `id`（例如 `10`）。

**UI 方式：**
1. 打开前端 → 侧边栏点击 **"脚本管理"** (`/script`)
2. 点击 **"+ 新建脚本"** 按钮
3. 填写表单：
   - 脚本编号：`verify_script_filter`
   - 脚本名称：`验证脚本-订单数据生成`
   - 脚本类型：选择 `PYTHON`
   - 状态：选择 `启用`
   - 描述：`接收参数生成模拟订单数据`
4. 点击 **"确定"**

### 2.2 创建脚本版本（DEV 环境）

**API 方式：**
```bash
# 先读取 Python 文件内容
SCRIPT_CONTENT=$(cat demo/verify_script_filter.py | python -c "import sys,json; print(json.dumps(sys.stdin.read()))")

curl -X POST http://localhost:8080/script/version \
  -H "Content-Type: application/json" \
  -d "{
    \"scriptId\": 10,
    \"environment\": \"DEV\",
    \"version\": \"1.0.0\",
    \"scriptCode\": \"verify_script_filter\",
    \"scriptCodeContent\": $(cat demo/verify_script_filter.py | python -c "import sys,json; print(json.dumps(sys.stdin.read()))"),
    \"timeout\": 30,
    \"retryCount\": 0,
    \"interpreterPath\": \"python\",
    \"workDir\": \".\",
    \"changeLog\": \"初始版本：订单数据生成脚本\"
  }"
```

> **Windows 注意**：由于 Windows 下 `python -c` 处理管道输入可能有问题，建议直接在 Swagger UI (`http://localhost:8080/doc.html`) → **脚本版本管理** → `POST /script/version` 中操作，或使用前端 UI。

**UI 方式（推荐）：**
1. 在脚本列表中，找到刚创建的 `verify_script_filter` 脚本
2. 点击该行的 **"环境"** 按钮 → 弹出环境管理弹窗
3. 在 **DEV** 标签页中，点击 **"+ 新建版本"**
4. 在 `ScriptVersionEditor` 弹窗中填写：

| 字段 | 值 | 说明 |
|------|-----|------|
| 脚本内容 | `demo/verify_script_filter.py` 的全部内容 | 复制粘贴 |
| 超时时间(秒) | `30` | Python 脚本执行超时 |
| 重试次数 | `0` | 不需要重试 |
| 解释器路径 | `python` | 或 `python3` |
| 工作目录 | `.` | 当前目录 |
| 版本号 | `1.0.0` | |
| 变更说明 | `初始版本：订单数据生成脚本` | |

5. 点击 **"保存"**

### 2.3 发布脚本到 TEST 并测试

**UI 方式：**
1. 在 DEV 标签页，选中刚创建的版本 → 点击 **"发布选中版本到TEST"**
2. 在确认弹窗中点击 **"确定"**
3. 切换到 **TEST** 标签页 → 点击该版本的 **"测试"** 按钮
4. 在 `ScriptTestModal` 中输入测试 JSON：
   ```json
   {"dataCount": 5, "minAmount": 10, "maxAmount": 500, "categoryList": "食品,电子"}
   ```
5. 点击 **"执行"** → 查看 stdout 输出是否正确

### 2.4 发布脚本到 PROD 并设为当前版本

**UI 方式：**
1. 在 TEST 标签页，选中版本 → 点击 **"发布选中版本到PROD"**
2. 切换到 **PROD** 标签页 → 选中版本 → 点击 **"选中"**（设为当前执行版本）

**API 方式（PROD 发布+设当前）：**
```bash
# 假设 version id = 15
curl -X POST http://localhost:8080/script/version/15/publish
curl -X POST http://localhost:8080/script/version/15/current
```

---

## 第二步：创建任务

### 3.1 创建任务

**API 方式：**
```bash
curl -X POST http://localhost:8082/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "taskCode": "VERIFY_SCRIPT_FILTER",
    "taskName": "脚本与过滤组件验证",
    "description": "Python脚本生成模拟订单数据 → FILTER过滤高金额订单 → 验证IO参数联动"
  }'
```

返回示例：`{"code": 0, "data": {"id": 11, "taskCode": "VERIFY_SCRIPT_FILTER", ...}}`  
记下返回的任务 `id`（例如 `11`）。

**UI 方式：**
1. 打开前端 → 侧边栏点击 **"任务管理"** (`/task`)
2. 点击 **"+ 新建任务"** 按钮
3. 填写表单：
   - 任务编号：`VERIFY_SCRIPT_FILTER`
   - 任务名称：`脚本与过滤组件验证`
   - 描述：`Python脚本生成模拟订单数据 → FILTER过滤高金额订单`
4. 点击 **"确定"**

### 3.2 创建任务 DSL 版本（DEV 环境）

这是最关键的一步——在画布中配置节点、连线、IO 参数。

**UI 方式：**
1. 在任务列表中，找到 `VERIFY_SCRIPT_FILTER` 任务
2. 点击该行的 **"环境"** 按钮 → 弹出环境管理弹窗
3. 在 **DEV** 标签页中，点击 **"+ 新建版本"**
4. 打开 `TaskDevVersionEditor` 可视化编辑器

---

### 3.2.1 在画布上拖入 4 个节点

从左侧组件面板拖入以下节点到画布：

| 节点 | 从面板中选择 | 画布位置 |
|------|------------|---------|
| 开始 | 流程控制 → 开始节点 | x:100, y:200 |
| 脚本 | 数据处理 → 脚本执行 | x:340, y:200 |
| 过滤 | 数据处理 → 数据过滤 | x:580, y:200 |
| 结束 | 流程控制 → 结束节点 | x:800, y:200 |

### 3.2.2 连线

从节点输出端口(右侧圆点)拖动到下一节点的输入端口(左侧圆点)：

```
[开始] ──→ [脚本执行] ──→ [数据过滤] ──→ [结束]
```

### 3.2.3 配置 START 节点 — 输出参数

双击 **"开始"** 节点 → 打开属性编辑面板 → 切换到 **"输出参数"** 标签页：

点击 **"+ 添加参数"** 4 次，依次填写：

| # | paramCode | paramName | dataType | paramSpace | defaultValue |
|---|-----------|-----------|----------|------------|--------------|
| 1 | `dataCount` | 生成条数 | NUMBER | TASK | `12` |
| 2 | `minAmount` | 最低单价 | NUMBER | TASK | `50` |
| 3 | `maxAmount` | 最高单价 | NUMBER | TASK | `3000` |
| 4 | `categoryList` | 商品类别 | STRING | TASK | `食品,电子,服装,家居,运动` |

**说明**：START 节点的 outputParams 定义了任务级初始参数。这些参数会被下游节点的 inputParams 通过 `UPSTREAM_OUTPUT` 引用。

### 3.2.4 配置 SCRIPT 节点 — 字段属性

双击 **"脚本执行"** 节点 → 切换到 **"组件字段"** 标签页：

| 字段名 | 值 |
|--------|-----|
| scriptCode | `verify_script_filter` |
| result_var | `orderData` |

> `result_var` 会把脚本的完整输出存入全局变量 `orderData`，供后续节点的表达式引用。

### 3.2.5 配置 SCRIPT 节点 — 输入参数（关键：UPSTREAM_OUTPUT 联动）

切换到 **"输入参数"** 标签页，点击 **"+ 添加参数"** 4 次：

| # | paramCode | paramName | dataType | paramSpace | sourceType | sourceValue |
|---|-----------|-----------|----------|------------|------------|-------------|
| 1 | `dataCount` | 生成条数 | NUMBER | NODE | **UPSTREAM_OUTPUT** | `s1.dataCount` |
| 2 | `minAmount` | 最低单价 | NUMBER | NODE | **UPSTREAM_OUTPUT** | `s1.minAmount` |
| 3 | `maxAmount` | 最高单价 | NUMBER | NODE | **UPSTREAM_OUTPUT** | `s1.maxAmount` |
| 4 | `categoryList` | 商品类别 | STRING | NODE | **UPSTREAM_OUTPUT** | `s1.categoryList` |

> **重点**：`sourceType = UPSTREAM_OUTPUT`，`sourceValue` 使用 `{上游节点id}.{参数code}` 格式（对象格式 `{"nodeId":"s1","paramCode":"dataCount"}` 也支持）。
>
> 引擎的 `ParamResolver` 会从 `nodeOutputsMap["s1"]` 中取出对应的实际值。
>
> 解析后的 4 个值序列化为 JSON 写入 Python 进程的 **stdin**。

### 3.2.6 配置 SCRIPT 节点 — 输出参数

切换到 **"输出参数"** 标签页，点击 **"+ 添加参数"** 3 次：

| # | paramCode | paramName | dataType | paramSpace |
|---|-----------|-----------|----------|------------|
| 1 | `rows` | 订单列表 | JSON | NODE |
| 2 | `rowCount` | 订单总数 | NUMBER | NODE |
| 3 | `summary` | 金额汇总 | JSON | NODE |

> **重点**：outputParams 是**白名单**机制。Python stdout 输出的 JSON 中只有这三个字段会被提取并存入 `nodeOutputsMap`。如果 outputParams **为空**，则整个 stdout JSON 都会暴露给下游。

### 3.2.7 配置 FILTER 节点 — 字段属性

双击 **"数据过滤"** 节点 → 切换到 **"组件字段"** 标签页：

| 字段名 | 值 |
|--------|-----|
| filterMode | `EXPRESSION` |
| expression | `totalAmount > 1000` |
| sourceNodeId | `sc1` |
| result_var | `highValueOrders` |

**说明**：
- `sourceNodeId = sc1`：告诉 FILTER 从哪个上游节点取数据
- `expression = totalAmount > 1000`：Aviator 表达式，对 `rows` 中每条记录求值
- `result_var = highValueOrders`：过滤结果存入全局变量

### 3.2.8 配置 FILTER 节点 — 输出参数

切换到 **"输出参数"** 标签页，点击 **"+ 添加参数"** 2 次：

| # | paramCode | paramName | dataType | paramSpace |
|---|-----------|-----------|----------|------------|
| 1 | `rows` | 高金额订单列表 | JSON | NODE |
| 2 | `rowCount` | 高金额订单数量 | NUMBER | NODE |

### 3.2.9 END 节点

**"结束"** 节点不需要额外配置。`inputParams` 为空时，引擎会自动汇总所有上游节点的输出作为最终结果。

### 3.2.10 保存 DSL

点击编辑器左上角 **"保存"** 按钮 → 填写：

| 字段 | 值 |
|------|-----|
| 版本号 | `1.0.0` |
| 变更说明 | `初始版本：脚本+过滤验证` |

> 保存时调用的 API：`POST /task-dsl/version`，body 包含 `taskId`, `environment: "DEV"`, `version`, `dslContent`（画布序列化的完整 JSON）, `changeLog`。

### 3.2.11 同步组件快照（重要）

保存后，点击 **"同步节点"** 按钮（或调用 API）：
```bash
# 假设 dsl version id = 20
curl -X POST http://localhost:8082/component-snapshot/20/rebuild
```

> 这一步将组件的 field 定义和 IO param 定义快照到 `node_instance` / `node_field_value` / `node_io_param_value` 表，确保执行时引擎能正确读取配置。

---

## 第三步：发布并执行

### 4.1 发布 DSL 到 TEST 并测试

**UI 方式：**
1. 在 DEV 标签页，选中 `1.0.0` 版本 → 点击 **"发布选中版本到TEST"**
2. 切换到 **TEST** 标签页 → 点击该版本的 **"测试"** 按钮
3. `TaskTestModal` 打开 → 点击 **"执行"**（不需要额外参数，START 节点已有 defaultValue）
4. 等待执行完成 → 查看结果

### 4.2 发布到 PROD 并执行

**UI 方式：**
1. 在 TEST 标签页，选中版本 → 点击 **"发布选中版本到PROD"**
2. 切换到 **PROD** 标签页 → 选中版本 → 点击 **"选中"**（设为当前版本）
3. 回到任务列表 → 点击任务的 **"执行"** 按钮（或在 TEST 弹窗中执行）

**API 方式（PROD）：**
```bash
# 1. 发布
curl -X POST http://localhost:8082/task-dsl/version/20/publish

# 2. 设为当前版本
curl -X POST http://localhost:8082/task-dsl/version/20/current

# 3. 执行任务
curl -X POST http://localhost:8082/tasks/11/execute \
  -H "Content-Type: application/json" \
  -d '{}'

# 返回 executionId，例如: {"code": 0, "data": "exec_abc123"}
```

---

## 第四步：查看执行结果

### 5.1 查看整体结果

```bash
curl http://localhost:8082/executor/log/detail/exec_abc123
```

关键响应字段：
```json
{
  "code": 0,
  "data": {
    "status": "SUCCESS",
    "outputResult": "{\"rows\":[{...高金额订单...}],\"rowCount\":5}",
    "startTime": "2026-05-29T10:30:00",
    "endTime": "2026-05-29T10:30:03",
    "durationMs": 3200
  }
}
```

### 5.2 查看每个节点的执行状态

```bash
curl http://localhost:8082/executor/log/nodes/exec_abc123
```

预期结果：
```json
{
  "data": [
    {"nodeId": "s1",  "nodeName": "开始",           "nodeType": "START",  "status": "SUCCESS"},
    {"nodeId": "sc1", "nodeName": "生成模拟订单",    "nodeType": "SCRIPT", "status": "SUCCESS"},
    {"nodeId": "f1",  "nodeName": "过滤高金额订单",  "nodeType": "FILTER", "status": "SUCCESS"},
    {"nodeId": "e1",  "nodeName": "结束",           "nodeType": "END",    "status": "SUCCESS"}
  ]
}
```

**UI 方式**：侧边栏 → **"执行日志"** (`/execute-log`) → 查看执行记录和每个节点的详情。

---

## 数据流转总结

```
START.outputParams             SCRIPT.inputParams           Python stdin
┌─────────────────┐           ┌────────────────────┐       ┌──────────────────────┐
│ dataCount=12    │──UPSTREAM─│ dataCount          │       │ {"dataCount":"12",   │
│ minAmount=50    │──OUTPUT──→│ minAmount          │──→JSON→│  "minAmount":"50",   │
│ maxAmount=3000  │  引用     │ maxAmount          │ 序列化 │  "maxAmount":"3000", │
│ categoryList=   │           │ categoryList       │       │  "categoryList":     │
│   "食品,电子.." │           └────────────────────┘       │   "食品,电子,..."}   │
└─────────────────┘                                       └──────────────────────┘
                                                                   │
                                                              Python stdout
                                                           ┌──────────────────────┐
                                                           │ {"rows":[...12条],   │
                                                           │  "rowCount":12,      │
                         FILTER.sourceNodeId               │  "summary":{...}}    │
                         ┌────"sc1"────┐                   └──────────────────────┘
                         ↓             │                            │
                    nodeOutputsMap     │              SCRIPT.outputParams 白名单提取
                    ["sc1"].rows       │              rows, rowCount, summary
                         │             │                   │
                         ↓             │                   ↓
                    Aviator 表达式      │            nodeOutputsMap["sc1"]
                    totalAmount>1000   │
                         │             │
                         ↓             │
                    filtered rows      │
                    约5-7条            │
                         │             │
                         ↓             │
                    FILTER.outputParams│              END
                    白名单: rows,      │              汇总所有上游输出
                    rowCount           │              = 最终 JSON 结果
                         ↓             │
                    nodeOutputsMap["f1"]
                         ↓
                       END
```

---

## 排查问题

| 问题 | 检查项 |
|------|--------|
| 脚本执行失败 | `interpreterPath` 是否正确（`python` vs `python3`）；`ScriptTestModal` 中单独测试脚本 |
| FILTER 过滤为空 | 检查 `sourceNodeId` 是否匹配 SCRIPT 节点 id；检查 `expression` 字段名是否与 Python stdout 一致（`totalAmount` 驼峰） |
| 参数未传递 | 检查 SCRIPT 的 `inputParams[].sourceType` 是否为 `UPSTREAM_OUTPUT`；`sourceValue` 节点 id 是否正确 |
| 输出参数为空 | 检查 `outputParams` 的 `paramCode` 是否与 Python stdout JSON 的 key 名一致 |
| 节点状态 FAILURE | `GET /executor/log/nodes/{execId}` 查看具体节点的 `errorMessage` |
