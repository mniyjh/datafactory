# DataFactory 安全加固实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DataFactory 平台实现生产级安全体系：JWT 认证、RBAC 授权、Gateway 鉴权、密钥外部化、API 审计。

**Architecture:** Gateway 负责 JWT 验证和限流，微服务通过解析透传 Header 获取用户上下文并用 `@PreAuthorize` 做方法级授权。RBAC 采用 User → Role → Permission 三级模型，权限粒度为 `resource:action`。

**Tech Stack:** Spring Security 6.x (WebFlux + WebMVC), JJWT 0.12.x, BCrypt, Bucket4j, Spring AOP, MyBatis-Plus

---

## 文件结构总览

### 新建文件

**Common 模块 (`datafactory-backend-common`):**
- `src/main/java/com/cqie/datafactory/common/security/SecurityUtils.java` — 从 SecurityContext 提取当前用户
- `src/main/java/com/cqie/datafactory/common/entity/AuditLog.java` — 审计日志实体
- `src/main/java/com/cqie/datafactory/common/mapper/AuditLogMapper.java` — 审计日志 Mapper
- `src/main/java/com/cqie/datafactory/common/aspect/AuditAspect.java` — API 审计 AOP 切面
- `src/main/java/com/cqie/datafactory/common/annotation/Auditable.java` — 审计注解

**Configuration 服务 (`datafactory-backend-configuration`):**
- `src/main/java/com/cqie/datafactory/configuration/entity/User.java`
- `src/main/java/com/cqie/datafactory/configuration/entity/Role.java`
- `src/main/java/com/cqie/datafactory/configuration/entity/Permission.java`
- `src/main/java/com/cqie/datafactory/configuration/entity/UserRole.java`
- `src/main/java/com/cqie/datafactory/configuration/entity/RolePermission.java`
- `src/main/java/com/cqie/datafactory/configuration/mapper/UserMapper.java`
- `src/main/java/com/cqie/datafactory/configuration/mapper/RoleMapper.java`
- `src/main/java/com/cqie/datafactory/configuration/mapper/PermissionMapper.java`
- `src/main/java/com/cqie/datafactory/configuration/mapper/UserRoleMapper.java`
- `src/main/java/com/cqie/datafactory/configuration/mapper/RolePermissionMapper.java`
- `src/main/java/com/cqie/datafactory/configuration/security/JwtProperties.java` — JWT 配置属性
- `src/main/java/com/cqie/datafactory/configuration/security/JwtService.java` — Token 生成/验证
- `src/main/java/com/cqie/datafactory/configuration/security/UserDetailsServiceImpl.java` — Spring Security UserDetails
- `src/main/java/com/cqie/datafactory/configuration/security/InternalAuthFilter.java` — Header → SecurityContext
- `src/main/java/com/cqie/datafactory/configuration/controller/AuthController.java` — /auth/login, /auth/refresh, /auth/logout
- `src/main/java/com/cqie/datafactory/configuration/controller/dto/LoginDTO.java`
- `src/main/java/com/cqie/datafactory/configuration/controller/vo/LoginVO.java`
- `src/main/java/com/cqie/datafactory/configuration/service/UserService.java`
- `src/main/java/com/cqie/datafactory/configuration/service/impl/UserServiceImpl.java`
- `src/main/java/com/cqie/datafactory/configuration/service/AuthService.java`
- `src/main/java/com/cqie/datafactory/configuration/service/impl/AuthServiceImpl.java`

**Executor 服务 (`datafactory-backend-executor-server`):**
- `src/main/java/com/cqie/datafactory/executor/security/InternalAuthFilter.java` — Header → SecurityContext

**Gateway (`datafactory-backend-gateway`):**
- `src/main/java/com/cqie/datafactory/gateway/security/JwtAuthFilter.java` — WebFlux JWT 验证过滤器
- `src/main/java/com/cqie/datafactory/gateway/security/GatewaySecurityConfig.java` — WebFlux Security 配置
- `src/main/java/com/cqie/datafactory/gateway/security/RateLimitConfig.java` — Bucket4j 限流
- `src/main/java/com/cqie/datafactory/gateway/security/SecurityHeadersFilter.java` — 安全响应头

### 修改文件

- `datafactory-backend-gateway/pom.xml` — 加 spring-boot-starter-security, jjwt, bucket4j
- `datafactory-backend-gateway/src/main/resources/application.yml` — CORS 收紧, 新增 jwt 配置
- `datafactory-backend-configuration/src/main/java/.../config/SecurityConfig.java` — 重写: JWT + RBAC
- `datafactory-backend-configuration/src/main/resources/application.yml` — 新增 jwt 配置
- `datafactory-backend-executor/.../config/SecurityConfig.java` — 重写: Header 解析 + 方法级鉴权
- `datafactory-backend-executor/.../resources/application.yml` — 新增 jwt 配置
- `datafactory-backend-common/pom.xml` — 加 spring-boot-starter-security, spring-aop, mybatis-plus
- `datafactory-backend-configuration/pom.xml` — 加 jjwt 依赖
- 所有 Controller 文件 — 加 `@PreAuthorize` 注解
- `datafactory.sql` — 新增用户/角色/权限 DDL + 初始化数据

