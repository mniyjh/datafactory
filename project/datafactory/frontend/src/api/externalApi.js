import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}/api`);

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

export const externalApi = {
  pageApi(params) {
    return api.get('/external-api', { params });
  },
  createApi(data) {
    return api.post('/external-api', data);
  },
  updateApi(id, data) {
    return api.put(`/external-api/${id}`, data);
  },
  deleteApi(id) {
    return api.delete(`/external-api/${id}`);
  },
  toggleStatus(id) {
    return api.put(`/external-api/${id}/status`);
  },
  listVersions(apiId, environment) {
    return api.get(`/external-api/version/${apiId}`, { params: { environment } });
  },
  createVersion(data) {
    return api.post('/external-api/version', data);
  },
  updateVersion(id, data) {
    return api.put(`/external-api/version/${id}`, data);
  },
  deleteVersion(id) {
    return api.delete(`/external-api/version/${id}`);
  },
  publishVersion(id) {
    return api.post(`/external-api/version/${id}/publish`);
  },
  promoteVersion(data) {
    return api.post('/external-api/version/promote', data);
  },
  setCurrentVersion(id) {
    return api.post(`/external-api/version/${id}/current`);
  },
  testConnection(data) {
    return api.post('/external-api/version/test', data);
  }
};
