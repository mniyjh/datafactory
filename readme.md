[datafactory-backend-common](datafactory-backend-common)  公共服务
[datafactory-backend-config](datafactory-backend-configuration)  数据工厂配置服务
[datafactory-backend-execute](datafactory-backend-executor) 数据工厂配置执行端
[datafactory-backend-gateway](datafactory-backend-gateway) 数据工厂网关


# 后端技术框架
- **核心框架**: Spring Cloud Alibaba
- **ORM框架**: MyBatis-Plus
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **安全框架**: Spring Security + JWT
- **API文档**: SpringDoc OpenAPI 3
- **构建工具**: Maven
- **配置中心**: Nacos Config
- **API网关**: Spring Cloud Gateway
- **负载均衡**: Nginx
- **测试工具**: JUnit 5

## 项目结构规范
datafactory-backend-common/ # 公共模块
├── src/main/java/com/cqie/datafactory
│   ├── common/                  # 公共模块
│   │   ├── enums/               # 枚举类
│   │   ├── constants/           # 常量定义
│   │   ├── exception/           # 统一异常处理
│   │   ├── result/              # 统一返回结果
│   │   └── utils/               # 工具类

datafactory-backend-configuration/ # 配置管理服务
├── src/main/java/com/cqie/datafactory/configuration/
│   ├── config/                  # 配置类
│   ├── controller/              # 控制器层
│   ├── entity/                  # 实体类
│   ├── mapper/                  # 数据访问层
│   ├── service/                 # 业务逻辑层
│   │   ├── impl/               # 服务实现
│   │   └── Service.java        # 服务接口
│   └── ConfigurationApplication.java         # 启动类
├── src/main/resources/
│   ├── mapper/                  # MyBatis XML映射文件
│   ├── logback.xml              # 日志打印配置
│   └── application.yml          # 应用配置
└── src/test/                    # 测试代码

datafactory-backend-executor/datafactory-backend-executor-common/ # 执行服务
├── src/main/java/com/cqie/datafactory/executor/
│   ├── common/                  # 配置类
│   └── vo          # 应用配置

datafactory-backend-executor/datafactory-backend-executor-feign/ # 执行远程调用接口
├── src/main/java/com/cqie/datafactory/executor/
│   ├── feign/                  # 配置类
│   │   └── XxxFeignService.java    # 服务接口

datafactory-backend-executor/datafactory-backend-server/ # 执行服务
├── src/main/java/com/cqie/datafactory/executor/
│   ├── config/                  # 配置类
│   ├── controller/              # 控制器层
│   ├── entity/                  # 实体类
│   ├── mapper/                  # 数据访问层
│   ├── service/                 # 业务逻辑层
│   │   ├── dto/                 # 数据传输对象
│   │   ├── impl/               # 服务实现
│   │   └── XxxService.java        # 服务接口
│   └── ExecutorApplication.java         # 启动类
├── src/main/resources/
│   ├── mapper/                  # MyBatis XML映射文件
│   ├── logback.xml              # 日志打印配置
│   └── application.yml          # 应用配置
└── src/test/                    # 测试代码

datafactory-backend-gateway/     # 网关服务
├── src/main/java/com/cqie/datafactory/gateway/
│   ├── config/                  # 配置类
│   └── GatewayApplication.java         # 启动类
├── src/main/resources/
│   ├── logback.xml              # 日志打印配置
│   └── application.yml          # 应用配置