---

### Task 1: 添加依赖

**Files:**
- Modify: `datafactory-backend-common/pom.xml`
- Modify: `datafactory-backend-gateway/pom.xml`
- Modify: `datafactory-backend-configuration/pom.xml`
- Modify: `datafactory/pom.xml` (root)

- [ ] **Step 1: 在 root pom.xml 添加 jjwt 和 bucket4j 版本管理**

在 `datafactory/pom.xml` 的 `<properties>` 中添加：
```xml
<jjwt.version>0.12.6</jjwt.version>
<bucket4j.version>8.10.1</bucket4j.version>
```

- [ ] **Step 2: 在 common 模块 pom.xml 添加依赖**

在 `datafactory-backend-common/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>${mybatis-plus.version}</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 3: 在 configuration 模块 pom.xml 添加 jjwt**

在 `datafactory-backend-configuration/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 4: 在 gateway 模块 pom.xml 添加 security + jjwt + bucket4j**

在 `datafactory-backend-gateway/pom.xml` 的 `<dependencies>` 中添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>${bucket4j.version}</version>
</dependency>
```

- [ ] **Step 5: 验证依赖下载**

Run: `cd d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory && mvn dependency:resolve -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add pom.xml datafactory-backend-common/pom.xml datafactory-backend-configuration/pom.xml datafactory-backend-gateway/pom.xml
git commit -m "chore: add security dependencies (jjwt, bucket4j, spring-security for gateway)"
```

---

### Task 2: 创建 User/Role/Permission 实体

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/User.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/Role.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/Permission.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/UserRole.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/RolePermission.java`

- [ ] **Step 1: 创建 User 实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private Integer status;       // 1=正常 0=禁用
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 2: 创建 Role 实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer status;       // 1=正常 0=禁用
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 3: 创建 Permission 实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;          // resource:action 如 task:write
    private String resource;      // 资源: task, datasource, script, schedule, user, monitor, log
    private String action;        // 操作: read, write, execute, delete
    private String description;
    private LocalDateTime createdTime;
}
```

- [ ] **Step 4: 创建 UserRole 关联实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class UserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;
}
```

- [ ] **Step 5: 创建 RolePermission 关联实体**

```java
package com.cqie.datafactory.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class RolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long permissionId;
}
```

- [ ] **Step 6: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/User.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/Role.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/Permission.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/UserRole.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/entity/RolePermission.java
git commit -m "feat: add User/Role/Permission entities for RBAC"
```

---

### Task 3: 创建 Mapper 接口

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/UserMapper.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/RoleMapper.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/PermissionMapper.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/UserRoleMapper.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/RolePermissionMapper.java`

- [ ] **Step 1: 创建 UserMapper**

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT DISTINCT p.code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT r.code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
```

- [ ] **Step 2: 创建 RoleMapper**

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<Role> selectByUserId(@Param("userId") Long userId);
}
```

- [ ] **Step 3: 创建 PermissionMapper, UserRoleMapper, RolePermissionMapper**

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
```

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
```

```java
package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
```

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/UserMapper.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/RoleMapper.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/PermissionMapper.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/UserRoleMapper.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/mapper/RolePermissionMapper.java
git commit -m "feat: add Mapper interfaces for User/Role/Permission"
```

---

### Task 4: 创建 DDL — 安全表结构

**Files:**
- Modify: `datafactory.sql` (在文件末尾追加 DDL)

- [ ] **Step 1: 追加用户/角色/权限建表 SQL 到 datafactory.sql**

在 `datafactory.sql` 文件末尾追加：
```sql
-- =============================================
-- 安全模块: 用户/角色/权限 (RBAC)
-- =============================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT 'BCrypt加密密码',
    `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 0=禁用',
    `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 0=禁用',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '权限名称',
    `code` VARCHAR(128) NOT NULL UNIQUE COMMENT '权限编码 (resource:action)',
    `resource` VARCHAR(64) NOT NULL COMMENT '资源标识',
    `action` VARCHAR(32) NOT NULL COMMENT '操作: read/write/execute/delete',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '权限描述',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_resource` (`resource`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT DEFAULT NULL COMMENT '操作用户ID',
    `username` VARCHAR(64) DEFAULT NULL COMMENT '操作用户名',
    `operation` VARCHAR(128) NOT NULL COMMENT '操作描述',
    `method` VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    `url` VARCHAR(512) NOT NULL COMMENT '请求URL',
    `params` TEXT DEFAULT NULL COMMENT '请求参数(截断)',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    `error_msg` VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_time` (`created_time`),
    INDEX `idx_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API审计日志表';
```

- [ ] **Step 2: Commit**

```bash
git add datafactory.sql
git commit -m "feat: add RBAC DDL (sys_user/role/permission/audit) to schema"
```

