# SQL 执行组件 & Shell 执行组件 设计文档

**日期**: 2026-06-11  
**状态**: 设计中

---

## 一、目标

1. 改造 `COMP_DB_QUERY`：去掉内联 `sql` 字段，改为从脚本管理选择 SQL 脚本
2. 新建 `COMP_SHELL_EXECUTOR`：Shell 脚本执行组件
3. 脚本版本编辑器根据 `script_type` 动态显示配置项
4. `scriptCode` 下拉框根据组件类型过滤可选的脚本类型

---

## 二、组件定义

### 2.1 组件对比

| 字段 | COMP_DB_QUERY（改造后） | COMP_SHELL_EXECUTOR（新建） | COMP_PYTHON_EXECUTOR（已有） |
|------|:--:|:--:|:--:|
| `scriptCode` | ✅ SQL 类型 | ✅ Shell 类型 | ✅ Python 类型 |
| `datasource` | ✅ | — | — |
| `result_var` | ✅ | ✅ | ✅ |
| `className` | — | — | ✅ |
| `methodName` | — | — | ✅ |
| `sql`（已删除） | 🗑 | — | — |

### 2.2 数据流

```
画布节点 fieldValues.scriptCode
  → ScriptFeignClient 拉取脚本管理 PROD 版本
  → 取 script_code_content 作为执行内容
  → COMP_DB_QUERY: JdbcTemplate + datasource 执行
  → COMP_SHELL_EXECUTOR: ProcessBuilder 执行
  → COMP_PYTHON_EXECUTOR: gRPC Python Server 执行
```

---

## 三、执行路由

| 组件 | 插件 | 执行方式 |
|------|------|------|
| COMP_DB_QUERY | DbPlugin | JdbcTemplate（datasource 动态连接） |
| COMP_SHELL_EXECUTOR | ScriptPlugin | ProcessBuilder |
| COMP_PYTHON_EXECUTOR | ScriptPlugin | gRPC |

---

## 四、ScriptVersionEditor 动态适配

| 配置项 | PYTHON | SQL | SHELL |
|------|:--:|:--:|:--:|
| 脚本内容 | ✅ | ✅ | ✅ |
| 变更说明 | ✅ | ✅ | ✅ |
| 超时时间 | ✅ | ✅ | ✅ |
| 重试次数 | ✅ | ✅ | ✅ |
| 类名 / 方法名 | ✅ | — | — |
| 解释器路径 | ✅ | — | ✅ |
| 工作目录 | ✅ | — | ✅ |
| 依赖 (dependencies) | ✅ | — | — |
| 环境变量 (env_vars) | ✅ | — | ✅ |
| 最大内存 | ✅ | — | — |

---

## 五、脚本类型过滤

`scriptCode` 的 `optionsSource.sourceType=SCRIPT` 解析时，根据组件过滤：

| 组件 | 只返回 |
|------|------|
| COMP_DB_QUERY | script_type = 'SQL' |
| COMP_SHELL_EXECUTOR | script_type = 'SHELL' |
| COMP_PYTHON_EXECUTOR | script_type = 'PYTHON' |

后端 `FieldOptionsService.resolveScript()` 新增 `scriptType` 参数。  
前端 `TaskDevVersionEditor.vue` 加载选项时传入组件对应的脚本类型。

---

## 六、改动清单

| 层 | 文件 | 改动 |
|------|------|------|
| **DB** | `datafactory.sql` | COMP_DB_QUERY 删 `sql` 加 `scriptCode`；新增 COMP_SHELL_EXECUTOR 及字段 |
| **Java** | `DbPlugin.java` | 读 `scriptCode` → Feign 拉脚本 PROD 版本 → 替换内联 SQL |
| **Java** | `FieldOptionsService.java` | `resolveScript()` 支持 `scriptType` 过滤参数 |
| **Java** | `FieldOptionsResolveDTO.java` | 新增 `scriptType` 字段 |
| **前端** | `ScriptVersionEditor.vue` | 根据 `formState.type` 动态显示/隐藏配置区 |
| **前端** | `TaskDevVersionEditor.vue` | 加载 scriptCode 选项时传入 scriptType 过滤 |

---

## 七、不改变的部分

- `ScriptPlugin.java` — 已支持 SHELL 类型，不需要改
- `ExecEngine.java` — `scriptCode` 字段检测不变，SHELL 类型由 `category="数据处理"` 兜底路由到 ScriptPlugin
- 脚本管理 CRUD（ScriptPage/ScriptController）— 不变
