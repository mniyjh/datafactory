# 数据工厂架构重构设计

## 概述

对 datafactory 项目进行架构重构，解决当前代码中的巨石 Service、模块耦合、安全缺陷等问题，同时为第5-7周的新功能提供清晰的扩展框架。

### 当前问题

1. `ExecutorTaskServiceImpl` 1745 行单体类，集执行引擎、组件逻辑、数据源管理、日志于一身
2. Executor 服务通过 jdbcTemplate 直查 Configuration 服务的数据库表（微服务共享数据库反模式）
3. 组件执行使用硬编码 switch，新增组件类型需改动核心类
4. 数据库密码明文存储
5. 异步执行使用 `new Thread()` 无线程池管控
6. DSL 校验和运行的解析逻辑重复
7. `datafactory-backend-executor-feign` 模块已建但为空

### 重构目标

- 执行引擎插件化：新增组件类型只需加一个 Plugin 实现类
- 服务间通信规范化：Feign 接口替代跨库 SQL
- 安全问题修复：密码加密、线程池管控
- 架构可扩展：为分支组件、过滤组件、通用任务组件、定时任务、监控统计预留清晰接入点

---

## 一、整体分层

```
Gateway (8080) — 路由、跨域
  ├── Configuration 服务 (8081) — 配置管理域
  │   ├── datasource/db/     — 数据库数据源 CRUD + 版本管理
  │   ├── datasource/api/    — 外部 API 数据源 CRUD + 版本管理
  │   ├── component/         — 组件定义、字段、IO参数
  │   ├── script/            — 脚本管理 + 版本管理
  │   ├── openapi/           — 开放接口管理
  │   └── feign/             — 对内 Feign 查询接口 (新增)
  │
  ├── Executor 服务 (8082) — 执行域
  │   ├── engine/core/       — DslParser, DslValidator, TopoSort, ParamResolver
  │   ├── engine/plugin/     — ComponentPlugin 接口 + 各组件插件实现
  │   ├── engine/pool/       — 异步线程池
  │   ├── task/              — 任务 CRUD + 执行入口
  │   ├── dsl/               — DSL 版本管理
  │   ├── log/               — 执行日志 + 节点日志
  │   ├── snapshot/          — 组件快照同步
  │   ├── schedule/          — 定时任务 (新增)
  │   └── statistics/        — 监控统计 (新增)
  │
  ├── Executor-Feign — Feign 接口定义
  │   ├── DatasourceFeignClient
  │   ├── ScriptFeignClient
  │   ├── ExternalApiFeignClient
  │   └── ComponentFeignClient
  │
  └── Common — 共享 DTO/Result/异常/工具
```

**核心原则：** Configuration 是数据主人，通过 Feign 对外暴露；Executor 不直接操作 Configuration 的表。

---

## 二、执行引擎插件化

### 插件接口

```java
public interface ComponentPlugin {
    String supportedType();          // "DB" | "API" | "SCRIPT" | "BRANCH" | "FILTER" | "PYTHON"
    Map<String, Object> execute(PluginContext context);
    default boolean isEnabled() { return true; }
}

public class PluginContext {
    JsonNode node;
    String environment;
    Map<String, Object> resolvedInputs;
    Map<String, Map<String, Object>> upstreamOutputs;
}
```

### 插件注册（Spring 自动发现）

```java
@Component
public class PluginRegistry {
    private final Map<String, ComponentPlugin> plugins = new HashMap<>();

    public PluginRegistry(List<ComponentPlugin> pluginList) {
        pluginList.forEach(p -> plugins.put(p.supportedType(), p));
    }

    public ComponentPlugin get(String type) {
        return Optional.ofNullable(plugins.get(type.toUpperCase()))
                .orElseThrow(() -> new BusinessException("不支持的组件类型: " + type));
    }
}
```

### 插件清单

| 插件 | 类型标识 | 来源 |
|------|---------|------|
| StartEndPlugin | START/END | 从 ExecutorTaskServiceImpl 提取 |
| DbPlugin | DB | 从 ExecutorTaskServiceImpl 提取 |
| ApiPlugin | API | 从 ExecutorTaskServiceImpl 提取 |
| ScriptPlugin | SCRIPT | 从 ExecutorTaskServiceImpl 提取 |
| BranchPlugin | BRANCH | 新增 (条件分支) |
| FilterPlugin | FILTER | 新增 (数据过滤) |
| CommonTaskPlugin | COMMON_TASK | 新增 (子任务递归) |

