# RBAC + 黑白灰主题 + 忘记密码 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 精简角色为 3 个、实现忘记密码(邮件验证码)、全局黑白灰主题、角色驱动菜单渲染、用户自身保护

**Architecture:** 数据库层更新初始化脚本 → 后端新增忘记密码 API + 用户管理加固 → 前端主题改造 + 角色菜单渲染 + 忘记密码页面

**Tech Stack:** Spring Boot 3 + MyBatis-Plus + Spring Mail + Vue 3 + Ant Design Vue + GSAP

---

### Task 1: 数据库脚本更新

**Files:**
- Modify: `project/datafactory/datafactory.sql:727-777`

- [ ] **Step 1: 删除 admin 和 viewer 角色，更新 admin 用户信息**

将第730-735行的角色数据改为：

```sql
INSERT INTO sys_role (name, code, description, status) VALUES
('超级管理员', 'super_admin', '拥有系统所有权限', 1),
('数据开发', 'developer', '任务/脚本/数据源 CRUD + 执行', 1),
('运维', 'operator', '查看 + 执行 + 监控', 1);
```

- [ ] **Step 2: 删除 admin 角色的权限分配**

删除第744-749行(admin 的 sys_role_permission INSERT)。

- [ ] **Step 3: 删除 viewer 角色的权限分配**

删除第763-766行(viewer 的 sys_role_permission INSERT)。

- [ ] **Step 4: 更新 admin 用户信息**

将第772-773行改为：

```sql
INSERT INTO sys_user (username, password, real_name, email, phone, status, created_by) VALUES
('admin', '$2a$10$l2KLCtdHeXrOYPUsx9kAnutLzc9ElZuiBwQQxTUSWgQ1tydkfxNpW', '系统管理员', '1106935659@qq.com', '17815213110', 1, 'SYSTEM');
```

- [ ] **Step 5: Commit**

```bash
git add project/datafactory/datafactory.sql
git commit -m "feat: remove admin/viewer roles, update super_admin contact info"
```

---

### Task 2: 后端 — 忘记密码 API (验证码服务)

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/VerificationCodeManager.java`

- [ ] **Step 1: 创建内存验证码管理器**

```java
package com.cqie.datafactory.configuration.service;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VerificationCodeManager {

    private static final long TTL_SECONDS = 300; // 5分钟

    private final ConcurrentHashMap<String, CodeEntry> store = new ConcurrentHashMap<>();

    public String generate(String key) {
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        store.put(key, new CodeEntry(code, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    public boolean verify(String key, String code) {
        CodeEntry entry = store.get(key);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(key);
            return false;
        }
        boolean match = entry.code.equals(code);
        if (match) store.remove(key);
        return match;
    }

    public void remove(String key) { store.remove(key); }

    private record CodeEntry(String code, Instant expiresAt) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/VerificationCodeManager.java
git commit -m "feat: add in-memory verification code manager with 5-min TTL"
```

---

### Task 3: 后端 — 忘记密码 API (邮件服务)

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/EmailService.java`

- [ ] **Step 1: 创建邮件发送服务**

```java
package com.cqie.datafactory.configuration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("1106935659@qq.com");
        msg.setTo(to);
        msg.setSubject("DataFactory 密码重置验证码");
        msg.setText("您的验证码是: " + code + "，有效期 5 分钟。如非本人操作请忽略。");
        mailSender.send(msg);
        log.info("Verification code sent to {}", to);
    }
}
```

- [ ] **Step 2: 确保 Configuration 服务有邮件配置**

检查 `datafactory-backend-configuration/src/main/resources/application.yml` 是否含邮件配置。如果没有，添加：

```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 465
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    protocol: smtps
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
          starttls:
            enable: true
            required: true
```

- [ ] **Step 3: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/EmailService.java
git commit -m "feat: add email service for verification code delivery"
```

---

### Task 4: 后端 — 忘记密码 API (DTO + Controller)

**Files:**
- Create: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/dto/ForgotPasswordDTO.java`
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/controller/AuthController.java`
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/AuthService.java`
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/AuthServiceImpl.java`

- [ ] **Step 1: 创建 DTO**

```java
package com.cqie.datafactory.configuration.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

public class ForgotPasswordDTO {

    @Data
    public static class SendCodeRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class ResetRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码为6位数字")
        private String code;
    }
}
```

- [ ] **Step 2: AuthService 接口新增方法**

```java
// 在 AuthService.java 中添加
void sendVerificationCode(String username, String email);
void resetPassword(String username, String email, String code);
```

- [ ] **Step 3: AuthServiceImpl 实现**

```java
// 在 AuthServiceImpl.java 中添加依赖注入
private final EmailService emailService;
private final VerificationCodeManager codeManager;

@Override
public void sendVerificationCode(String username, String email) {
    User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, username)
    );
    if (user == null) {
        throw new BusinessException("用户名不存在");
    }
    if (!email.equals(user.getEmail())) {
        throw new BusinessException("用户名与邮箱不匹配");
    }
    // super_admin 不能使用忘记密码
    List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
    if (roles.contains("super_admin")) {
        throw new BusinessException("超级管理员不能使用忘记密码功能");
    }
    String code = codeManager.generate(username + ":" + email);
    emailService.sendVerificationCode(email, code);
    log.info("Verification code sent to user {} at {}", username, email);
}

