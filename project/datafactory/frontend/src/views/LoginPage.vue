<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>数据工厂</h1>
        <p>DataFactory 数据开发平台</p>
      </div>
      <a-form
        :model="form"
        :rules="rules"
        ref="formRef"
        @finish="handleLogin"
        layout="vertical"
        size="large"
      >
        <a-form-item name="username">
          <a-input v-model:value="form.username" placeholder="用户名" autocomplete="username">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password">
          <a-input-password v-model:value="form.password" placeholder="密码" autocomplete="current-password">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" block>
            登 录
          </a-button>
        </a-form-item>
      </a-form>
      <div v-if="errorMsg" class="login-error">{{ errorMsg }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { login } from '../api/authApi';
import { authStore } from '../store/auth';

const router = useRouter();
const formRef = ref();
const loading = ref(false);
const errorMsg = ref('');

const form = reactive({
  username: '',
  password: '',
});

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const res = await login(form.username, form.password);
    authStore.setAuth(res.data);
    message.success('登录成功');
    router.replace('/dashboard');
  } catch (e) {
    errorMsg.value = e.message || '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h1 {
  font-size: 28px;
  color: #1a1a2e;
  margin: 0 0 8px 0;
}
.login-header p {
  color: #888;
  margin: 0;
}
.login-error {
  color: #ff4d4f;
  text-align: center;
  margin-top: 8px;
}
</style>
