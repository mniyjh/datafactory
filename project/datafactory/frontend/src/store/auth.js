import { reactive } from 'vue';

export const authStore = reactive({
  user: null,
  roles: [],
  permissions: [],
  token: localStorage.getItem('accessToken') || null,
  refreshToken: localStorage.getItem('refreshToken') || null,
  tenants: [],
  currentTenantId: localStorage.getItem('tenantId') || null,

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
    this.tenants = data.user?.tenants || [];
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('userInfo', JSON.stringify(data.user));
    // Auto-select first tenant if none selected
    if (!localStorage.getItem('tenantId') && this.tenants.length > 0) {
      localStorage.setItem('tenantId', this.tenants[0].id);
      this.currentTenantId = this.tenants[0].id;
      localStorage.setItem('tenantName', this.tenants[0].name);
    } else {
      this.currentTenantId = localStorage.getItem('tenantId');
    }
  },

  clearAuth() {
    this.token = null;
    this.refreshToken = null;
    this.user = null;
    this.roles = [];
    this.permissions = [];
    this.tenants = [];
    this.currentTenantId = null;
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userInfo');
    localStorage.removeItem('tenantId');
    localStorage.removeItem('tenantName');
  },

  switchTenant(tenantId) {
    const tenant = this.tenants.find(t => t.id == tenantId);
    if (tenant) {
      localStorage.setItem('tenantId', tenant.id);
      localStorage.setItem('tenantName', tenant.name);
      this.currentTenantId = tenant.id;
      // Reload the page to refresh all data with new tenant context
      window.location.reload();
    }
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
        this.tenants = this.user?.tenants || [];
        this.currentTenantId = localStorage.getItem('tenantId');
      } catch (e) {
        this.clearAuth();
      }
    }
  }
});
