# 加法任务 — 输入输出参数配置表

## 任务流水线

```
[开始节点 s1] ──→ [脚本执行 sc1] ──→ [结束节点 e1]
```

---

## 一、开始节点 (START) — `s1`

**组件**: COMP_START / 开始节点  
**作用**: 定义任务的初始输入参数，传递加数 a 和 b 给下游脚本

### 输出参数 (outputParams)

| 序号 | 参数编码 (paramCode) | 参数名称 (paramName) | 数据类型 (dataType) | 参数空间 (paramSpace) | 默认值 (defaultValue) | 说明 |
|:---:|---------------------|---------------------|-------------------|---------------------|---------------------|------|
| 1 | `a` | 加数a | NUMBER | TASK | `5` | 加法运算的第一个加数，下游用 UPSTREAM_OUTPUT 引用 |
| 2 | `b` | 加数b | NUMBER | TASK | `3` | 加法运算的第二个加数，下游用 UPSTREAM_OUTPUT 引用 |

> **注意**: 开始节点的 outputParams 会写入 `nodeOutputsMap["s1"]`。`paramSpace = TASK` 表示这些参数也可以在任务执行时由外部调用方覆盖。

---

## 二、脚本执行节点 (SCRIPT) — `sc1`

**组件**: COMP_SCRIPT / 脚本执行  
**作用**: 调用 Python 加法脚本，计算两数之和与乘积

### 组件字段 (fieldValues)

| 字段编码 (fieldCode) | 字段名称 | 值 | 说明 |
|---------------------|---------|-----|------|
| `scriptCode` | 选择脚本 | `add_script` | 脚本管理中注册的脚本编码，需先在脚本管理 → 创建版本 → 发布到 PROD |
| `result_var` | 结果变量 | `calcResult` | 将脚本完整输出存入全局变量 `calcResult`，供后续节点表达式引用 |

### 输入参数 (inputParams)

| 序号 | 参数编码 (paramCode) | 参数名称 (paramName) | 数据类型 (dataType) | 来源类型 (sourceType) | 来源值 (sourceValue) | 说明 |
|:---:|---------------------|---------------------|-------------------|---------------------|---------------------|------|
| 1 | `a` | 加数a | NUMBER | **UPSTREAM_OUTPUT** | `s1.a` | 引用开始节点 s1 输出的参数 a |
| 2 | `b` | 加数b | NUMBER | **UPSTREAM_OUTPUT** | `s1.b` | 引用开始节点 s1 输出的参数 b |

> **关键**: `sourceType = UPSTREAM_OUTPUT` 表示从上游节点取值。`sourceValue` 格式为 `{上游节点id}.{参数code}`。
> 引擎的 `ParamResolver` 会从 `nodeOutputsMap["s1"]["a"]` 取出实际值 `"5"`，序列化为 JSON 后通过 stdin 传给 Python 脚本:
> ```json
> {"a": "5", "b": "3"}
> ```

### 输出参数 (outputParams)

| 序号 | 参数编码 (paramCode) | 参数名称 (paramName) | 数据类型 (dataType) | 对应 Python 返回字段 | 说明 |
|:---:|---------------------|---------------------|-------------------|---------------------|------|
| 1 | `sum` | 两数之和 | NUMBER | `sum` | a + b 的结果 |
| 2 | `product` | 两数之积 | NUMBER | `product` | a × b 的结果 |
| 3 | `detail` | 计算明细 | STRING | `detail` | 可读的计算过程描述 |
| 4 | `rows` | 结果列表 | JSON | `rows` | 包含计算明细的对象数组，供下游 FILTER/SQL 等其他组件消费 |
| 5 | `rowCount` | 行数 | NUMBER | `rowCount` | rows 数组的长度 |

> **白名单机制**: outputParams 定义了哪些字段从 Python stdout JSON 中提取到 `nodeOutputsMap`。
> 如果 outputParams **为空数组**，则 stdout 的整个 JSON 都会暴露给下游节点。

---

## 三、结束节点 (END) — `e1`

**组件**: COMP_END / 结束节点  
**作用**: 汇总所有上游节点的输出，作为任务的最终执行结果

### 输入参数 (inputParams)

| 序号 | 参数编码 (paramCode) | 参数名称 (paramName) | 数据类型 (dataType) | 来源类型 (sourceType) | 来源值 (sourceValue) | 说明 |
|:---:|---------------------|---------------------|-------------------|---------------------|---------------------|------|
| — | （空） | — | — | — | — | inputParams 为空时，引擎自动汇总所有上游节点的输出 |

### 输出参数 (outputParams)

| 序号 | 参数编码 (paramCode) | 参数名称 (paramName) | 数据类型 (dataType) | 说明 |
|:---:|---------------------|---------------------|-------------------|------|
| — | （空） | — | — | 结束节点不需要定义输出参数 |

> **自动汇总**: 当 inputParams 为空且 outputParams 为空时，引擎会把 `nodeOutputsMap` 中该节点的所有上游输出合并成最终结果 JSON。

---

## 四、完整数据流转图

