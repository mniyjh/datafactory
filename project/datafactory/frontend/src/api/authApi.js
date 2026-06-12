import api from './index';

export function login(username, password) {
  return api.post('/auth/login', { username, password });
}

export function refreshToken(token) {
  return api.post('/auth/refresh', { refreshToken: token });
}

export function logout() {
  return api.post('/auth/logout');
}