@Override
public void resetPassword(String username, String email, String code) {
    User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, username)
    );
    if (user == null) {
        throw new BusinessException("用户名不存在");
    }
    if (!codeManager.verify(username + ":" + email, code)) {
        throw new BusinessException("验证码错误或已过期");
    }
    String encodedPwd = passwordEncoder.encode("123456");
    user.setPassword(encodedPwd);
    userMapper.updateById(user);
    log.info("Password reset for user {}", username);
}
```

- [ ] **Step 4: AuthController 新增端点**

```java
// 在 AuthController.java 中添加
@Autowired
private AuthService authService;

@PostMapping("/forgot-password/send-code")
public R<Void> sendVerificationCode(@Valid @RequestBody ForgotPasswordDTO.SendCodeRequest req) {
    authService.sendVerificationCode(req.getUsername(), req.getEmail());
    return R.ok();
}

@PostMapping("/forgot-password/reset")
public R<Void> resetPassword(@Valid @RequestBody ForgotPasswordDTO.ResetRequest req) {
    authService.resetPassword(req.getUsername(), req.getEmail(), req.getCode());
    return R.ok();
}
```

- [ ] **Step 5: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/
git commit -m "feat: add forgot-password API with email verification code"
```

---

### Task 5: 后端 — 用户管理加固(自身保护)

**Files:**
- Modify: `datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/UserServiceImpl.java`

- [ ] **Step 1: 新增自身保护校验辅助方法，修改 updateUser 和 deleteUser**

在 `UserServiceImpl` 属性中添加：

```java
@Value("${security.internal-auth-key:}")
private String internalAuthKey;
```

新增方法：

```java
private Long getCurrentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof Long) {
        return (Long) auth.getDetails();
    }
    return null;
}

private void assertNotSelf(Long targetUserId) {
    Long selfId = getCurrentUserId();
    if (selfId != null && selfId.equals(targetUserId)) {
        throw new BusinessException("不能操作自己的账户");
    }
}
```

修改 `updateUser` 方法，在开头添加：

```java
assertNotSelf(id);
// 不能改自己的 status
if (dto.getStatus() != null) {
    Long selfId = getCurrentUserId();
    if (selfId != null && selfId.equals(id)) {
        throw new BusinessException("不能修改自己的状态");
    }
}
```

修改 `deleteUser` 方法，第一行添加：

```java
assertNotSelf(id);
```

- [ ] **Step 2: Commit**

```bash
git add datafactory-backend-configuration/src/main/java/com/cqie/datafactory/configuration/service/impl/UserServiceImpl.java
git commit -m "feat: prevent self-status-change and self-deletion for all users"
```

---

### Task 6: 前端 — 全局黑白灰主题

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: 更新全局色板**

