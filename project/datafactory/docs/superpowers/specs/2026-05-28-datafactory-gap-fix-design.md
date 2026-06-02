# Data Factory 遗漏项修复设计方案

日期：2026-05-28

## 概述

基于七周开发计划对项目进行全面审查后，识别出以下 8 项遗漏/问题需要修复（安全认证系统不在本次范围内）。

## 修复项清单

### 一、FilterPlugin 行过滤增强

**现状：** 只支持 COLUMN 模式（列投影），前端的 `condition_field`/`condition_op`/`condition_value` 字段定义但插件未使用。

**方案：** 新增 `filterMode` 字段切换三种模式：

| 模式 | 说明 | 使用字段 |
|------|------|----------|
| COLUMN | 列投影（已有） | `columns` |
| SIMPLE | 简单条件行过滤 | `condition_field`, `condition_op`, `condition_value` |
| EXPRESSION | Aviator 表达式过滤 | `expression` |

SIMPLE 模式支持的运算符：EQ / NEQ / GT / GTE / LT / LTE / CONTAINS / IS_NULL / IS_NOT_NULL / IN

EXPRESSION 模式复用已有 Aviator 引擎（与 BranchPlugin 一致），每行数据作为变量上下文传入求值。

**涉及文件：**
- 修改：`FilterPlugin.java` — 新增 SIMPLE/EXPRESSION 过滤逻辑
- 修改：`ComponentPage.vue` — Filter 默认字段新增 `filterMode`、`expression`

### 二、Cron 定时调度修复

**现状：** `TaskScheduleExecutor` 使用 `@Scheduled(fixedRate = 30000)` 轮询，但只检查 `nextFireTime` 是否到达。`cronExpression` 字段存在但从未被解析，`nextFireTime` 只在手动触发时设置。

**方案：**
- 创建/更新 `ScheduleJob` 时，使用 Spring 的 `CronExpression.parse(cronExpr).next(LocalDateTime.now())` 计算并设置 `nextFireTime`
- `checkAndFire()` 触发任务后，自动计算下一次 `nextFireTime` 更新回数据库
- 手动触发（`POST /schedule/{id}/trigger`）后也计算下一次 `nextFireTime`
- 保持 30 秒轮询框架不变

**涉及文件：**
- 修改：`ScheduleJobService.java` — 创建/更新时计算 nextFireTime
- 修改：`TaskScheduleExecutor.java` — fireJob 后计算下一次 nextFireTime
- 修改：`ScheduleJobController.java` — trigger 后计算 nextFireTime

### 三、执行重试机制

**现状：** `retryCount` 字段存在于 `external_api_version`、`script_version`、`node_execution_log`，但执行引擎完全忽略，插件失败直接抛异常终止 DAG。

**方案：**
- 在 `ExecEngine` 节点执行循环中添加重试逻辑
- 从插件配置中读取 `retryCount`，执行失败后立即重试，最多 N 次
- 全部失败后记录 `NodeExecutionLog.retryCount` 为实际重试次数，再抛出异常
- 适用插件：`DbPlugin`、`ApiPlugin`、`ScriptPlugin`、`GrpcScriptPlugin`
- 不重试：`BranchPlugin`、`FilterPlugin`、`StartEndPlugin`、`CommonTaskPlugin`

**涉及文件：**
- 修改：`ExecEngine.java` — 节点执行循环加重试

### 四、线程池替换 new Thread()

**现状：** `ExecutorTaskServiceImpl.execute()` 使用 `new Thread(() -> {...}).start()` 异步执行。

**方案：**
- 复用 `ExecutionThreadPoolConfig` 中已有的线程池配置
- 将 `new Thread()` 替换为注入 `ThreadPoolTaskExecutor` 并提交 Runnable
- 或直接使用 `@Async("taskExecutor")` 注解

**涉及文件：**
- 修改：`ExecutorTaskServiceImpl.java` — 替换线程创建为线程池提交
- 确认：`ExecutionThreadPoolConfig.java` — 确保线程池配置充分

### 五、component_io_param_history 补全

**现状：** SQL 表 `component_io_param_history` 已定义，但 Java 代码零引用。已有的 `component_field_history` 可作为参照模式。

**方案：**
- 新增实体 `ComponentIoParamHistory`、Mapper `ComponentIoParamHistoryMapper`
- 在 `ComponentServiceImpl` 中，新增 `saveComponentIoParams()` 方法处理 IO 参数变更时同步写入历史快照

**涉及文件：**
- 新增：`ComponentIoParamHistory.java`
- 新增：`ComponentIoParamHistoryMapper.java`
- 修改：`ComponentServiceImpl.java` — IO 参数保存时写入历史
- 修改：`ComponentController.java` — 添加 IO 参数保存端点（如缺失）

### 六、ComponentFeignClient 补全

**现状：** `ComponentFeignController`（服务端）已实现，但 executor-feign 模块缺少对应的 `@FeignClient` 接口。

**方案：** 参照已有模式（DatasourceFeignClient）：
- 新增 `ComponentFeignClient` 接口，声明 contextId 避免与其他 FeignClient 冲突
- 提供 `resolveComponentMeta(componentId, environment)` 方法
- Executor 端通过 Feign 调用替代直接注入配置模块 Mapper

**涉及文件：**
- 新增：`ComponentFeignClient.java`
- 修改：`ExecutorApplication.java` — 确认 Feign 扫描路径包含新客户端

### 七、全面集成测试

**方案：** 使用 `@SpringBootTest` + H2 内存数据库补充集成测试：

| 测试类 | 覆盖范围 |
|--------|----------|
| FilterPluginTest | SIMPLE/EXPRESSION 行过滤 + COLUMN 列投影 |
| BranchPluginTest | Aviator 表达式条件分支 |
| DbPluginTest | SQL 执行 + 重试 |
| ApiPluginTest | HTTP 调用 + 重试 |
| ScriptPluginTest | 本地脚本执行 + 重试 |
| ExecEngineTest | 完整 DAG 执行 + 重试流程 |
| TaskScheduleExecutorTest | Cron 解析 + nextFireTime 计算 |
| ExecutorTaskControllerTest | Controller CRUD |
| ScheduleJobControllerTest | 定时任务 CRUD + Cron |
| ExecutionLogServiceTest | 日志读写 + 清理 |

### 八、Nacos 配置中心启用

**方案：** 将 `spring.cloud.nacos.config.enabled` 从 `false` 改为 `true`，并将各服务的 `application.yml` 中可外部化的配置抽取到 Nacos 配置中心。

**涉及文件：**
- 修改：Configuration 和 Executor 的 `application.yml` — 启用 config

---

## 不涉及项

- 安全认证系统（用户明确表示暂不需要）
- `executor-common` 空模块（保持预留）
