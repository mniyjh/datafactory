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

export function sendVerificationCode(username, email) {
  return api.post('/auth/forgot-password/send-code', { username, email });
}
export function resetPassword(username, email, code) {
  return api.post('/auth/forgot-password/reset', { username, email, code });
}
export function checkForgotPasswordAvailable(username) {
  return api.get('/auth/forgot-password/check', { params: { username } });
}