### 改造后执行流程

```
ExecEngine.execute(taskId, env, params)
  ├── DslParser       → JSON → DslModel
  ├── DslValidator    → 校验链 (结构/DAG/资源关联/参数类型)
  ├── TopoSort        → 按画布坐标拓扑排序
  ├── for each node:
  │     ├── ParamResolver  → CONST / UPSTREAM_OUTPUT / EXPRESSION
  │     ├── PluginRegistry → 按组件类型获取插件
  │     ├── plugin.execute(context)
  │     └── LogRecorder    → 记录节点日志
  └── return finalOutput
```

`ExecutorTaskServiceImpl` 从 1745 行缩减为约 200 行的编排器。

---

## 三、DSL 引擎独立

```
engine/core/
├── DslParser.java           // JSON → DslModel
├── DslValidator.java        // 校验链
│   ├── StructureRule        // 基础结构校验
│   ├── DagRule              // DAG 有效性 (起点/终点/孤立点)
│   ├── ResourceRefRule      // 关联资源存在性
│   └── ParamTypeRule        // 上下游参数类型匹配
├── TopoSort.java            // 拓扑排序
├── ParamResolver.java       // 参数解析 (CONST/UPSTREAM/EXPRESSION)
└── model/
    ├── DslModel.java
    ├── NodeDef.java
    └── EdgeDef.java
```

校验与执行共用 `DslModel`，消除当前两次解析的重复。

---

## 四、Feign 服务间通信

### Feign 接口 (定义在 executor-feign 模块)

```java
@FeignClient(name = "datafactory-backend-configuration", path = "/feign/datasource")
public interface DatasourceFeignClient {
    @GetMapping("/db-version/resolve")
    Result<DbVersionResolveVO> resolveDbVersion(@RequestParam Long dbId, @RequestParam String environment);
}

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/script")
public interface ScriptFeignClient {
    @GetMapping("/version/for-execution")
    Result<ScriptExecutionVO> resolveScriptVersion(@RequestParam Long scriptId, @RequestParam String environment);
}

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/external-api")
public interface ExternalApiFeignClient {
    @GetMapping("/version/resolve")
    Result<ApiVersionResolveVO> resolveApiVersion(@RequestParam Long apiId, @RequestParam String environment);
}

@FeignClient(name = "datafactory-backend-configuration", path = "/feign/component")
public interface ComponentFeignClient {
    @GetMapping("/{id}/validate")
    Result<ComponentMetaVO> validateAndGet(@PathVariable Long id);
}
```

### Configuration 服务新增 FeignController

提供 `/feign/**` 路径下的只读查询接口，复用已有 Service 层。

### 改造范围

Executor 中原有的 4 处 `jdbcTemplate.queryForList()` 跨库直查（datasource_db_version, script_version, external_api_version, component）替换为 Feign 调用。

---

## 五、密码安全

### 方案：AES 双向加密

- 写入：明文 → AES-256-GCM 加密 → 密文存入 `datasource_db_version.password`
- 读取：密文 → AES-256-GCM 解密 → 明文通过 Feign 传输给 Executor
- 密钥通过环境变量 `DATASOURCE_ENCRYPT_KEY` 注入
- Configuration 服务在 FeignController 返回数据时自动解密

### 涉及点

- `DatasourceDbVersionServiceImpl`：保存时加密
- `DatasourceDbVersionFeignController`：返回时解密
- 历史数据需一次性迁移：写脚本遍历加密现有明文密码

---

## 六、异步线程池

替换 `new Thread().start()`：

```java
@Configuration
public class ExecutionThreadPoolConfig {
    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.setThreadNamePrefix("task-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
```

`ExecEngine` 使用 `@Async("taskExecutor")` 替代 `new Thread()`。

---

## 七、新组件设计

### BranchPlugin（条件分支）

根据表达式结果决定走哪条路径：

