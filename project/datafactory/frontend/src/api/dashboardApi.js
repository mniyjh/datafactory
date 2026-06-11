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

export const dashboardApi = {
  getStats() {
    return api.get('/dashboard/stats');
  },
  getMetrics() {
    return api.get('/executor/metrics/dashboard', { timeout: 5000 });
  },
  getOverview() {
    return api.get('/statistics/overview');
  },
  getTrend(days = 7) {
    return api.get('/statistics/trend', { params: { days } });
  },
  getTaskRank() {
    return api.get('/statistics/task-rank');
  },
  getExecutionProgress(executionId) {
    return api.get(`/statistics/execution/${executionId}/progress`);
  }
};
