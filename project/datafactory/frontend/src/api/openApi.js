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

export const openApi = {
  pageApi(params) {
    return api.get('/open-api', { params });
  },
  createApi(data) {
    return api.post('/open-api', data);
  },
  updateApi(id, data) {
    return api.put(`/open-api/${id}`, data);
  },
  deleteApi(id) {
    return api.delete(`/open-api/${id}`);
  },
  toggleStatus(id) {
    return api.put(`/open-api/${id}/status`);
  },
  generateKey(id) {
    return api.post(`/open-api/${id}/key`);
  },
  invokeByCode(code, payload, appSecret) {
    const headers = appSecret ? { 'X-App-Secret': appSecret } : {};
    return api.post(`/open-api/invoke/${code}`, payload || {}, { headers });
  }
};
