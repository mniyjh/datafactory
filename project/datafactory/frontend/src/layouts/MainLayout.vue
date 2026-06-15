<template>
  <div class="app-shell">
    <!-- 左侧菜单栏 -->
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
          <!-- 首页 -->
          <a-menu-item key="/dashboard" @click="go('/dashboard')">
            <template #icon><HomeOutlined /></template>
            首页
          </a-menu-item>

          <a-sub-menu v-if="subMenuItems.length > 0" key="sub1" title="数据源管理">
            <template #icon><DatabaseOutlined /></template>
            <a-menu-item v-for="item in subMenuItems" :key="item.key" @click="go(item.key)">{{ item.label }}</a-menu-item>
          </a-sub-menu>

          <a-menu-item v-for="item in topMenuItems" :key="item.key" @click="go(item.key)">
            <template #icon><component :is="item.icon" /></template>
            {{ item.label }}
          </a-menu-item>
        </a-menu>
      </nav>

      <!-- 监控服务快捷入口（替换了原来的折叠按钮） -->
      <div class="sider-footer">
        <a class="service-link" href="http://127.0.0.1:8848/nacos" target="_blank" title="Nacos 注册中心">
          <CloudServerOutlined /><span v-show="!collapsed">Nacos</span>
        </a>
        <a class="service-link" href="http://127.0.0.1:9090" target="_blank" title="Prometheus 监控">
          <DashboardOutlined /><span v-show="!collapsed">Prometheus</span>
        </a>
        <a class="service-link" href="http://127.0.0.1:3001" target="_blank" title="Grafana 图表">
          <LineChartOutlined /><span v-show="!collapsed">Grafana</span>
        </a>
      </div>
    </aside>

    <!-- 右侧页面区域 -->
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
    <ExecutionMonitorBall v-if="hasPerm('task:execute') || hasPerm('task:read')" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
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
  ClockCircleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BuildOutlined,
  UserOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();

const hasPerm = (perm) => !perm || authStore.hasPermission(perm);

const visibleMenuItems = computed(() => {
  return [
    { key: '/database', icon: DatabaseOutlined, label: '数据库管理', perm: 'datasource:read', parent: 'sub1' },
    { key: '/api-config', icon: ApiOutlined, label: '三方API管理', perm: 'datasource:read', parent: 'sub1' },
    { key: '/script', icon: CodeOutlined, label: '脚本管理', perm: 'script:read' },
    { key: '/task', icon: ProfileOutlined, label: '任务管理', perm: 'task:read' },
    { key: '/open-api', icon: LinkOutlined, label: '开放接口', perm: 'datasource:write' },
    { key: '/component', icon: AppstoreOutlined, label: '组件管理', perm: 'task:write' },
    { key: '/schedule', icon: ClockCircleOutlined, label: '定时任务', perm: 'schedule:read' },
    { key: '/execute-log', icon: FileTextOutlined, label: '执行日志', perm: 'log:read' },
  ].filter(item => !item.perm || hasPerm(item.perm));
});

const subMenuItems = computed(() => visibleMenuItems.value.filter(i => i.parent === 'sub1'));
const topMenuItems = computed(() => visibleMenuItems.value.filter(i => !i.parent));

const collapsed = ref(false);
const openKeys = ref(['sub1']);
const selectedKeys = computed(() => [route.path]);

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

const go = (path) => router.push(path);

const titleMap = {
  '/profile': '个人中心',
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
  router.push('/profile');
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
