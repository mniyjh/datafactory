import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}:8080/api`);

const api = axios.create({
  baseURL: apiBaseURL,
  timeout: 60000
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

export const scriptApi = {
  pageScript(params) {
    return api.get('/script', { params });
  },
  createScript(data) {
    return api.post('/script', data);
  },
  updateScript(id, data) {
    return api.put(`/script/${id}`, data);
  },
  deleteScript(id) {
    return api.delete(`/script/${id}`);
  },
  toggleStatus(id) {
    return api.put(`/script/${id}/status`);
  },
  listVersions(scriptId, environment) {
    return api.get(`/script/version/${scriptId}`, { params: { environment } });
  },
  createVersion(data) {
    return api.post('/script/version', data);
  },
  updateVersion(id, data) {
    return api.put(`/script/version/${id}`, data);
  },
  deleteVersion(id) {
    return api.delete(`/script/version/${id}`);
  },
  publishVersion(id) {
    return api.post(`/script/version/${id}/publish`);
  },
  setCurrentVersion(id) {
    return api.post(`/script/version/${id}/current`);
  },
  testVersion(id, inputJson) {
    const body = inputJson?.trim() ? inputJson : undefined;
    return api.post(`/script/version/${id}/test`, body, {
      headers: body ? { 'Content-Type': 'text/plain' } : {}
    });
  },
  promoteVersion(id, toEnvironment) {
    return api.post(`/script/version/${id}/promote`, null, { params: { toEnvironment } });
  }
};