```json
{
  "type": "BRANCH",
  "fieldValues": {
    "expression": "${rowCount} > 0",
    "trueBranch": "node_3",
    "falseBranch": "node_4"
  }
}
```

使用 Aviator 或 SpEL 表达式引擎求值，返回 `{conditionResult: true/false}`。执行引擎据此跳过不走的路径分支。

### FilterPlugin（数据过滤）

对上游数据进行行/列过滤：

```json
{
  "type": "FILTER",
  "fieldValues": {
    "filterType": "ROW",
    "rowFilter": "id != null && status == 1",
    "sourceNodeId": "db_node_1"
  }
}
```

### CommonTaskPlugin（子任务调用）

将当前上下文传递给子任务并递归调用执行引擎：

```json
{
  "type": "COMMON_TASK",
  "fieldValues": {
    "subTaskId": 12,
    "passContextParams": true
  }
}
```

---

## 八、监控统计

### API 设计

| API | 说明 |
|-----|------|
| `GET /statistics/overview` | 今日执行总数/成功率/失败数/平均耗时 |
| `GET /statistics/trend?days=7` | 近N天每天执行量趋势 |
| `GET /statistics/task-rank?type=failure` | 失败次数最多的 Top10 任务 |
| `GET /execution/{executionId}/progress` | 实时执行进度（当前节点、各节点状态、耗时） |

### 数据来源

基于已有的 `execution_log` 和 `node_execution_log` 表，纯 SQL 统计查询。

---

## 九、定时任务

### 新增表

```sql
CREATE TABLE schedule_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL UNIQUE,
  task_id BIGINT NOT NULL,
  task_version_id BIGINT NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  environment VARCHAR(20) DEFAULT 'PROD',
  status TINYINT DEFAULT 1,
  last_execution_id VARCHAR(64),
  last_fire_time DATETIME,
  next_fire_time DATETIME,
  delete_flag TINYINT DEFAULT 0,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 实现要点

- Spring `@Scheduled` + `CronTrigger`，从数据库动态加载
- 定时触发走与手动执行相同的执行引擎路径
- 支持暂停/恢复/手动触发
- 每次触发自动记录 `last_execution_id`

---

## 十、改造阶段

| 阶段 | 内容 | 依赖 |
|------|------|------|
| 1. 基础解耦 | 密码加密、DslEngine 独立、工具抽取、异常规范 | 无 |
| 2. 执行引擎 | 插件接口+注册、各插件实现、ExecEngine、线程池 | 阶段1 |
| 3. Feign 通信 | Feign 接口定义+实现、Executor 替换跨库查询 | 阶段2 |
| 4. 新组件+定时 | BranchPlugin, FilterPlugin, CommonTaskPlugin, schedule_job | 阶段3 |
| 5. 监控统计 | StatisticsController, 进度API, Dashboard | 阶段4 |

### 文件变更清单

| 类型 | 数量 | 说明 |
|------|------|------|
| 新增 | ~35 个 | 插件(8)、引擎(6)、Feign(4)+VO(6)、FeignController(4)、统计(4)、定时(3) |
| 修改 | ~10 个 | ExecutorTaskServiceImpl、TaskDslServiceImpl、application.yml(×3)等 |
| 删除 | 0 | 旧代码逐步废弃 |
| 新增表 | 1 张 | schedule_job |

### 不变的部分

- 数据库整体 Schema 不变
- 前端 API 契约不变
- Nacos + Gateway 配置不变
- MyBatis-Plus + 实体类不变

---

## 十一、风险与注意事项

1. **Feign 调用增加网络开销**：原 jdbcTemplate 直查 → Feign HTTP 调用，响应时间增加约 5-15ms。对于执行频率不高的场景（相比数据库查询本身的耗时），这个开销可忽略。需要加超时和重试配置。
2. **密码加密历史数据迁移**：需写一次性脚本将现有明文密码加密，部署时先跑迁移再启动服务。
3. **插件执行顺序**：BranchPlugin 的执行结果会改变 DAG 实际遍历路径，ExecEngine 需要支持条件跳过的拓扑遍历。
4. **CommonTaskPlugin 递归**：子任务执行会递归进入 ExecEngine，需要防循环调用（子任务中再引回父任务），通过任务ID集合传递检测。
