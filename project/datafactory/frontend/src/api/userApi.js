import api from './index';

export function pageUsers(params) {
  return api.get('/users', { params });
}

export function getUserById(id) {
  return api.get(`/users/${id}`);
}

export function createUser(data) {
  return api.post('/users', data);
}

export function updateUser(id, data) {
  return api.put(`/users/${id}`, data);
}

export function deleteUser(id) {
  return api.delete(`/users/${id}`);
}

export function getCurrentUser() {
  return api.get('/users/current');
}

export function changePassword(data) {
  return api.put('/users/current/password', data);
}

export function getRoles() {
  return api.get('/roles');
}
