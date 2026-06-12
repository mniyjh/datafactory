<template>
  <div class="app-shell">
    <!-- 左侧菜单栏 — 浏览器导航条级别，始终在最上层 -->
    <aside class="app-sidebar" :class="{ 'sidebar-collapsed': collapsed }">
      <div class="logo" :class="{ 'logo-collapsed': collapsed }">
        <BuildOutlined class="logo-icon" />
        <span v-show="!collapsed" class="logo-text">数据工厂</span>
      </div>

      <nav class="nav-menu">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          :inline-collapsed="collapsed"
          theme="dark"
          mode="inline"
        >
          <a-menu-item key="/dashboard" @click="go('/dashboard')">
            <template #icon><component :is="menuIcon('/dashboard')" /></template>
            首页
          </a-menu-item>

          <a-sub-menu key="sub1" title="数据源管理">
            <template #icon><component :is="menuIcon('/database')" /></template>
            <a-menu-item key="/database" @click="go('/database')">数据库管理</a-menu-item>
            <a-menu-item key="/api-config" @click="go('/api-config')">三方API管理</a-menu-item>
          </a-sub-menu>

          <a-menu-item key="/script" @click="go('/script')">
            <template #icon><component :is="menuIcon('/script')" /></template>
            脚本管理
          </a-menu-item>
          <a-menu-item key="/task" @click="go('/task')">
            <template #icon><component :is="menuIcon('/task')" /></template>
            任务管理
          </a-menu-item>
          <a-menu-item key="/open-api" @click="go('/open-api')">
            <template #icon><component :is="menuIcon('/open-api')" /></template>
            开放接口
          </a-menu-item>
          <a-menu-item key="/component" @click="go('/component')">
            <template #icon><component :is="menuIcon('/component')" /></template>
            组件管理
          </a-menu-item>
          <a-menu-item key="/users" @click="go('/users')">
            <template #icon><component :is="menuIcon('/users')" /></template>
            用户管理
          </a-menu-item>
          <a-menu-item key="/schedule" @click="go('/schedule')">
            <template #icon><component :is="menuIcon('/schedule')" /></template>
            定时任务
          </a-menu-item>
          <a-menu-item key="/execute-log" @click="go('/execute-log')">
            <template #icon><component :is="menuIcon('/execute-log')" /></template>
            执行日志
          </a-menu-item>
        </a-menu>
      </nav>

      <div class="sider-footer" @click="toggleCollapse">{{ collapsed ? '▶' : '◀' }}</div>
    </aside>

    <!-- 右侧页面区域 — 独立滚动容器 -->
    <div class="app-main">
      <header class="app-header">
        <span class="menu-mark" @click="toggleCollapse">
          <component :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined" />
        </span>
        <span>{{ pageTitle }}</span>
        <div class="header-right">
          <a-select
            v-if="authStore.tenants.length > 1"
            :value="authStore.currentTenantId"
            @change="handleTenantChange"
            style="width: 160px; margin-right: 16px;"
            size="small"
          >
            <a-select-option
              v-for="t in authStore.tenants"
              :key="t.id"
              :value="t.id"
            >{{ t.name }}</a-select-option>
          </a-select>
          <a-dropdown>
            <a-space class="user-info">
              <a-avatar size="small"><UserOutlined /></a-avatar>
              <span>{{ authStore.user?.realName || authStore.user?.username }}</span>
            </a-space>
            <template #overlay>
              <a-menu>
                <a-menu-item key="profile" @click="goProfile">个人中心</a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </header>
      <div class="app-content">
        <div class="content-inner">
          <router-view v-slot="{ Component }">
            <keep-alive :max="10">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
      </div>
    </div>

    <!-- 全局执行监控悬浮球 -->
    <ExecutionMonitorBall />
  </div>
</template>

<script setup>
import { computed, h, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import ExecutionMonitorBall from '../components/ExecutionMonitorBall.vue';
import { authStore } from '../store/auth';
import { logout } from '../api/authApi';
import {
  HomeOutlined,
  DatabaseOutlined,
  ApiOutlined,
  CodeOutlined,
  ProfileOutlined,
  LinkOutlined,
  FileTextOutlined,
  AppstoreOutlined,
  TeamOutlined,
  ClockCircleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BuildOutlined,
  UserOutlined
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();

const collapsed = ref(false);
const openKeys = ref(['sub1']);
const selectedKeys = computed(() => [route.path]);

const iconMap = {
  '/dashboard': HomeOutlined,
  '/database': DatabaseOutlined,
  '/api-config': ApiOutlined,
  '/script': CodeOutlined,
  '/task': ProfileOutlined,
  '/open-api': LinkOutlined,
  '/component': AppstoreOutlined,
  '/users': TeamOutlined,
  '/schedule': ClockCircleOutlined,
  '/execute-log': FileTextOutlined
};

const menuIcon = (path) => h(iconMap[path]);

const go = (path) => router.push(path);
const toggleCollapse = () => {
  collapsed.value = !collapsed.value;
};

watch(collapsed, (val) => {
  if (val) {
    openKeys.value = [];
  } else {
    openKeys.value = ['sub1'];
  }
});

const titleMap = {
  '/dashboard': '首页',
  '/database': '数据库管理',
  '/api-config': '三方API管理',
  '/script': '脚本管理',
  '/task': '任务管理',
  '/open-api': '开放接口管理',
  '/component': '组件管理',
  '/users': '用户管理',
  '/schedule': '定时任务',
  '/execute-log': '执行日志'
};

const pageTitle = computed(() => titleMap[route.path] || '首页');

const handleLogout = async () => {
  try {
    await logout();
  } catch (e) {
    // ignore server errors during logout
  }
  authStore.clearAuth();
  router.replace('/login');
};

const goProfile = () => {
  message.info('个人中心功能开发中');
};

const handleTenantChange = (tenantId) => {
  authStore.switchTenant(tenantId);
};
</script>

<style scoped>
.header-right {
  float: right;
  height: 42px;
  display: flex;
  align-items: center;
}
.user-info {
  cursor: pointer;
  padding: 0 8px;
}
.user-info:hover {
  background: #f5f5f5;
}
</style>
