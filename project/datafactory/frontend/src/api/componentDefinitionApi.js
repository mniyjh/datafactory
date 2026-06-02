import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
});

api.interceptors.response.use(
  (res) => {
    if (res.data && res.data.code !== undefined && res.data.code !== 0) {
      return Promise.reject(new Error(res.data.message || '请求失败'));
    }
    return res;
  },
  (error) => Promise.reject(new Error(error?.response?.data?.message || error?.message || '请求失败'))
);

export const componentDefinitionApi = {
  page(params) {
    return api.get('/component-definition', { params });
  },
  create(data) {
    return api.post('/component-definition', data);
  },
  update(id, data) {
    return api.put(`/component-definition/${id}`, data);
  },
  remove(id) {
    return api.delete(`/component-definition/${id}`);
  },
  toggleStatus(id) {
    return api.put(`/component-definition/${id}/status`);
  }
};
