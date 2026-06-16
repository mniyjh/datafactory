<template>
  <div class="fp-container">
    <div class="fp-card">
      <h1 class="fp-title">忘记密码</h1>
      <p class="fp-subtitle">输入用户名和邮箱接收验证码</p>

      <a-form :model="form" :rules="rules" ref="formRef" layout="vertical" size="large">
        <a-form-item name="username">
          <a-input v-model:value="form.username" placeholder="用户名" />
        </a-form-item>
        <a-form-item name="email">
          <a-input v-model:value="form.email" placeholder="注册邮箱" />
        </a-form-item>

        <template v-if="step === 1">
          <a-button type="primary" :loading="sending" block class="fp-btn" @click="sendCode">
            {{ sending ? '发送中...' : '获取验证码' }}
          </a-button>
          <div v-if="countdown > 0" class="fp-hint">{{ countdown }}秒后可重新发送</div>
        </template>

        <template v-else>
          <a-form-item name="code">
            <a-input v-model:value="form.code" placeholder="6位验证码" maxlength="6" />
          </a-form-item>
          <a-button type="primary" :loading="resetting" block class="fp-btn" @click="doReset">
            {{ resetting ? '重置中...' : '重置密码为 123456' }}
          </a-button>
        </template>

        <div v-if="errorMsg" class="fp-error">{{ errorMsg }}</div>
        <div v-if="successMsg" class="fp-success">{{ successMsg }}</div>
      </a-form>

      <div class="fp-back">
        <router-link to="/login">← 返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { sendVerificationCode, resetPassword } from '../api/authApi';
import { message } from 'ant-design-vue';

const router = useRouter();

const formRef = ref();
const step = ref(1);
const sending = ref(false);
const resetting = ref(false);
const errorMsg = ref('');
const successMsg = ref('');
const countdown = ref(0);

const form = reactive({ username: '', email: '', code: '' });
const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  email: [{ required: true, type: 'email', message: '请输入正确的邮箱' }],
};

let cdTimer = null;
async function sendCode() {
  try { await formRef.value.validate(); } catch { return; }
  sending.value = true; errorMsg.value = ''; successMsg.value = '';
  try {
    await sendVerificationCode(form.username, form.email);
    step.value = 2;
    successMsg.value = '验证码已发送，请检查邮箱';
    countdown.value = 60;
    cdTimer = setInterval(() => { countdown.value--; if (countdown.value <= 0) clearInterval(cdTimer); }, 1000);
  } catch (e) { errorMsg.value = e.message || '发送失败'; }
  finally { sending.value = false; }
}

async function doReset() {
  if (!form.code || form.code.length !== 6) { errorMsg.value = '请输入6位验证码'; return; }
  resetting.value = true; errorMsg.value = '';
  try {
    await resetPassword(form.username, form.email, form.code);
    message.success('密码已重置为 123456');
    setTimeout(() => router.replace('/login'), 800);
  } catch (e) { errorMsg.value = e.message || '重置失败'; }
  finally { resetting.value = false; }
}
</script>

<style scoped>
.fp-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f5f5; }
.fp-card { width: 100%; max-width: 400px; background: #fff; border-radius: 12px; padding: 40px 36px; box-shadow: 0 4px 24px rgba(0,0,0,0.08); }
.fp-title { font-size: 24px; font-weight: 700; color: #111; text-align: center; margin: 0 0 8px; }
.fp-subtitle { font-size: 14px; color: #888; text-align: center; margin: 0 0 28px; }
.fp-btn { height: 44px; border-radius: 8px; background: #111; border-color: #111; }
.fp-btn:hover { background: #333; border-color: #333; }
.fp-hint { text-align: center; font-size: 12px; color: #999; margin-top: 8px; }
.fp-error { color: #dc2626; text-align: center; margin-top: 12px; font-size: 13px; }
.fp-success { color: #16a34a; text-align: center; margin-top: 12px; font-size: 13px; }
.fp-back { text-align: center; margin-top: 20px; }
.fp-back a { color: #666; font-size: 13px; text-decoration: none; }
.fp-back a:hover { color: #111; }
</style>
