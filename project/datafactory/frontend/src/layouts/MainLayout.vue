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
import ExecutionMonitorBall from '../components/ExecutionMonitorBall.vue';
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
  BuildOutlined
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
  '/schedule': '定时任务',
  '/execute-log': '执行日志'
};

const pageTitle = computed(() => titleMap[route.path] || '首页');
</script>
