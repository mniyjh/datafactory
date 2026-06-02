import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}:8080/api`);

const api = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
});

api.interceptors.response.use(
  (res) => {
    if (res.data && res.data.code !== undefined && res.data.code !== 0) {
      return Promise.reject(new Error(res.data.message || '请求失败'));
    }
    return res;
  },
  (error) => {
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