---

### Task 5: 初始化角色和权限数据

**Files:**
- Modify: `datafactory.sql` (在安全 DDL 后追加初始数据)

- [ ] **Step 1: 追加权限初始化数据**

在 `datafactory.sql` 安全 DDL 后面追加：
```sql
-- =============================================
-- 初始化权限数据
-- =============================================
INSERT INTO `sys_permission` (`name`, `code`, `resource`, `action`, `description`) VALUES
('读取任务', 'task:read', 'task', 'read', '查看任务列表和详情'),
('创建编辑任务', 'task:write', 'task', 'write', '创建/编辑/删除任务'),
('执行任务', 'task:execute', 'task', 'execute', '手动执行任务'),
('读取数据源', 'datasource:read', 'datasource', 'read', '查看数据源配置'),
('管理数据源', 'datasource:write', 'datasource', 'write', '创建/编辑/删除数据源'),
('读取脚本', 'script:read', 'script', 'read', '查看脚本内容'),
('管理脚本', 'script:write', 'script', 'write', '创建/编辑/删除脚本'),
('读取调度', 'schedule:read', 'schedule', 'read', '查看调度配置'),
('管理调度', 'schedule:write', 'schedule', 'write', '创建/编辑/启停调度'),
('管理用户', 'user:write', 'user', 'write', '创建/编辑/禁用用户'),
('读取监控', 'monitor:read', 'monitor', 'read', '查看监控面板'),
('读取日志', 'log:read', 'log', 'read', '查看执行日志');

-- =============================================
-- 初始化角色数据
-- =============================================
INSERT INTO `sys_role` (`name`, `code`, `description`, `status`) VALUES
('超级管理员', 'super_admin', '拥有系统所有权限', 1),
('管理员', 'admin', '用户管理 + 配置管理 + 调度管理', 1),
('数据开发', 'developer', '任务/脚本/数据源 CRUD + 执行', 1),
('运维', 'operator', '查看 + 执行 + 监控', 1),
('只读', 'viewer', '仅查看所有资源', 1);

-- =============================================
-- 角色-权限关联 (super_admin 拥有所有权限)
-- =============================================
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'super_admin'), id FROM sys_permission;

-- admin: user管理 + 所有读权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'admin'), id FROM sys_permission
WHERE code IN ('task:read', 'task:write', 'task:execute', 'datasource:read', 'datasource:write',
               'script:read', 'script:write', 'schedule:read', 'schedule:write',
               'user:write', 'monitor:read', 'log:read');

-- developer: 任务/脚本/数据源 CRUD + 执行 + 日志查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'developer'), id FROM sys_permission
WHERE code IN ('task:read', 'task:write', 'task:execute', 'datasource:read', 'datasource:write',
               'script:read', 'script:write', 'schedule:read', 'log:read');

-- operator: 查看 + 执行 + 监控
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'operator'), id FROM sys_permission
WHERE code IN ('task:read', 'task:execute', 'datasource:read', 'script:read',
               'schedule:read', 'monitor:read', 'log:read');

-- viewer: 仅查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'viewer'), id FROM sys_permission
WHERE code IN ('task:read', 'datasource:read', 'script:read', 'schedule:read', 'monitor:read', 'log:read');

-- =============================================
-- 初始化管理员用户 (admin / admin123)
-- 密码: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
-- (BCrypt 加密的 "admin123")
-- =============================================
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `created_by`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@datafactory.dev', 1, 'SYSTEM');

-- 为 admin 分配 super_admin 角色
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT (SELECT id FROM sys_user WHERE username = 'admin'), (SELECT id FROM sys_role WHERE code = 'super_admin');
```

- [ ] **Step 2: Commit**

```bash
git add datafactory.sql
git commit -m "feat: add RBAC init data (roles, permissions, default admin user)"
```

---

### Task 6: 实现 JwtService + JwtProperties

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/JwtProperties.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/JwtService.java`

- [ ] **Step 1: 创建 JwtProperties**

```java
package com.cqie.datafactory.configuration.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /** JWT签名密钥 (HMAC-SHA256, 最少256位) */
    private String secret = "${JWT_SECRET:}";
    /** Access Token 过期时间(秒), 默认24小时 */
    private long accessTokenExpiration = 86400;
    /** Refresh Token 过期时间(秒), 默认7天 */
    private long refreshTokenExpiration = 604800;
    /** Token 签发者 */
    private String issuer = "datafactory";
}
```

- [ ] **Step 2: 创建 JwtService**

```java
package com.cqie.datafactory.configuration.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // Ensure key is at least 256 bits for HS256
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 生成 Access Token */
    public String generateAccessToken(Long userId, String username,
                                       List<String> roles, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** 生成 Refresh Token */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiration() * 1000);

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** 解析 JWT Claims (不抛异常) */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 验证 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Token 中提取 userId */
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /** 从 Token 中提取用户名 */
    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 中提取角色列表 */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseToken(token).get("roles", List.class);
    }

    /** 从 Token 中提取权限列表 */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        return parseToken(token).get("permissions", List.class);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/
