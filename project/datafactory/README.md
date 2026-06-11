# DataFactory — 数据工厂平台

低代码数据平台，提供可视化 DAG 编排、多类型脚本执行、定时调度、数据血缘追踪。

---

## 快速开始

### 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | Java 运行时 |
| Maven | 3.8+ | 项目构建 |
| MySQL | 5.7+ | 元数据库 |
| Nacos | 2.x | 服务注册发现 |
| Node.js | 18+ | 前端开发 |

### 一分钟启动

```bash
# 1. 初始化数据库
mysql -u root -p < datafactory.sql

# 2. 启动基础设施（需要 Docker）
docker-compose up -d mysql nacos

# 3. 启动后端三服务（开三个终端）
mvn spring-boot:run -pl datafactory-backend-gateway
mvn spring-boot:run -pl datafactory-backend-configuration
mvn spring-boot:run -pl datafactory-backend-executor/datafactory-backend-executor-server

# 4. 启动前端
cd frontend && npm install && npm run dev
```

访问 `http://127.0.0.1:5173` 打开控制台。

---

## 架构

```
┌──────────┐   ┌──────────────┐   ┌──────────┐
│  Gateway  │──▶ Configuration │──▶  MySQL   │
│  (:8080)  │   │    (:8081)    │   │ (:3306)  │
└────┬─────┘   └──────┬───────┘   └──────────┘
     │                │
     │           ┌────▼───────┐   ┌──────────┐
     └──────────▶│  Executor   │──▶  Nacos   │
                 │   (:8082)   │   │ (:8848)  │
                 └──────┬──────┘   └──────────┘
                        │
              ┌─────────▼──────────┐
              │ gRPC Python Server │
              │     (:50051)       │
              └────────────────────┘
```

### 模块

| 模块 | 端口 | 职责 |
|------|:--:|------|
| **gateway** | 8080 | 统一入口、路由转发 |
| **configuration** | 8081 | 脚本管理、数据源、API、组件、调度 CRUD |
| **executor** | 8082 | 任务执行引擎、定时调度、数据血缘、监控 |
| **frontend** | 5173 | Vue 3 + Ant Design Vue 前端 |

### 画布组件 (9 种)

| 组件 | 编码 | 分类 |
|------|------|------|
| 开始节点 | COMP_START | 流程控制 |
| 结束节点 | COMP_END | 流程控制 |
| 数据库查询 | COMP_DB_QUERY | 数据接入 |
| 接口调用 | COMP_API_CALL | 数据接入 |
| PYTHON执行器 | COMP_PYTHON_EXECUTOR | 数据处理 |
| Shell执行器 | COMP_SHELL_EXECUTOR | 数据处理 |
| 数据过滤 | COMP_FILTER | 数据处理 |
| 条件分支 | COMP_BRANCH | 流程控制 |
| 子任务调用 | COMP_SUB_TASK | 流程控制 |

---

## API 文档

启动后访问 Swagger：
- Configuration API: http://127.0.0.1:8081/swagger-ui.html
- Executor API: http://127.0.0.1:8082/swagger-ui.html

---

## 监控

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/prometheus` | Prometheus 指标 |
| `/metrics/dashboard` | 仪表盘实时数据 |

仪表盘页面（`/dashboard`）30 秒自动刷新，显示 JVM 内存、线程数、今日执行统计。

---

## 数据库

`datafactory.sql` 包含完整的建库 + 建表 + 种子数据。

```bash
# 备份
scripts/backup.bat

# 恢复
scripts/restore.bat scripts/backup\datafactory_20260611_120000.sql
```

---

## 测试

```bash
mvn test -pl datafactory-backend-executor/datafactory-backend-executor-server
```

---

## 技术栈

| 层 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3 + Spring Cloud |
| ORM | MyBatis-Plus 3.5 |
| 服务发现 | Nacos |
| RPC | gRPC (Python 执行器) |
| 数据库 | MySQL 5.7 |
| 规则引擎 | Aviator 5.4 |
| 前端 | Vue 3 + Vite + Ant Design Vue |
| 监控 | Micrometer + Prometheus |
| 文档 | SpringDoc OpenAPI (Swagger) |

---

## 项目结构

```
datafactory/
├── datafactory-backend-common/       公共模块
├── datafactory-backend-configuration/ 配置管理服务
├── datafactory-backend-executor/      执行引擎服务
│   ├── datafactory-backend-executor-feign/  Feign 接口
│   └── datafactory-backend-executor-server/ 执行器实现
├── datafactory-backend-gateway/       API 网关
├── frontend/                          前端
├── grpc-python-server/                Python gRPC 执行服务
├── demo/                              Demo 脚本
├── docs/                              设计文档
├── datafactory.sql                    数据库初始化脚本
├── docker-compose.yml                 基础设施编排
├── scripts/                            一键脚本（备份/恢复/环境配置）
├── docker-compose.yml                 基础设施编排
```
