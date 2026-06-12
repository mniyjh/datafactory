import { reactive } from 'vue';

export const authStore = reactive({
  user: null,
  roles: [],
  permissions: [],
  token: localStorage.getItem('accessToken') || null,
  refreshToken: localStorage.getItem('refreshToken') || null,

  get isLoggedIn() {
    return !!this.token;
  },

  get isAdmin() {
    return this.roles.includes('super_admin') || this.roles.includes('admin');
  },

  hasPermission(perm) {
    return this.permissions.includes(perm);
  },

  setAuth(data) {
    this.token = data.accessToken;
    this.refreshToken = data.refreshToken;
    this.user = data.user;
    this.roles = data.user?.roles || [];
    this.permissions = data.user?.permissions || [];
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('userInfo', JSON.stringify(data.user));
  },

  clearAuth() {
    this.token = null;
    this.refreshToken = null;
    this.user = null;
    this.roles = [];
    this.permissions = [];
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userInfo');
  },

  restoreFromStorage() {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('userInfo');
    if (token && userStr) {
      try {
        this.token = token;
        this.refreshToken = localStorage.getItem('refreshToken');
        this.user = JSON.parse(userStr);
        this.roles = this.user?.roles || [];
        this.permissions = this.user?.permissions || [];
      } catch (e) {
        this.clearAuth();
      }
    }
  }
});
