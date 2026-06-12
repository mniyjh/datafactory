import { createRouter, createWebHashHistory } from 'vue-router';
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
import LoginPage from '../views/LoginPage.vue';
import UserPage from '../views/UserPage.vue';
import { authStore } from '../store/auth';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      component: LoginPage
    },
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
        { path: 'users', component: UserPage },
        { path: 'schedule', component: SchedulePage },
        { path: 'execute-log', component: ExecuteLogPage }
      ]
    }
  ]
});

router.beforeEach((to, from, next) => {
  authStore.restoreFromStorage();
  if (to.path !== '/login' && !authStore.isLoggedIn) {
    next('/login');
  } else if (to.path === '/login' && authStore.isLoggedIn) {
    next('/dashboard');
  } else {
    next();
  }
});

export default router;