```
┌──────────────────────────────────────────────────────────────────────┐
│                        START 节点 (s1)                               │
│                                                                      │
│  outputParams:                                                       │
│    a = "5"  ─────────────────────────────────────────────┐           │
│    b = "3"  ───────────────────────────────────────┐     │           │
│                                                     │     │           │
│  → 写入 nodeOutputsMap["s1"] = { a: "5", b: "3" }  │     │           │
└─────────────────────────────────────────────────────│─────│──────────┘
                                                      │     │
                                          UPSTREAM_OUTPUT  │
                                          s1.a              │ s1.b
                                                      │     │
┌─────────────────────────────────────────────────────│─────│──────────┐
│                        SCRIPT 节点 (sc1)             ↓     ↓          │
│                                                                      │
│  fieldValues:                                                        │
│    scriptCode = "add_script"                                         │
│    result_var = "calcResult"                                         │
│                                                                      │
│  inputParams (UPSTREAM_OUTPUT):                                      │
│    a ← s1.a (= "5")                                                  │
│    b ← s1.b (= "3")                                                  │
│                                                                      │
│  → 序列化为 JSON → Python stdin:                                     │
│    {"a": "5", "b": "3"}                                              │
│                                                                      │
│  → Python 脚本执行 → stdout 输出:                                     │
│    {                                                                 │
│      "success": true,                                                │
│      "sum": 8.0,                                                     │
│      "product": 15.0,                                                │
│      "detail": "5.0 + 3.0 = 8.0，5.0 × 3.0 = 15.0",                 │
│      "rows": [{"a":5,"b":3,"sum":8,"product":15}],                  │
│      "rowCount": 1                                                   │
│    }                                                                 │
│                                                                      │
│  outputParams 白名单提取:                                             │
│    sum = 8.0                                                         │
│    product = 15.0                                                    │
│    detail = "5.0 + 3.0 = 8.0，5.0 × 3.0 = 15.0"                     │
│    rows = [{"a":5,"b":3,"sum":8,"product":15}]                      │
│    rowCount = 1                                                      │
│                                                                      │
│  → 写入 nodeOutputsMap["sc1"] = { sum: 8.0, product: 15.0, ... }   │
│                                                                      │
│  → 全局变量 calcResult = 完整 stdout JSON                             │
└──────────────────────────────────────────────────────────────────────┘
                                                      │
                                                      │ 自动汇总
                                                      ↓
┌──────────────────────────────────────────────────────────────────────┐
│                         END 节点 (e1)                                │
│                                                                      │
│  最终输出 = {                                                        │
│    "s1":  { "a": "5", "b": "3" },                                   │
│    "sc1": {                                                          │
│      "sum": 8.0,                                                     │
│      "product": 15.0,                                                │
│      "detail": "5.0 + 3.0 = 8.0，5.0 × 3.0 = 15.0",                 │
│      "rows": [{ "a":5, "b":3, "sum":8, "product":15 }],             │
│      "rowCount": 1                                                   │
│    }                                                                 │
│  }                                                                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 五、参数速查表

### 按节点维度

| 节点 | paramCode | paramName | IO方向 | dataType | sourceType | sourceValue | defaultValue |
|------|-----------|-----------|--------|----------|------------|-------------|--------------|
| s1 (START) | `a` | 加数a | 输出 | NUMBER | — | — | `5` |
| s1 (START) | `b` | 加数b | 输出 | NUMBER | — | — | `3` |
| sc1 (SCRIPT) | `a` | 加数a | 输入 | NUMBER | UPSTREAM_OUTPUT | `s1.a` | — |
| sc1 (SCRIPT) | `b` | 加数b | 输入 | NUMBER | UPSTREAM_OUTPUT | `s1.b` | — |
| sc1 (SCRIPT) | `sum` | 两数之和 | 输出 | NUMBER | — | — | — |
| sc1 (SCRIPT) | `product` | 两数之积 | 输出 | NUMBER | — | — | — |
| sc1 (SCRIPT) | `detail` | 计算明细 | 输出 | STRING | — | — | — |
| sc1 (SCRIPT) | `rows` | 结果列表 | 输出 | JSON | — | — | — |
| sc1 (SCRIPT) | `rowCount` | 行数 | 输出 | NUMBER | — | — | — |

### 按数据流向维度

| 数据流向 | 源节点 | 源参数 | 目标节点 | 目标参数 | 传递方式 |
|---------|--------|--------|---------|---------|---------|
| 加数a | s1 | a | sc1 | a | UPSTREAM_OUTPUT (s1.a) |
| 加数b | s1 | b | sc1 | b | UPSTREAM_OUTPUT (s1.b) |
| 两数之和 | sc1 | sum | e1 | (自动汇总) | 引擎自动合并 |
| 两数之积 | sc1 | product | e1 | (自动汇总) | 引擎自动合并 |
| 计算明细 | sc1 | detail | e1 | (自动汇总) | 引擎自动合并 |
| 结果列表 | sc1 | rows | e1 | (自动汇总) | 引擎自动合并 |
| 行数 | sc1 | rowCount | e1 | (自动汇总) | 引擎自动合并 |

---

## 六、sourceType 取值说明

| sourceType | 含义 | sourceValue 格式 | 适用场景 |
|------------|------|-----------------|---------|
| **CONST** | 常量值 | 直接写值，如 `"5"`、`"hello"` | 固定不变的参数 |
| **UPSTREAM_OUTPUT** | 上游节点输出 | `{上游节点id}.{参数code}`，如 `s1.a` | 需要从前一个节点获取数据 |
| **EXPRESSION** | 表达式 | Aviator 表达式，如 `a + b` | 需要动态计算 |

---

## 七、dataType 取值说明

| 数据类型 | 说明 | 示例值 |
|---------|------|--------|
| STRING | 字符串 | `"hello"` |
| NUMBER | 数值（整数或小数） | `5`、`3.14` |
| BOOLEAN | 布尔值 | `true`、`false` |
| JSON | JSON 对象或数组 | `[{"a":5}]`、`{"key":"val"}` |

---

## 八、paramSpace 取值说明

| 参数空间 | 含义 | 可见范围 |
|---------|------|---------|
| NODE | 节点级 | 仅当前节点可见 |
| TASK | 任务级 | 整个任务可见，执行时可覆盖 |
| ENV | 环境级 | 同一环境（DEV/TEST/PROD）下所有任务可见 |
| GLOBAL | 全局级 | 跨环境跨任务可见 |
