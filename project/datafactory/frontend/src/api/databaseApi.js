import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}/api`);

const api = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
});

// Request interceptor: attach JWT token + tenant ID
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const tenantId = localStorage.getItem('tenantId');
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (res) => {
    if (res.data && res.data.code !== undefined && res.data.code !== 0) {
      return Promise.reject(new Error(res.data.message || '请求失败'));
    }
    return res;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userInfo');
      window.location.hash = '#/login';
    }
    const msg = error?.response?.data?.message || error?.message || '请求失败';
    return Promise.reject(new Error(msg));
  }
);

export const databaseApi = {
  pageDb(params) {
    return api.get('/datasource/db', { params });
  },
  createDb(data) {
    return api.post('/datasource/db', data);
  },
  updateDb(id, data) {
    return api.put(`/datasource/db/${id}`, data);
  },
  deleteDb(id) {
    return api.delete(`/datasource/db/${id}`);
  },
  listVersions(dbId, environment) {
    return api.get('/datasource/db-version', { params: { dbId, environment } });
  },
  createVersion(data) {
    return api.post('/datasource/db-version', data);
  },
  selectVersion(id) {
    return api.post(`/datasource/db-version/${id}/select`);
  },
  testConnection(data) {
    return api.post('/datasource/db-version/test', data);
  }
};