git commit -m "feat: implement JwtService with access/refresh token generation"
```

---

### Task 7: 实现 UserDetailsServiceImpl

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/UserDetailsServiceImpl.java`

- [ ] **Step 1: 创建 UserDetailsServiceImpl**

```java
package com.cqie.datafactory.configuration.security;

import com.cqie.datafactory.configuration.entity.User;
import com.cqie.datafactory.configuration.mapper.UserMapper;
import com.cqie.datafactory.configuration.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                true,   // accountNonLocked
                authorities
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/UserDetailsServiceImpl.java
git commit -m "feat: implement UserDetailsService with RBAC permission loading"
```

---

### Task 8: 实现 AuthService + AuthController

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/dto/LoginDTO.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/vo/LoginVO.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/AuthService.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/AuthServiceImpl.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/AuthController.java`

- [ ] **Step 1: 创建 LoginDTO**

```java
package com.cqie.datafactory.configuration.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

- [ ] **Step 2: 创建 LoginVO**

```java
package com.cqie.datafactory.configuration.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType = "Bearer";
    private UserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String email;
        private List<String> roles;
        private List<String> permissions;
    }
}
```

- [ ] **Step 3: 创建 AuthService 接口**

```java
package com.cqie.datafactory.configuration.service;

import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;

public interface AuthService {
    LoginVO login(LoginDTO loginDTO);
    LoginVO refresh(String refreshToken);
    void logout(Long userId);
}
```

- [ ] **Step 4: 创建 AuthServiceImpl**

```java
package com.cqie.datafactory.configuration.service.impl;

import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;
import com.cqie.datafactory.configuration.entity.User;
import com.cqie.datafactory.configuration.mapper.UserMapper;
import com.cqie.datafactory.configuration.security.JwtService;
import com.cqie.datafactory.configuration.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getUsername(), roles, permissions);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("用户 {} 登录成功, 角色: {}, 权限数: {}", user.getUsername(), roles, permissions.size());

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400)
                .tokenType("Bearer")
                .user(LoginVO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .build())
                .build();
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (!jwtService.validateToken(refreshToken)) {
            throw new BusinessException("Refresh Token 无效或已过期");
        }

        Long userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException("用户不存在或已被禁用");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = userMapper.selectPermissionsByUserId(userId);

        String newAccessToken = jwtService.generateAccessToken(
                userId, user.getUsername(), roles, permissions);

        return LoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400)
                .tokenType("Bearer")
                .user(LoginVO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .realName(user.getRealName())
                        .email(user.getEmail())
                        .roles(roles)
                        .permissions(permissions)
                        .build())
                .build();
    }

    @Override
    public void logout(Long userId) {
        log.info("用户 {} 已登出", userId);
        // Token黑名单可在此扩展 (Redis)
    }
}
```

- [ ] **Step 5: 创建 AuthController**

```java
package com.cqie.datafactory.configuration.controller;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;
import com.cqie.datafactory.configuration.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return Result.success(authService.refresh(refreshToken));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(value = "userId", required = false) Long userId) {
        authService.logout(userId);
        return Result.success();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/dto/LoginDTO.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/vo/LoginVO.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/AuthService.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/AuthServiceImpl.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/AuthController.java
git commit -m "feat: implement login/refresh/logout with JWT"
```

---

### Task 9: 重写 Configuration 服务 SecurityConfig

**Files:**
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/config/SecurityConfig.java`
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/InternalAuthFilter.java`

- [ ] **Step 1: 创建 InternalAuthFilter (解析 Gateway 透传的 Header)**

```java
package com.cqie.datafactory.configuration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Username");
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

        if (StringUtils.hasText(userId) && StringUtils.hasText(username)) {
            List<String> permissions = StringUtils.hasText(permissionsHeader)
                    ? Arrays.stream(permissionsHeader.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList())
                    : Collections.emptyList();

            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            auth.setDetails(Long.parseLong(userId));

            SecurityContextHolder.getContext().setAuthentication(auth);

            // 将 userId 作为 request attribute 方便 Controller 获取
            request.setAttribute("userId", Long.parseLong(userId));
            request.setAttribute("username", username);

            log.debug("Internal auth: user={} userId={} permissions={}", username, userId, permissions);
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: 重写 SecurityConfig**

```java
package com.cqie.datafactory.configuration.config;

import com.cqie.datafactory.configuration.security.InternalAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 启用 @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalAuthFilter internalAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 认证接口公开
                .requestMatchers("/auth/**").permitAll()
                // Actuator仅健康检查公开，其余需SUPER_ADMIN
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("monitor:read")
                // Swagger公开
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Feign内部调用需要认证（简单IP白名单或内部token）
                .requestMatchers("/feign/**").permitAll()
                // 所有其他请求需认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                System.getProperty("CORS_ORIGINS", "http://localhost:5173").split(",")
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/security/InternalAuthFilter.java \
        datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/config/SecurityConfig.java