```css
/* 替换 styles.css 第1-8行 */
html, body, #app {
  margin: 0; padding: 0; height: 100%;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;
  color: #111;
}

/* 侧边栏: 深黑 */
.app-sidebar {
  background: #111 !important;
}
.sider-footer {
  background: #1a1a1a !important;
  border-top-color: rgba(255,255,255,0.06) !important;
}

/* Header */
.app-header {
  background: #fff;
  border-bottom: 1px solid #e5e5e5;
}

/* 内容卡片 */
.content-inner {
  background: #fff;
}

/* 按钮风格: 黑底白字 */
.submit-btn, .btn-dark, button[type="submit"].ant-btn-primary {
  background: #111 !important;
  border-color: #111 !important;
}
.submit-btn:hover, .btn-dark:hover {
  background: #333 !important;
  border-color: #333 !important;
}

/* 链接 */
a, .op-link { color: #111; }
.op-tag-blue { color: #555; }

/* 危险色统一 */
.op-danger { color: #dc2626; }
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/styles.css
git commit -m "style: apply black-white-gray theme to global styles"
```

---

### Task 7: 前端 — 侧边栏黑底 + 角色菜单渲染

**Files:**
- Modify: `frontend/src/layouts/MainLayout.vue`

- [ ] **Step 1: 添加 computed 菜单过滤逻辑**

```javascript
// 在 script setup 中添加，authStore 已导入
const hasPermission = (perm) => authStore.hasPermission(perm);

const menuItems = computed(() => {
  const all = [
    { key: '/dashboard', icon: HomeOutlined, label: '首页', perm: null },
    { key: '/database', icon: DatabaseOutlined, label: '数据库管理', perm: 'datasource:read', parent: 'sub1' },
    { key: '/api-config', icon: ApiOutlined, label: '三方API管理', perm: 'datasource:read', parent: 'sub1' },
    { key: '/script', icon: CodeOutlined, label: '脚本管理', perm: 'script:read' },
    { key: '/task', icon: ProfileOutlined, label: '任务管理', perm: 'task:read' },
    { key: '/open-api', icon: LinkOutlined, label: '开放接口', perm: 'datasource:write' },
    { key: '/component', icon: AppstoreOutlined, label: '组件管理', perm: 'task:write' },
    { key: '/schedule', icon: ClockCircleOutlined, label: '定时任务', perm: 'schedule:read' },
    { key: '/execute-log', icon: FileTextOutlined, label: '执行日志', perm: 'log:read' },
  ];
  return all.filter(item => !item.perm || hasPermission(item.perm));
});

const showSubMenu = computed(() => menuItems.value.some(i => i.parent === 'sub1'));
```

- [ ] **Step 2: 更新模板，菜单项使用 v-if 控制**

在 `<a-menu>` 中，每个静态菜单项改为使用 `v-for` 动态渲染，`<a-sub-menu>` 用 `v-if="showSubMenu"` 包裹。

```html
<a-sub-menu v-if="showSubMenu" key="sub1" title="数据源管理">
  <template #icon><DatabaseOutlined /></template>
  <template v-for="item in menuItems.filter(i => i.parent === 'sub1')" :key="item.key">
    <a-menu-item :key="item.key" @click="go(item.key)">{{ item.label }}</a-menu-item>
  </template>
</a-sub-menu>

<template v-for="item in menuItems.filter(i => !i.parent)" :key="item.key">
  <a-menu-item :key="item.key" @click="go(item.key)">
    <template #icon><component :is="item.icon" /></template>
    {{ item.label }}
  </a-menu-item>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/layouts/MainLayout.vue
git commit -m "feat: role-based sidebar menu rendering with dark theme"
```

---

### Task 8: 前端 — 小火箭按角色控制

**Files:**
- Modify: `frontend/src/layouts/MainLayout.vue:120`

- [ ] **Step 1: 根据权限控制小火箭显示**

在模板中：

```html
<ExecutionMonitorBall v-if="hasPermission('task:execute') || hasPermission('task:read')" />
```

`hasPermission` 已在 Task 7 中添加。

- [ ] **Step 2: Commit**

```bash
git add frontend/src/layouts/MainLayout.vue
git commit -m "feat: show execution rocket only for users with task permissions"
```

---

### Task 9: 前端 — 忘记密码页面

