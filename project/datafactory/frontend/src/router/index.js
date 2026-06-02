import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router';
import MainLayout from '../layouts/MainLayout.vue';
import DashboardPage from '../views/DashboardPage.vue';
import DatabasePage from '../views/DatabasePage.vue';
import ApiConfigPage from '../views/ApiConfigPage.vue';
import ScriptPage from '../views/ScriptPage.vue';
import TaskPage from '../views/TaskPage.vue';
import OpenApiPage from '../views/OpenApiPage.vue';
import ExecuteLogPage from '../views/ExecuteLogPage.vue';
import ComponentPage from '../views/ComponentPage.vue';
import SchedulePage from '../views/SchedulePage.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: DashboardPage },
        { path: 'database', component: DatabasePage },
        { path: 'api-config', component: ApiConfigPage },
        { path: 'script', component: ScriptPage },
        { path: 'task', component: TaskPage },
        { path: 'open-api', component: OpenApiPage },
        { path: 'component', component: ComponentPage },
        { path: 'schedule', component: SchedulePage },
        { path: 'execute-log', component: ExecuteLogPage }
      ]
    }
  ]
});

export default router;
