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

export const scheduleApi = {
  list() {
    return api.get('/schedule');
  },
  get(id) {
    return api.get(`/schedule/${id}`);
  },
  create(data) {
    return api.post('/schedule', data);
  },
  update(id, data) {
    return api.put(`/schedule/${id}`, data);
  },
  remove(id) {
    return api.delete(`/schedule/${id}`);
  },
  toggle(id) {
    return api.post(`/schedule/${id}/toggle`);
  },
  trigger(id) {
    return api.post(`/schedule/${id}/trigger`);
  },
  executions(id) {
    return api.get(`/schedule/${id}/executions`);
  }
};