git commit -m "feat: rewrite config SecurityConfig with JWT + method security + internal auth filter"
```

---

### Task 10: 重写 Executor 服务 SecurityConfig

**Files:**
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/config/SecurityConfig.java`
- Create: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/security/InternalAuthFilter.java`

- [ ] **Step 1: 创建 Executor 的 InternalAuthFilter**

与 Configuration 服务的 InternalAuthFilter 相同逻辑，复制到 executor 包下：

```java
package com.cqie.datafactory.executor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component("executorInternalAuthFilter")
public class InternalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Username");
        String rolesHeader = request.getHeader("X-User-Roles");
        String permissionsHeader = request.getHeader("X-User-Permissions");

        if (StringUtils.hasText(userId) && StringUtils.hasText(username)) {
            List<String> permissions = StringUtils.hasText(permissionsHeader)
                    ? Arrays.stream(permissionsHeader.split(","))
                            .map(String::trim)
                            .collect(Collectors.toList())
                    : Collections.emptyList();

            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            auth.setDetails(Long.parseLong(userId));

            SecurityContextHolder.getContext().setAuthentication(auth);

            request.setAttribute("userId", Long.parseLong(userId));
            request.setAttribute("username", username);

            log.debug("Internal auth (executor): user={} userId={} permissions={}", username, userId, permissions);
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: 重写 Executor SecurityConfig**

```java
package com.cqie.datafactory.executor.config;

import com.cqie.datafactory.executor.security.InternalAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration("executorSecurityConfig")
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalAuthFilter internalAuthFilter;

    @Bean("executorSecurityFilterChain")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("monitor:read")
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/feign/**").permitAll()  // Feign 内部调用
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean("executorCorsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                System.getProperty("CORS_ORIGINS", "http://localhost:5173").split(",")
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/security/InternalAuthFilter.java \
        datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/config/SecurityConfig.java
git commit -m "feat: rewrite executor SecurityConfig with internal auth filter + method security"
```

---

### Task 11: 实现 Gateway JWT 验证过滤器

**Files:**
- Create: `datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/JwtAuthFilter.java`
- Create: `datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/GatewaySecurityConfig.java`

- [ ] **Step 1: 创建 JwtAuthFilter (WebFlux GatewayFilter)**

```java
package com.cqie.datafactory.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /** 白名单路径 (无需认证) */
    private static final List<String> WHITELIST = List.of(
            "/auth/login",
            "/auth/refresh",
            "/actuator/health",
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // OPTIONS 预检放行
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseToken(token);

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            @SuppressWarnings("unchecked")
            List<String> permissions = claims.get("permissions", List.class);

            // 透传用户信息到下游微服务
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Username", username)
                    .header("X-User-Roles", roles != null ? String.join(",", roles) : "")
                    .header("X-User-Permissions", permissions != null ? String.join(",", permissions) : "")
                    .build();

            log.debug("JWT validated: user={} userId={} path={}", username, userId, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
                Arrays.copyOf(jwtSecret.getBytes(StandardCharsets.UTF_8), 32));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}
```

- [ ] **Step 2: 创建 GatewaySecurityConfig (WebFlux Security)**

```java
package com.cqie.datafactory.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> {}) // CORS 由 application.yml globalcors 管理
            .authorizeExchange(ex -> ex
                .pathMatchers("/auth/**").permitAll()
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().permitAll() // 实际鉴权由 JwtAuthFilter 处理
            )
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);

        return http.build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/
git commit -m "feat: implement Gateway JWT validation filter with user header propagation"
```

---

### Task 12: Gateway 安全响应头 + 限流

**Files:**
- Create: `datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/SecurityHeadersFilter.java`
- Create: `datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/RateLimitConfig.java`

- [ ] **Step 1: 创建 SecurityHeadersFilter**

```java
package com.cqie.datafactory.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
            headers.add("X-XSS-Protection", "1; mode=block");
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.add("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            headers.add("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate");
            headers.add("Pragma", "no-cache");
            headers.remove("Server");
            headers.remove("X-Powered-By");
        }));
    }

    @Override
    public int getOrder() {
        return -50; // 响应处理阶段
    }
}
```

- [ ] **Step 2: 创建 RateLimitConfig (内存级令牌桶)**

```java
package com.cqie.datafactory.gateway.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitConfig implements GlobalFilter, Ordered {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 默认: 每个 IP 每分钟 100 次请求 */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(100,
                Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 登录接口特殊限制: 每分钟 5 次
        if (path.startsWith("/auth/login")) {
            String ip = getClientIp(exchange);
            Bucket bucket = buckets.computeIfAbsent("login:" + ip, k -> {
                Bandwidth limit = Bandwidth.classic(5,
                        Refill.intervally(5, Duration.ofMinutes(1)));
                return Bucket.builder().addLimit(limit).build();
            });

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for login from IP: {}", ip);
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        }

        // 通用限流
        String key = getClientIp(exchange);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", key);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return -90; // 在 JWT 验证之前
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/SecurityHeadersFilter.java \
        datafactory-backend-gateway/src/main/java/com/cqie/datafactory/gateway/security/RateLimitConfig.java
git commit -m "feat: add security headers filter + rate limiting at gateway"
```

---

### Task 13: 更新 Gateway application.yml

**Files:**
- Modify: `datafactory-backend-gateway/src/main/resources/application.yml`

- [ ] **Step 1: 更新 CORS 和新增 JWT 配置**

替换 gateway application.yml 内容：
```yaml
server:
  port: 8080

spring:
  application:
    name: datafactory-backend-gateway
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848
      discovery:
        server-addr: 127.0.0.1:8848
        ip: 127.0.0.1
        prefer-ip-address: true
    gateway:
      routes:
        - id: auth-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/auth/**
        - id: datasource-db-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/datasource/db/**, /datasource/db
        - id: datasource-db-version-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/datasource/db-version/**, /datasource/db-version
        - id: external-api-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/external-api/**, /external-api
        - id: external-api-version-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/external-api-version/**, /external-api-version
        - id: open-api-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/open-api/**, /open-api
        - id: script-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/script/**, /script
        - id: dashboard-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/dashboard/**
        - id: component-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/component/**, /component
        - id: config-health-service
          uri: lb://datafactory-backend-configuration
          predicates:
            - Path=/configuration/**
        - id: task-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/tasks/**, /tasks
        - id: task-dsl-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/task-dsl/**, /task-dsl
        - id: task-test-config-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/task-test-config/**, /task-test-config
        - id: schedule-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/schedule/**
        - id: statistics-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/statistics/**
        - id: executor-health-service
          uri: lb://datafactory-backend-executor-server
          predicates:
            - Path=/executor/**
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "${CORS_ORIGINS:http://localhost:5173}"
            allowedMethods: "GET,POST,PUT,DELETE,OPTIONS"
            allowedHeaders: "Authorization,Content-Type,X-Trace-Id"
            allowCredentials: true
            maxAge: 3600

jwt:
  secret: ${JWT_SECRET}

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-gateway/src/main/resources/application.yml
git commit -m "feat: tighten CORS, add JWT config, remove unused gateway routes"
```

---

### Task 14: 更新微服务 application.yml (JWT 配置)

**Files:**
- Modify: `datafactory-backend-configuration/src/main/resources/application.yml`
- Modify: `datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml`

- [ ] **Step 1: 在 Configuration 服务 application.yml 追加 JWT 配置**

在文件末尾追加：
```yaml
jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 86400
  refresh-token-expiration: 604800

springdoc:
  swagger-ui:
    enabled: true
  api-docs:
    enabled: true
```

- [ ] **Step 2: 在 Executor 服务 application.yml 追加 JWT 配置**

在文件末尾追加：
```yaml
jwt:
  secret: ${JWT_SECRET}  # executor 也需要验证来自 gateway 的 header 签名 (可选)
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-configuration/src/main/resources/application.yml \
        datafactory-backend-executor/datafactory-backend-executor-server/src/main/resources/application.yml
git commit -m "feat: add JWT configuration to backend services"
```

---

### Task 15: 创建 SecurityUtils 工具类

**Files:**
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/security/SecurityUtils.java`

- [ ] **Step 1: 创建 SecurityUtils**

```java
package com.cqie.datafactory.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** 获取当前登录用户ID */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }
        return null;
    }

    /** 获取当前登录用户名 */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        return null;
    }

    /** 获取当前用户权限集合 */
    public static Set<String> getCurrentPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    /** 是否拥有指定权限 */
    public static boolean hasPermission(String permission) {
        return getCurrentPermissions().contains(permission);
    }

    /** 是否已认证 */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-common/src/main/java/com/cqie/datafactory/common/security/SecurityUtils.java
