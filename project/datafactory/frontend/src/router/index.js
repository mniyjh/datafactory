import { createRouter, createWebHashHistory } from 'vue-router';
import MainLayout from '../layouts/MainLayout.vue';
import { authStore } from '../store/auth';

const DashboardPage = () => import('../views/DashboardPage.vue');
const DatabasePage = () => import('../views/DatabasePage.vue');
const ApiConfigPage = () => import('../views/ApiConfigPage.vue');
const ScriptPage = () => import('../views/ScriptPage.vue');
const TaskPage = () => import('../views/TaskPage.vue');
const OpenApiPage = () => import('../views/OpenApiPage.vue');
const ExecuteLogPage = () => import('../views/ExecuteLogPage.vue');
const ComponentPage = () => import('../views/ComponentPage.vue');
const SchedulePage = () => import('../views/SchedulePage.vue');
const LoginPage = () => import('../views/AnimatedLogin.vue');
const ForgotPasswordPage = () => import('../views/ForgotPassword.vue');
const ProfilePage = () => import('../views/ProfilePage.vue');
const UserPage = () => import('../views/ProfilePage.vue');  // 用户管理已整合到个人中心

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/forgot-password',
      component: ForgotPasswordPage
    },
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
        { path: 'profile', component: ProfilePage },
        { path: 'users', component: UserPage },
        { path: 'schedule', component: SchedulePage },
        { path: 'execute-log', component: ExecuteLogPage }
      ]
    }
  ]
});

router.beforeEach((to, from, next) => {
  authStore.restoreFromStorage();
  if (to.path !== '/login' && to.path !== '/forgot-password' && !authStore.isLoggedIn) {
    next('/login');
  } else if (to.path === '/login' && authStore.isLoggedIn) {
    next('/dashboard');
  } else {
    next();
  }
});

export default router;
