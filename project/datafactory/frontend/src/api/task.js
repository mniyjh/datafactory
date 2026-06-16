import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}/api`);

const http = axios.create({
  baseURL: apiBaseURL,
  timeout: 60000
});

// Request interceptor: attach JWT token + tenant ID
http.interceptors.request.use(
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

http.interceptors.response.use(
  (res) => {
    // 拦截业务异常（code !== 0）
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

export const taskApi = {
  // 任务基本信息
  page(params) {
    return http.get('/tasks/page', { params });
  },
  detail(id) {
    return http.get(`/tasks/${id}`);
  },
  create(data) {
    return http.post('/tasks', data);
  },
  update(data) {
    return http.put(`/tasks/${data.id}`, data);
  },
  remove(id) {
    return http.delete(`/tasks/${id}`);
  },
  changeStatus(id, statusOrPayload) {
    const payload = typeof statusOrPayload === 'object'
      ? statusOrPayload
      : { status: statusOrPayload === 1 ? '发布' : '停运', publishInfo: '页面状态变更' };
    return http.put(`/tasks/${id}/status`, payload);
  },
  getTaskHistory(id) {
    return http.get(`/task-dsl/${id}/versions`);
  },

  // 任务 DSL 版本管理
  getTaskVersions(taskId, params) {
    return http.get(`/task-dsl/${taskId}/versions`, { params });
  },
  createVersion(data) {
    return http.post('/task-dsl/version', data);
  },
  updateTaskVersion(id, data) {
    return http.put(`/task-dsl/version/${id}`, data);
  },
  publish(versionId) {
    return http.post(`/task-dsl/version/${versionId}/publish`);
  },
  promote(taskId, payload) {
    return http.post(`/task-dsl/${taskId}/promote`, payload);
  },
  rollbackEnvironment(taskId, payload) {
    return http.post(`/task-dsl/${taskId}/rollback-env`, payload);
  },
  removeVersion(versionId) {
    return http.delete(`/task-dsl/version/${versionId}`);
  },
  setCurrentVersion(versionId) {
    return http.post(`/task-dsl/version/${versionId}/current`);
  },
  current(taskId, environment) {
    return http.get(`/task-dsl/${taskId}/current`, { params: { environment } });
  },

  // 节点字段/参数值
  getNodeFields(taskId, nodeId) {
    return http.get(`/task/${taskId}/node/${nodeId}/fields`);
  },
  saveNodeFields(taskId, nodeId, values, params) {
    return http.put(`/task/${taskId}/node/${nodeId}/fields`, values, { params });
  },
  getNodeIoParams(taskId, nodeId) {
    return http.get(`/task/${taskId}/node/${nodeId}/io-params`);
  },
  saveNodeIoParams(taskId, nodeId, values, params) {
    return http.put(`/task/${taskId}/node/${nodeId}/io-params`, values, { params });
  },
  rebuildComponentSnapshots(taskDslId) {
    return http.post(`/component-snapshot/${taskDslId}/rebuild`);
  },
  getOutdatedNodes(taskId, environment) {
    return http.get(`/task-dsl/${taskId}/outdatedNodes`, { params: { environment } });
  },

  // 任务测试配置管理
  getTestConfigs(taskId, versionId) {
    return http.get(`/task-test-config/list/${taskId}`, { params: { versionId } });
  },
  saveTestConfig(data) {
    return http.post('/task-test-config', data);
  },
  deleteTestConfig(id) {
    return http.delete(`/task-test-config/${id}`);
  },

  // 任务执行与日志
  execute(id, params) {
    return http.post(`/tasks/${id}/execute`, params || {});
  },
  test(id, params) {
    return http.post(`/tasks/${id}/test`, params || {});
  },
  pageLogs(params) {
    return http.get('/executor/log/page', { params });
  },
  getLogDetail(executionId) {
    return http.get(`/executor/log/detail/${executionId}`);
  },
  getNodeLogs(executionId) {
    return http.get(`/executor/log/nodes/${executionId}`);
  },
  getExecutionStats() {
    return http.get('/executor/log/stats');
  }
};

// For components that import taskDslApi separately
export { taskApi as taskDslApi };