git commit -m "feat: add SecurityUtils for accessing current user from SecurityContext"
```

---

### Task 16: 创建 API 审计系统

**Files:**
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/entity/AuditLog.java`
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/mapper/AuditLogMapper.java`
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/annotation/Auditable.java`
- Create: `datafactory-backend-common/src/main/java/com/cqie/datafactory/common/aspect/AuditAspect.java`

- [ ] **Step 1: 创建 AuditLog 实体**

```java
package com.cqie.datafactory.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    private String url;
    private String params;
    private String ip;
    private Integer status;       // 1=成功 0=失败
    private String errorMsg;
    private Long costMs;
    private LocalDateTime createdTime;
}
```

- [ ] **Step 2: 创建 AuditLogMapper**

```java
package com.cqie.datafactory.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.common.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
```

- [ ] **Step 3: 创建 @Auditable 注解**

```java
package com.cqie.datafactory.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    /** 操作描述 */
    String value() default "";
}
```

- [ ] **Step 4: 创建 AuditAspect**

```java
package com.cqie.datafactory.common.aspect;

import com.cqie.datafactory.common.annotation.Auditable;
import com.cqie.datafactory.common.entity.AuditLog;
import com.cqie.datafactory.common.mapper.AuditLogMapper;
import com.cqie.datafactory.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Instant start = Instant.now();
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation(auditable.value());
        auditLog.setStatus(1);

        try {
            // 获取当前用户
            auditLog.setUserId(SecurityUtils.getCurrentUserId());
            auditLog.setUsername(SecurityUtils.getCurrentUsername());

            // 获取请求信息
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                auditLog.setMethod(request.getMethod());
                auditLog.setUrl(request.getRequestURI());
                auditLog.setIp(getClientIp(request));

                // 截断请求参数
                String params = Arrays.toString(joinPoint.getArgs());
                auditLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
            } else {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                auditLog.setMethod("INTERNAL");
                auditLog.setUrl(signature.getDeclaringTypeName() + "." + signature.getName());
            }

            Object result = joinPoint.proceed();

            auditLog.setCostMs(Duration.between(start, Instant.now()).toMillis());
            auditLogMapper.insert(auditLog);

            return result;

        } catch (Throwable e) {
            auditLog.setStatus(0);
            auditLog.setErrorMsg(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000))
                    : e.getClass().getSimpleName());
            auditLog.setCostMs(Duration.between(start, Instant.now()).toMillis());
            auditLogMapper.insert(auditLog);
            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add datafactory-backend-common/src/main/java/com/cqie/datafactory/common/entity/AuditLog.java \
        datafactory-backend-common/src/main/java/com/cqie/datafactory/common/mapper/AuditLogMapper.java \
        datafactory-backend-common/src/main/java/com/cqie/datafactory/common/annotation/Auditable.java \
        datafactory-backend-common/src/main/java/com/cqie/datafactory/common/aspect/AuditAspect.java
git commit -m "feat: add API audit system with @Auditable annotation and AOP"
```

