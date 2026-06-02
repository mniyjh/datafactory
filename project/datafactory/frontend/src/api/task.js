import axios from 'axios';

const apiBaseURL = import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV
  ? '/api'
  : `${window.location.protocol}//${window.location.hostname}:8080/api`);

const http = axios.create({
  baseURL: apiBaseURL,
  timeout: 60000
});

http.interceptors.response.use(
  (res) => {
    // 拦截业务异常（code !== 0）
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

export const taskApi = {
  // 任务基本信息
  page(params) {
    return http.get('/tasks/page', { params });
  },
  pageTasks(params) {
    return this.page(params);
  },
  detail(id) {
    return http.get(`/tasks/${id}`);
  },
  getTaskDetail(id) {
    return this.detail(id);
  },
  create(data) {
    return http.post('/tasks', data);
  },
  createTask(data) {
    return this.create(data);
  },
  update(data) {
    return http.put(`/tasks/${data.id}`, data);
  },
  updateTask(data) {
    return this.update(data);
  },
  remove(id) {
    return http.delete(`/tasks/${id}`);
  },
  deleteTask(id) {
    return this.remove(id);
  },
  changeStatus(id, statusOrPayload) {
    const payload = typeof statusOrPayload === 'object'
      ? statusOrPayload
      : { status: statusOrPayload === 1 ? '发布' : '停运', publishInfo: '页面状态变更' };
    return http.put(`/tasks/${id}/status`, payload);
  },
  toggleTaskStatus(id, payload) {
    return this.changeStatus(id, payload);
  },
  getTaskHistory(id) {
    return http.get(`/task-dsl/${id}/versions`);
  },

  // 任务 DSL 版本管理
  getTaskVersions(taskId, params) {
    return http.get(`/task-dsl/${taskId}/versions`, { params });
  },
  list(taskId, env) {
    return this.getTaskVersions(taskId, { environment: env });
  },
  listVersions(taskId, env) {
    return this.list(taskId, env);
  },
  createVersion(data) {
    return http.post('/task-dsl/version', data);
  },
  createTaskVersion(data) {
    return this.createVersion(data);
  },
  updateTaskVersion(id, data) {
    return http.put(`/task-dsl/version/${id}`, data);
  },
  publish(versionId) {
    return http.post(`/task-dsl/version/${versionId}/publish`);
  },
  publishVersion(versionId) {
    return this.publish(versionId);
  },
  publishTaskVersion(versionId) {
    return this.publish(versionId);
  },
  promote(taskId, payload) {
    return http.post(`/task-dsl/${taskId}/promote`, payload);
  },
  promoteVersion(taskId, payload) {
    return this.promote(taskId, payload);
  },
  promoteTaskVersion(taskId, payload) {
    return this.promote(taskId, payload);
  },
  rollbackEnvironment(taskId, payload) {
    return http.post(`/task-dsl/${taskId}/rollback-env`, payload);
  },
  rollbackTaskEnvironment(taskId, payload) {
    return this.rollbackEnvironment(taskId, payload);
  },
  removeVersion(versionId) {
    return http.delete(`/task-dsl/version/${versionId}`);
  },
  deleteVersion(versionId) {
    return this.removeVersion(versionId);
  },
  deleteTaskVersion(versionId) {
    return this.removeVersion(versionId);
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
  syncTaskNodes(taskDslId) {
    return this.rebuildComponentSnapshots(taskDslId);
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

export const taskDslApi = taskApi; // For components importing taskDslApi separately
