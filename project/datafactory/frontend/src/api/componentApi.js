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

export const componentApi = {
  pageComponent(params) {
    return api.get('/component', { params });
  },
  createComponent(data) {
    return api.post('/component', data);
  },
  updateComponent(id, data) {
    return api.put(`/component/${id}`, data);
  },
  deleteComponent(id) {
    return api.delete(`/component/${id}`);
  },
  toggleStatus(id) {
    return api.put(`/component/${id}/status`);
  },
  getMeta(id) {
    return api.get(`/component/${id}/meta`);
  },
  saveFields(id, data) {
    return api.put(`/component/${id}/fields`, data);
  },
  resolveFieldOptions(data) {
    return api.post('/component/field/options/resolve', data);
  },
  listDatasources() {
    return api.get('/datasource/db/simple');
  },
  listExternalApis() {
    return api.get('/external-api/simple');
  },
  listDbVersions(dbId) {
    return api.get('/datasource/db-version', { params: { dbId } });
  },
  listApiVersions(apiId) {
    return api.get(`/external-api/version/${apiId}`);
  },
  listScripts() {
    return api.get('/script/simple');
  }
};