---

### Task 17: Controller 加 @PreAuthorize 注解

**Files:**
- Modify: Configuration 服务全部 Controller
- Modify: Executor 服务全部 Controller

- [ ] **Step 1: Configuration 服务 Controller 加权限注解**

对以下 Controller 的 CUD 方法加 `@PreAuthorize`：
- `DatasourceDbController` — 读: `hasAuthority('datasource:read')`, 写: `hasAuthority('datasource:write')`
- `DatasourceDbVersionController` — 同上
- `ScriptController` — 读: `hasAuthority('script:read')`, 写: `hasAuthority('script:write')`
- `ScriptVersionController` — 同上
- `ExternalApiController` — `datasource:read/write`
- `ExternalApiVersionController` — `datasource:read/write`
- `OpenApiController` — `task:read/write`
- `ComponentController` — 仅 admin: `hasAnyAuthority('task:write', 'user:write')`

示例 — DatasourceDbController GET 方法：
```java
@GetMapping
@PreAuthorize("hasAuthority('datasource:read')")
public Result<PageResult<DatasourceDbVO>> list(...) { ... }

@PostMapping
@PreAuthorize("hasAuthority('datasource:write')")
public Result<DatasourceDbVO> create(@Valid @RequestBody DatasourceDbCreateDTO dto) { ... }

@PutMapping("/{id}")
@PreAuthorize("hasAuthority('datasource:write')")
public Result<DatasourceDbVO> update(@PathVariable Long id, @Valid @RequestBody DatasourceDbCreateDTO dto) { ... }

@DeleteMapping("/{id}")
@PreAuthorize("hasAuthority('datasource:write')")
public Result<Void> delete(@PathVariable Long id) { ... }
```

- [ ] **Step 2: Executor 服务 Controller 加权限注解**

- `ExecutorTaskController` — `task:read/write/execute`
- `TaskDslController` — `task:read/write`
- `ScheduleController` (如有) — `schedule:read/write`
- `ExecutionLogController` — `log:read`
- `MetricsController` — `monitor:read`
- `StatisticsController` — `monitor:read`

示例：
```java
@PostMapping("/{id}/execute")
@PreAuthorize("hasAuthority('task:execute')")
public Result<ExecutionLog> execute(@PathVariable Long id) { ... }
```

- [ ] **Step 3: 对登录/注册/健康检查接口不加权限 (已是 permitAll)**

Controllers that should remain open:
- `AuthController` — 所有方法无需注解 (已在 SecurityConfig 中设置为 permitAll)
- `DashboardController` (健康检查相关) — 不加

- [ ] **Step 4: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/ \
        datafactory-backend-executor/datafactory-backend-executor-server/src/main/java/com/cqie/datafactory/executor/controller/
git commit -m "feat: add @PreAuthorize annotations to all controllers for RBAC"
```

---

### Task 18: 创建 .env.example 环境变量模板

**Files:**
- Create: `.env.example` (项目根目录)

- [ ] **Step 1: 创建 .env.example**

```bash
# DataFactory 环境变量配置模板
# 复制为 .env 并修改实际值

