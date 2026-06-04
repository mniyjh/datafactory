<template>
  <a-layout class="app-layout">
    <a-layout-sider v-model:collapsed="collapsed" :width="200" :collapsed-width="64" collapsible class="sider">
      <div class="logo" :class="{ 'logo-collapsed': collapsed }">
        <BuildOutlined class="logo-icon" />
        <template v-if="!collapsed">
          <span class="logo-text">数据工厂</span>
        </template>
      </div>

      <a-menu v-model:selectedKeys="selectedKeys" v-model:openKeys="openKeys" :inline-collapsed="collapsed" theme="dark"
        mode="inline" class="side-menu">
        <a-menu-item key="/dashboard" title="首页" @click="go('/dashboard')">
          <template #icon>
            <component :is="menuIcon('/dashboard')" />
          </template>
          首页
        </a-menu-item>

        <a-sub-menu key="sub1" title="数据源管理">
          <template #icon>
            <component :is="menuIcon('/database')" />
          </template>
          <a-menu-item key="/database" title="数据库管理" @click="go('/database')">
            数据库管理
          </a-menu-item>
          <a-menu-item key="/api-config" title="三方API管理" @click="go('/api-config')">
            三方API管理
          </a-menu-item>
        </a-sub-menu>

        <a-menu-item key="/script" title="脚本管理" @click="go('/script')">
          <template #icon>
            <component :is="menuIcon('/script')" />
          </template>
          脚本管理
        </a-menu-item>
        <a-menu-item key="/task" title="任务管理" @click="go('/task')">
          <template #icon>
            <component :is="menuIcon('/task')" />
          </template>
          任务管理
        </a-menu-item>
        <a-menu-item key="/open-api" title="开放接口" @click="go('/open-api')">
          <template #icon>
            <component :is="menuIcon('/open-api')" />
          </template>
          开放接口
        </a-menu-item>
        <a-menu-item key="/component" title="组件管理" @click="go('/component')">
          <template #icon>
            <component :is="menuIcon('/component')" />
          </template>
          组件管理
        </a-menu-item>
        <a-menu-item key="/schedule" title="定时任务" @click="go('/schedule')">
          <template #icon>
            <component :is="menuIcon('/schedule')" />
          </template>
          定时任务
        </a-menu-item>
        <a-menu-item key="/execute-log" title="执行日志" @click="go('/execute-log')">
          <template #icon>
            <component :is="menuIcon('/execute-log')" />
          </template>
          执行日志
        </a-menu-item>
      </a-menu>

      <div class="sider-footer" @click="toggleCollapse">{{ collapsed ? '>' : '<' }}</div>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="top-header">
        <span class="menu-mark" @click="toggleCollapse">
          <component :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined" />
        </span>
        <span>{{ pageTitle }}</span>
      </a-layout-header>
      <a-layout-content class="content">
        <div class="content-inner"><router-view /></div>
      </a-layout-content>
    </a-layout>
    <!-- 全局执行监控悬浮球 -->
    <ExecutionMonitorBall />
  </a-layout>
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