**Files:**
- Create: `frontend/src/views/ForgotPassword.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/views/AnimatedLogin.vue` (添加"忘记密码"链接)
- Modify: `frontend/src/api/authApi.js`

- [ ] **Step 1: 更新 authApi.js 添加忘记密码 API 调用**

```javascript
// 在 authApi.js 末添加
export function sendVerificationCode(username, email) {
  return api.post('/auth/forgot-password/send-code', { username, email });
}
export function resetPassword(username, email, code) {
  return api.post('/auth/forgot-password/reset', { username, email, code });
}
```

- [ ] **Step 2: 创建 ForgotPassword.vue**

```vue
<template>
  <div class="fp-container">
    <div class="fp-card">
      <h1 class="fp-title">忘记密码</h1>
      <p class="fp-subtitle">输入用户名和邮箱接收验证码</p>

      <a-form :model="form" :rules="rules" ref="formRef" layout="vertical" size="large">
        <a-form-item name="username">
          <a-input v-model:value="form.username" placeholder="用户名" />
        </a-form-item>
        <a-form-item name="email">
          <a-input v-model:value="form.email" placeholder="注册邮箱" />
        </a-form-item>

        <!-- Step 1: 发送验证码 -->
        <template v-if="step === 1">
          <a-button type="primary" :loading="sending" block class="fp-btn" @click="sendCode">
            {{ sending ? '发送中...' : '获取验证码' }}
          </a-button>
          <div v-if="countdown > 0" class="fp-hint">{{ countdown }}秒后可重新发送</div>
        </template>

        <!-- Step 2: 输入验证码 + 重置 -->
        <template v-else>
          <a-form-item name="code">
            <a-input v-model:value="form.code" placeholder="6位验证码" maxlength="6" />
          </a-form-item>
          <a-button type="primary" :loading="resetting" block class="fp-btn" @click="doReset">
            {{ resetting ? '重置中...' : '重置密码' }}
          </a-button>
        </template>

        <div v-if="errorMsg" class="fp-error">{{ errorMsg }}</div>
        <div v-if="successMsg" class="fp-success">{{ successMsg }}</div>
      </a-form>

      <div class="fp-back">
        <router-link to="/login">← 返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { sendVerificationCode, resetPassword } from '../api/authApi';
import { message } from 'ant-design-vue';

const formRef = ref();
const step = ref(1);
const sending = ref(false);
const resetting = ref(false);
const errorMsg = ref('');
const successMsg = ref('');
const countdown = ref(0);

const form = reactive({ username: '', email: '', code: '' });
const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, type: 'email', message: '请输入正确的邮箱' }],
};

let cdTimer = null;
async function sendCode() {
  try { await formRef.value.validate(); } catch { return; }
  sending.value = true; errorMsg.value = ''; successMsg.value = '';
  try {
    await sendVerificationCode(form.username, form.email);
    step.value = 2;
    successMsg.value = '验证码已发送，请检查邮箱';
    countdown.value = 60;
    cdTimer = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(cdTimer); }, 1000);
  } catch (e) { errorMsg.value = e.message || '发送失败'; }
  finally { sending.value = false; }
}

async function doReset() {
  if (!form.code || form.code.length !== 6) { errorMsg.value = '请输入6位验证码'; return; }
  resetting.value = true; errorMsg.value = '';
  try {
    await resetPassword(form.username, form.email, form.code);
    successMsg.value = '密码已重置为 123456，请返回登录';
    step.value = 1; form.code = '';
  } catch (e) { errorMsg.value = e.message || '重置失败'; }
  finally { resetting.value = false; }
}
</script>

<style scoped>
.fp-container {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #f5f5f5;
}
.fp-card {
  width: 100%; max-width: 400px;
  background: #fff; border-radius: 12px; padding: 40px 36px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
}
.fp-title { font-size: 24px; font-weight: 700; color: #111; text-align: center; margin: 0 0 8px; }
.fp-subtitle { font-size: 14px; color: #888; text-align: center; margin: 0 0 28px; }
.fp-btn { height: 44px; border-radius: 8px; background: #111; border-color: #111; }
.fp-btn:hover { background: #333; border-color: #333; }
.fp-hint { text-align: center; font-size: 12px; color: #999; margin-top: 8px; }
.fp-error { color: #dc2626; text-align: center; margin-top: 12px; font-size: 13px; }
.fp-success { color: #16a34a; text-align: center; margin-top: 12px; font-size: 13px; }
.fp-back { text-align: center; margin-top: 20px; }
.fp-back a { color: #666; font-size: 13px; text-decoration: none; }
.fp-back a:hover { color: #111; }
</style>
```