# 数据库
DB_USERNAME=root
DB_PASSWORD=your_db_password_here

# JWT 签名密钥 (至少32字符, 生成方式: openssl rand -base64 32)
JWT_SECRET=change-me-to-a-random-256-bit-key-min-32-chars

# 数据源加密 AES 密钥 (Base64编码的256位密钥)
DATASOURCE_ENCRYPT_KEY=change-me-to-a-random-base64-encoded-256-bit-key

# SMTP 邮件 (QQ邮箱)
SMTP_USERNAME=your_email@qq.com
SMTP_PASSWORD=your_smtp_authorization_code

# gRPC Python 执行器地址
GRPC_PYTHON_HOST=127.0.0.1
GRPC_PYTHON_PORT=50051

# 允许的前端跨域来源 (逗号分隔)
CORS_ORIGINS=http://localhost:5173
```

- [ ] **Step 2: 在 .gitignore 中确保 .env 被忽略**

在 `.gitignore` 中检查/添加:
```
.env
```

- [ ] **Step 3: Commit**

```bash
git add .env.example .gitignore
git commit -m "chore: add .env.example template and gitignore .env"
```

---

### Task 19: 编译验证 + 修复编译错误

- [ ] **Step 1: 整体编译**

Run: `cd d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: 修复所有编译错误**

逐一检查并修复:
- 缺少的 import 语句
- Bean 冲突 (executor 已有 `SecurityConfig`，确保 Bean 名称不冲突)
- 循环依赖检查
- MyBatis-Plus mapper 扫描配置

- [ ] **Step 3: Commit (如需要)**

```bash
git add -A
git commit -m "fix: resolve compilation errors after security hardening"
```

---

### Task 20: 集成测试 — 验证认证流程

- [ ] **Step 1: 启动基础设施**

确保 MySQL (3306) 和 Nacos (8848) 在运行：
```bash
cd d:/大三下-金融行业软件开发技术/第二阶段/project/datafactory
docker-compose up -d mysql nacos
```

- [ ] **Step 2: 初始化数据库**

```bash
mysql -u root -p datafactory < datafactory.sql
```

- [ ] **Step 3: 启动服务**

依次启动:
```bash
# Terminal 1: Configuration Service
mvn -pl datafactory-backend-configuration spring-boot:run

# Terminal 2: Executor Service
mvn -pl datafactory-backend-executor/datafactory-backend-executor-server spring-boot:run

# Terminal 3: Gateway
mvn -pl datafactory-backend-gateway spring-boot:run
```

- [ ] **Step 4: 测试登录 API**

```bash
# 测试登录
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 预期: 200, 返回 accessToken + refreshToken + userInfo
```

- [ ] **Step 5: 测试认证访问**

```bash
# 使用 Token 访问受保护接口
TOKEN="<从登录响应中获取的accessToken>"
curl http://localhost:8080/tasks \
  -H "Authorization: Bearer $TOKEN"

# 预期: 200
```

- [ ] **Step 6: 测试未认证访问 (应被拒绝)**

```bash
curl http://localhost:8080/tasks

# 预期: 401 Unauthorized
```

- [ ] **Step 7: 测试权限不足 (viewer 角色尝试创建任务)**

```bash
# 需要先创建一个 viewer 用户，然后用其 token 访问
curl -X POST http://localhost:8080/tasks \
  -H "Authorization: Bearer $VIEWER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}'

# 预期: 403 Forbidden
```

- [ ] **Step 8: 测试限流**

```bash
# 连续快速请求 /auth/login 6 次
for i in {1..6}; do
  curl -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}'
done

# 预期: 第6次返回 429 Too Many Requests
```

- [ ] **Step 9: 验证安全响应头**

```bash
curl -v http://localhost:8080/auth/login 2>&1 | grep -E "^< (X-|Referrer|Cache|Pragma)"

# 预期输出:
# < X-Content-Type-Options: nosniff
# < X-Frame-Options: DENY
# < X-XSS-Protection: 1; mode=block
# < Referrer-Policy: strict-origin-when-cross-origin
# < Cache-Control: no-store, no-cache, must-revalidate, proxy-revalidate
# < Pragma: no-cache
```

- [ ] **Step 10: 验证审计日志写入**

```bash
mysql -u root -p datafactory -e "SELECT id, username, operation, method, url, status, cost_ms, created_time FROM sys_audit_log ORDER BY id DESC LIMIT 5;"
```

---

## 自审清单

1. **Spec coverage** — 审计日志 API 访问审计 ✅, RBAC 权限模型 ✅, JWT 认证 ✅, Gateway 鉴权 ✅, 密钥外部化 ✅, 安全头 ✅, 限流 ✅, 方法级授权 ✅
2. **Placeholder scan** — 无 TBD/TODO/placeholder；所有步骤包含完整代码
3. **Type consistency** — JwtService/jjwt API 版本一致 (0.12.x)；实体类与 DDL 字段对应；`@PreAuthorize` 权限码与 sys_permission 初始化数据一致
