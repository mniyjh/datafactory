# DataFactory RBAC + 黑白灰主题 + 忘记密码 设计文档

**日期**: 2026-06-15
**状态**: Approved

---

## 1. 概述

### 1.1 目标
1. 全局主题改为黑白灰风格
2. 角色权限精简为 3 个固定角色，移除 `admin` 角色
3. 实现忘记密码功能（邮件验证码）
4. 超级管理员用户管理（仅 super_admin 可见）
5. 角色基于权限渲染前端菜单和小火箭
6. 用户自身保护规则（不能改自己状态、不能删自己）

### 1.2 范围
- 前端: `frontend/src/` — 主题、菜单渲染、忘记密码页面、ProfilePage完善
- 后端: `datafactory-backend-configuration/` — 忘记密码API、用户管理加固
- 数据库: `datafactory.sql` — 角色权限初始化、初始用户

---

## 2. 角色权限方案

### 2.1 3 个固定角色

| 角色 | code | 权限数 |
|------|------|--------|
| 超级管理员 | super_admin | 全部 12 个 |
| 数据开发 | developer | 9 个 |
| 运维 | operator | 7 个 |

### 2.2 12 个权限(不变)

`task:read`, `task:write`, `task:execute`, `datasource:read`, `datasource:write`,
`script:read`, `script:write`, `schedule:read`, `schedule:write`, `user:write`, `monitor:read`, `log:read`

### 2.3 角色-权限分配

**super_admin**: 全部 12 个
**developer**: task:r/w/e, datasource:r/w, script:r/w, schedule:r, log:r
**operator**: task:r/e, datasource:r, script:r, schedule:r, monitor:r, log:r
### 2.4 初始用户

| username | password | role | email | phone |
|----------|----------|------|-------|-------|
| admin | admin123 | super_admin | 1106935659@qq.com | 17815213110 |

---

## 3. 前端菜单按角色渲染

| 菜单/路由 | 权限要求 | super_admin | developer | operator |
|-----------|----------|:--:|:--:|:--:|
| 首页 /dashboard | 登录即可 | ✅ | ✅ | ✅ |
| 数据源管理 /database, /api-config | datasource:read | ✅ | ✅ | ✅ |
| 脚本管理 /script | script:read | ✅ | ✅ | ✅ |
| 任务管理 /task | task:read | ✅ | ✅ | ✅ |
| 开放接口 /open-api | datasource:write | ✅ | ✅ | - |
| 组件管理 /component | task:write | ✅ | ✅ | - |
| 定时任务 /schedule | schedule:read | ✅ | ✅ | - |
| 执行日志 /execute-log | log:read | ✅ | ✅ | ✅ |
| 个人中心 /profile | 登录即可 | ✅ | ✅ | ✅ |
| ┗ 用户管理 Tab | user:write | ✅ | ❌ | ❌ |
| 小火箭悬浮球 | task:execute 或 task:read | ✅ | ✅ | ✅ |

---

## 4. 忘记密码

### 4.1 流程

```
忘记密码页面:
  1. 输入用户名 + 邮箱 → 后端校验两者是否匹配
  2. 匹配成功 → 后端生成 6 位验证码 → 通过 SMTP 发送到用户邮箱
  3. 用户输入验证码 + 新邮箱(用于二次确认) → 后端验证
  4. 验证通过 → 密码重置为 123456
  5. 前端提示"密码已重置为 123456，请登录后修改"
```

### 4.2 约束
- **super_admin 不能使用忘记密码**，返回错误提示
- 其他所有角色均可使用
- 验证码有效期 5 分钟，存储在内存（不落库）
- 前后端均需校验：用户名非空、邮箱格式、验证码6位数字

### 4.3 后端 API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/forgot-password/send-code` | No | 发送验证码到邮箱 |
| POST | `/auth/forgot-password/reset` | No | 验证验证码并重置密码 |

请求体:
```json
// send-code
{ "username": "xxx", "email": "xxx@qq.com" }

// reset
{ "username": "xxx", "email": "xxx@qq.com", "code": "123456", "newPassword": "123456" }
```

---

## 5. 用户管理规则

### 5.1 自身保护
- 用户**不能修改自己的 status**（前端禁用开关 + 后端校验）
- 用户**不能删除自己**（后端校验 `targetUserId != currentUserId`）
- 修改用户时，如果请求体含 `status` 且目标是自己 → 后端拒绝

### 5.2 super_admin 权限
- 可查看/创建/编辑/删除/启禁用所有其他用户
- 用户管理 Tab 仅在 ProfilePage 中向 super_admin 显示
- ProfilePage 中"用户管理" Tab 标记 total 数量

---

## 6. 黑白灰主题

### 6.1 色板

| 用途 | 颜色 |
|------|------|
| 页面背景 | `#f5f5f5` |
| 内容卡片背景 | `#ffffff` |
| 主文字 | `#111111` |
| 次要文字 | `#666666` |
| 边框 | `#e5e5e5` |
| 深色背景(侧边栏) | `#111111` |
| 主按钮/强调 | `#111111` (白底黑字按钮) |
| 危险色 | `#dc2626` |

### 6.2 改动范围
- `styles.css`: 全局背景、卡片、边框、文字颜色
- `MainLayout.vue`: 侧边栏背景、header 颜色
- `ProfilePage.vue`: 已在上一版实现了黑白风格，微调
- `AnimatedLogin.vue`: 已在上一版实现了 dark 风格，保持不变
- `DashboardPage.vue`: 卡片、表格风格统一

---

## 7. 数据库脚本更新

`datafactory.sql` 需更新:
1. 删除 `admin` 和 `viewer` 角色的 INSERT 及权限分配
2. 更新初始 admin 用户邮箱为 `1106935659@qq.com`，手机为 `17815213110`
3. 删除 `token_blacklist` 历史数据

---

## 8. 不涉及的范围
- gRPC Python 执行器
- 调度执行引擎
- 数据源加密
- 租户功能
- 审计日志(已有，不动)
- docker-compose / Nacos / Prometheus / Grafana

---

## 9. 实现顺序

1. 数据库脚本更新
2. 后端: 移除 admin 角色、忘记密码 API、用户管理加固
3. 前端: 黑白灰主题
4. 前端: 忘记密码页面
5. 前端: 角色菜单渲染 + 小火箭权限控制
6. 前端: ProfilePage 完善
7. 集成测试