- [ ] **Step 3: 路由中添加忘记密码**

```javascript
// router/index.js
const ForgotPasswordPage = () => import('../views/ForgotPassword.vue');

// routes 数组添加
{ path: '/forgot-password', component: ForgotPasswordPage }
```

- [ ] **Step 4: 登录页添加"忘记密码"链接**

在 `AnimatedLogin.vue` 的表单底部添加：

```html
<div class="fp-link">
  <router-link to="/forgot-password">忘记密码？</router-link>
</div>
```

```css
.fp-link { text-align: center; margin-top: 16px; }
.fp-link a { color: rgba(255,255,255,0.5); font-size: 13px; text-decoration: none; }
.fp-link a:hover { color: rgba(255,255,255,0.8); }
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/ForgotPassword.vue frontend/src/router/index.js \
        frontend/src/views/AnimatedLogin.vue frontend/src/api/authApi.js
git commit -m "feat: add forgot password page with email verification code flow"
```

---

### Task 10: 前端 — ProfilePage 完善（用户管理 Tab 权限控制）

**Files:**
- Modify: `frontend/src/views/ProfilePage.vue`

- [ ] **Step 1: 用户管理 Tab 仅 super_admin 可见**

`ProfilePage.vue` 中已有 `isAdmin` 变量（`authStore.isAdmin` 检查 `super_admin` 或 `admin` 角色）。由于移除了 admin 角色，`isAdmin` 现在等价于 super_admin。确认模板中用户管理 Tab 使用 `v-if="isAdmin"` 控制。若当前用的是 `hasPermission('user:write')`，改为 `isAdmin`。

- [ ] **Step 2: 用户管理 Tab 中，禁用当前用户的开关/删除按钮**

在用户表格的操作列中，为当前登录用户所在行添加判断：

```html
<a-switch
  v-if="record.id !== authStore.user?.id"
  :checked="record.status === 'enabled' || record.status === 1"
  @change="(checked) => toggleStatus(record, checked)"
/>
<span v-else class="self-hint">当前用户</span>

<a-popconfirm
  v-if="record.id !== authStore.user?.id"
  title="确定删除该用户吗？"
  @confirm="handleDelete(record.id)"
>
  <a-button size="small" danger>删除</a-button>
</a-popconfirm>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/ProfilePage.vue
git commit -m "feat: restrict user management tab to super_admin, prevent self-disable/delete in table"
```

---

### Task 11: 集成验证

- [ ] **Step 1: 重建数据库**

```bash
# 在 MySQL 中执行
DROP DATABASE IF EXISTS datafactory;
CREATE DATABASE datafactory DEFAULT CHARACTER SET utf8mb4;
USE datafactory;
SOURCE project/datafactory/datafactory.sql;
```

- [ ] **Step 2: 启动后端并测试忘记密码 API**

```bash
# 启动 Configuration 服务
# 测试发送验证码
curl -X POST http://127.0.0.1:8081/auth/forgot-password/send-code \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"1106935659@qq.com"}'
# 预期: 返回错误 "超级管理员不能使用忘记密码功能"
```

- [ ] **Step 3: 验证前端主题和菜单**

启动前端 `cd frontend && npm run dev`，用 admin 登录：
- 侧边栏为深黑 `#111`
- 所有菜单项可见（super_admin 有全部权限）
- 个人中心有"用户管理" Tab
- 小火箭可见

创建一个 developer 角色用户测试：
- 侧边栏无"开放接口"、"组件管理"、"定时任务"
- 个人中心无"用户管理" Tab
- 小火箭可见

- [ ] **Step 4: Commit final fixes**

```bash
git add -A
git commit -m "chore: integration fixes and final adjustments"
```
