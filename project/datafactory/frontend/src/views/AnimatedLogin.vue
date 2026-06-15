<template>
  <div class="container">
    <!-- ===== 左侧：暗色背景 + 角色居中 ===== -->
    <div class="leftPanel" @mousemove="onMouseMove">
      <div class="leftContent">
        <!-- 角色区域 — 容器 550x400, 垂直居中 -->
        <div ref="containerRef" class="stage">
          <!-- Purple — #6C3FF5, 180x400, left:70, z-1 -->
          <div ref="purpleRef" class="monster-purple">
            <div ref="purpleFaceRef" class="purple-face">
              <div class="eyeball" data-max-distance="5" :style="{ width:'18px', height:'18px' }">
                <div class="eyeball-pupil" :style="{ width:'7px', height:'7px', background:'#2D2D2D' }" />
              </div>
              <div class="eyeball" data-max-distance="5" :style="{ width:'18px', height:'18px' }">
                <div class="eyeball-pupil" :style="{ width:'7px', height:'7px', background:'#2D2D2D' }" />
              </div>
            </div>
          </div>

          <!-- Black — #2D2D2D, 120x310, left:240, z-2 -->
          <div ref="blackRef" class="monster-black">
            <div ref="blackFaceRef" class="black-face">
              <div class="eyeball" data-max-distance="4" :style="{ width:'16px', height:'16px' }">
                <div class="eyeball-pupil" :style="{ width:'6px', height:'6px', background:'#2D2D2D' }" />
              </div>
              <div class="eyeball" data-max-distance="4" :style="{ width:'16px', height:'16px' }">
                <div class="eyeball-pupil" :style="{ width:'6px', height:'6px', background:'#2D2D2D' }" />
              </div>
            </div>
          </div>

          <!-- Orange — #FF9B6B, 240x200, left:0, z-3 -->
          <div ref="orangeRef" class="monster-orange">
            <div ref="orangeFaceRef" class="orange-face">
              <div class="pupil" data-max-distance="5" :style="{ width:'12px', height:'12px', background:'#2D2D2D' }" />
              <div class="pupil" data-max-distance="5" :style="{ width:'12px', height:'12px', background:'#2D2D2D' }" />
            </div>
          </div>

          <!-- Yellow — #E8D754, 140x230, left:310, z-4 -->
          <div ref="yellowRef" class="monster-yellow">
            <div ref="yellowFaceRef" class="yellow-face">
              <div class="pupil" data-max-distance="5" :style="{ width:'12px', height:'12px', background:'#2D2D2D' }" />
              <div class="pupil" data-max-distance="5" :style="{ width:'12px', height:'12px', background:'#2D2D2D' }" />
            </div>
            <div ref="yellowMouthRef" class="yellow-mouth" />
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 右侧：暗色背景 + 白色文字表单 ===== -->
    <div class="rightPanel">
      <div class="formWrapper">
        <!-- 品牌名 -->
        <div class="brandName">DataFactory</div>

        <div class="formHeader">
          <h1 class="formTitle">欢迎回来</h1>
          <p class="formSubtitle">请输入账号密码登录系统</p>
        </div>

        <a-form :model="form" :rules="rules" ref="formRef" @finish="handleLogin" size="large" class="form">
          <label class="fieldLabel">用户名</label>
          <a-form-item name="username">
            <a-input
              v-model:value="form.username"
              placeholder="输入您的用户名"
              @focus="onTypeStart"
              @blur="onTypeEnd"
            />
          </a-form-item>

          <label class="fieldLabel">密码</label>
          <a-form-item name="password">
            <a-input
              v-model:value="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="••••••••"
              @input="onPasswordInput"
            />
          </a-form-item>

          <div v-if="errorMsg" class="errorBox">{{ errorMsg }}</div>

          <a-form-item style="margin-bottom: 0">
            <a-button
              type="primary"
              html-type="submit"
              :loading="loading"
              block
              class="submitBtn"
            >
              {{ loading ? '登录中...' : '登录' }}
            </a-button>
          </a-form-item>
        </a-form>

        <div class="fp-link">
          <router-link to="/forgot-password">忘记密码？</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import gsap from 'gsap';
import { login } from '../api/authApi';
import { authStore } from '../store/auth';

const router = useRouter();
const formRef = ref();
const loading = ref(false);
const errorMsg = ref('');
const showPassword = ref(false);
const form = reactive({ username: '', password: '' });
const passwordLength = ref(0);
const isTyping = ref(false);

const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
};

// ─── DOM refs ───
const containerRef = ref(null);
const purpleRef = ref(null), blackRef = ref(null), orangeRef = ref(null), yellowRef = ref(null);
const purpleFaceRef = ref(null), blackFaceRef = ref(null), orangeFaceRef = ref(null), yellowFaceRef = ref(null);
const yellowMouthRef = ref(null);

// ─── Mouse ───
const mouse = { x: 0, y: 0 };
let rafId = 0;

// ─── GSAP quickTo ───
let qpSkew, qbSkew, qoSkew, qySkew;
let qpX, qbX, qpHeight;
let qpFaceL, qpFaceT, qbFaceL, qbFaceT;
let qoFaceX, qoFaceY, qyFaceX, qyFaceY;
let qMouthX, qMouthY;

// ─── State ───
let isLooking = false;
const state = { isTyping: false, isHidingPassword: false, isShowingPassword: false, isLooking: false };
let purpleBlinkT, blackBlinkT, purplePeekT, lookingT;

// ═══ 原版计算函数 ═══
function calcPos(el) {
  const r = el.getBoundingClientRect();
  const cx = r.left + r.width / 2;
  const cy = r.top + r.height / 3;
  const dx = mouse.x - cx, dy = mouse.y - cy;
  return {
    faceX: Math.max(-15, Math.min(15, dx / 20)),
    faceY: Math.max(-10, Math.min(10, dy / 30)),
    bodySkew: Math.max(-6, Math.min(6, -dx / 120)),
  };
}

function calcEyePos(el, maxDist) {
  const r = el.getBoundingClientRect();
  const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
  const dx = mouse.x - cx, dy = mouse.y - cy;
  const dist = Math.min(Math.sqrt(dx ** 2 + dy ** 2), maxDist);
  const angle = Math.atan2(dy, dx);
  return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist };
}

// ═══ 主循环 ═══
function tick() {
  const c = containerRef.value;
  if (!c) return;
  const { isTyping: t, isHidingPassword: h, isShowingPassword: s, isLooking: l } = state;

  // Purple
  if (purpleRef.value && !s) {
    const p = calcPos(purpleRef.value);
    if (t || h) { qpSkew(p.bodySkew - 12); qpX(40); qpHeight(440); }
    else { qpSkew(p.bodySkew); qpX(0); qpHeight(400); }
  }
  // Black
  if (blackRef.value && !s) {
    const p = calcPos(blackRef.value);
    if (l) { qbSkew(p.bodySkew * 1.5 + 4); qbX(5); }
    else if (t || h) { qbSkew(p.bodySkew * 1.5); qbX(0); }
    else { qbSkew(p.bodySkew); qbX(0); }
  }
  // Orange
  if (orangeRef.value && !s) { qoSkew(calcPos(orangeRef.value).bodySkew); }
  // Yellow
  if (yellowRef.value && !s) { qySkew(calcPos(yellowRef.value).bodySkew); }

  // Purple face (not looking)
  if (purpleRef.value && !s && !l) {
    const p = calcPos(purpleRef.value);
    qpFaceL(45 + (p.faceX >= 0 ? Math.min(25, p.faceX * 1.5) : p.faceX));
    qpFaceT(40 + p.faceY);
  }
  // Black face (not looking)
  if (blackRef.value && !s && !l) {
    const p = calcPos(blackRef.value);
    qbFaceL(26 + p.faceX); qbFaceT(32 + p.faceY);
  }
  // Orange face (x/y = translate)
  if (orangeRef.value && !s) { const p = calcPos(orangeRef.value); qoFaceX(p.faceX); qoFaceY(p.faceY); }
  // Yellow face
  if (yellowRef.value && !s) { const p = calcPos(yellowRef.value); qyFaceX(p.faceX); qyFaceY(p.faceY); }
  // Yellow mouth
  if (yellowRef.value && !s) { const p = calcPos(yellowRef.value); qMouthX(p.faceX); qMouthY(p.faceY); }

  // Pupils
  if (!s) {
    c.querySelectorAll('.pupil').forEach(el => { const e = calcEyePos(el, Number(el.dataset.maxDistance) || 5); gsap.set(el, { x: e.x, y: e.y }); });
    if (!l) {
      c.querySelectorAll('.eyeball').forEach(el => {
        const p = el.querySelector('.eyeball-pupil'); if (!p) return;
        const e = calcEyePos(el, Number(el.dataset.maxDistance) || 10); gsap.set(p, { x: e.x, y: e.y });
      });
    }
  }

  rafId = requestAnimationFrame(tick);
}

// ═══ 状态变更 ═══
function applyLookAtEachOther() {
  qpFaceL?.(55); qpFaceT?.(65); qbFaceL?.(32); qbFaceT?.(12);
  purpleRef.value?.querySelectorAll('.eyeball-pupil').forEach(p => gsap.to(p, { x: 3, y: 4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
  blackRef.value?.querySelectorAll('.eyeball-pupil').forEach(p => gsap.to(p, { x: 0, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
}

function applyHidingPassword() { qpFaceL?.(55); qpFaceT?.(65); }

function applyShowPassword() {
  qpSkew?.(0); qbSkew?.(0); qoSkew?.(0); qySkew?.(0);
  qpX?.(0); qbX?.(0); qpHeight?.(400);
  qpFaceL?.(20); qpFaceT?.(35);
  qbFaceL?.(10); qbFaceT?.(28);
  qoFaceX?.(-32); qoFaceY?.(-5);
  qyFaceX?.(-32); qyFaceY?.(-5);
  qMouthX?.(-30); qMouthY?.(0);
  purpleRef.value?.querySelectorAll('.eyeball-pupil').forEach(p => gsap.to(p, { x: -4, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
  blackRef.value?.querySelectorAll('.eyeball-pupil').forEach(p => gsap.to(p, { x: -4, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
  orangeRef.value?.querySelectorAll('.pupil').forEach(p => gsap.to(p, { x: -5, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
  yellowRef.value?.querySelectorAll('.pupil').forEach(p => gsap.to(p, { x: -5, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
}

// ═══ Blink ═══
function blinkThen(el, size, next) {
  el.forEach(e => gsap.to(e, { height: 2, duration: 0.08, ease: 'power2.in' }));
  setTimeout(() => { el.forEach(e => gsap.to(e, { height: size, duration: 0.08, ease: 'power2.out' })); next(); }, 150);
}
function schedulePurpleBlink() {
  const el = purpleRef.value?.querySelectorAll('.eyeball');
  if (!el?.length) return;
  purpleBlinkT = setTimeout(() => {
    blinkThen(el, 18, () => schedulePurpleBlink());
  }, Math.random() * 4000 + 3000);
}
function scheduleBlackBlink() {
  const el = blackRef.value?.querySelectorAll('.eyeball');
  if (!el?.length) return;
  blackBlinkT = setTimeout(() => {
    blinkThen(el, 16, () => scheduleBlackBlink());
  }, Math.random() * 4000 + 3000);
}

// ═══ Peek (密码可见时) ═══
function schedulePeek() {
  const p = purpleRef.value?.querySelectorAll('.eyeball-pupil'); if (!p?.length) return;
  purplePeekT = setTimeout(() => {
    p.forEach(e => gsap.to(e, { x: 4, y: 5, duration: 0.3, ease: 'power2.out', overwrite: 'auto' }));
    qpFaceL?.(20); qpFaceT?.(35);
    setTimeout(() => { p.forEach(e => gsap.to(e, { x: -4, y: -4, duration: 0.3, ease: 'power2.out', overwrite: 'auto' })); schedulePeek(); }, 800);
  }, Math.random() * 3000 + 2000);
}

// ═══ Input ═══
function onTypeStart() {
  isTyping.value = true;
  if (!showPassword.value) {
    isLooking = true; state.isLooking = true;
    applyLookAtEachOther();
    clearTimeout(lookingT);
    lookingT = setTimeout(() => {
      isLooking = false; state.isLooking = false;
      purpleRef.value?.querySelectorAll('.eyeball-pupil').forEach(p => gsap.killTweensOf(p));
    }, 800);
  }
}
function onTypeEnd() { isTyping.value = false; }
function onPasswordInput(e) { passwordLength.value = e.target.value?.length || 0; }
function onMouseMove(e) { mouse.x = e.clientX; mouse.y = e.clientY; }

// ═══ State watcher ═══
let prevH = false, prevS = false;
function watchState() {
  const h = passwordLength.value > 0 && !showPassword.value;
  const s = passwordLength.value > 0 && showPassword.value;
  state.isHidingPassword = h; state.isShowingPassword = s;
  if (s && passwordLength.value > 0) { if (!prevS) { applyShowPassword(); schedulePeek(); } }
  else { clearTimeout(purplePeekT); }
  if (h && !prevH) { applyHidingPassword(); }
  prevH = h; prevS = s;
  requestAnimationFrame(watchState);
}

// ═══ Login ═══
async function handleLogin() {
  loading.value = true; errorMsg.value = '';
  try {
    const res = await login(form.username, form.password);
    authStore.setAuth(res.data);
    message.success('登录成功');
    setTimeout(() => router.replace('/dashboard'), 400);
  } catch (e) {
    errorMsg.value = e.message || '账号或密码有误，请重新输入';
  } finally { loading.value = false; }
}

// ═══ Lifecycle ═══
onMounted(() => nextTick(() => {
  if (!purpleRef.value || !blackRef.value || !orangeRef.value || !yellowRef.value) return;
  if (!purpleFaceRef.value || !blackFaceRef.value || !orangeFaceRef.value || !yellowFaceRef.value || !yellowMouthRef.value) return;

  qpSkew = gsap.quickTo(purpleRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' });
  qbSkew = gsap.quickTo(blackRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' });
  qoSkew = gsap.quickTo(orangeRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' });
  qySkew = gsap.quickTo(yellowRef.value, 'skewX', { duration: 0.3, ease: 'power2.out' });
  qpX = gsap.quickTo(purpleRef.value, 'x', { duration: 0.3, ease: 'power2.out' });
  qbX = gsap.quickTo(blackRef.value, 'x', { duration: 0.3, ease: 'power2.out' });
  qpHeight = gsap.quickTo(purpleRef.value, 'height', { duration: 0.3, ease: 'power2.out' });
  qpFaceL = gsap.quickTo(purpleFaceRef.value, 'left', { duration: 0.3, ease: 'power2.out' });
  qpFaceT = gsap.quickTo(purpleFaceRef.value, 'top', { duration: 0.3, ease: 'power2.out' });
  qbFaceL = gsap.quickTo(blackFaceRef.value, 'left', { duration: 0.3, ease: 'power2.out' });
  qbFaceT = gsap.quickTo(blackFaceRef.value, 'top', { duration: 0.3, ease: 'power2.out' });
  qoFaceX = gsap.quickTo(orangeFaceRef.value, 'x', { duration: 0.2, ease: 'power2.out' });
  qoFaceY = gsap.quickTo(orangeFaceRef.value, 'y', { duration: 0.2, ease: 'power2.out' });
  qyFaceX = gsap.quickTo(yellowFaceRef.value, 'x', { duration: 0.2, ease: 'power2.out' });
  qyFaceY = gsap.quickTo(yellowFaceRef.value, 'y', { duration: 0.2, ease: 'power2.out' });
  qMouthX = gsap.quickTo(yellowMouthRef.value, 'x', { duration: 0.2, ease: 'power2.out' });
  qMouthY = gsap.quickTo(yellowMouthRef.value, 'y', { duration: 0.2, ease: 'power2.out' });

  gsap.set('.pupil', { x: 0, y: 0 });
  gsap.set('.eyeball-pupil', { x: 0, y: 0 });

  const r = document.querySelector('.leftPanel')?.getBoundingClientRect();
  if (r) { mouse.x = r.left + r.width / 2; mouse.y = r.top + r.height * 0.35; }

  rafId = requestAnimationFrame(tick);
  schedulePurpleBlink();
  scheduleBlackBlink();
  requestAnimationFrame(watchState);
}));

onUnmounted(() => {
  cancelAnimationFrame(rafId);
  clearTimeout(purpleBlinkT); clearTimeout(blackBlinkT);
  clearTimeout(purplePeekT); clearTimeout(lookingT);
});
</script>

<style scoped>
/* ═══ Layout ═══ */
.container {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
}
@media (max-width: 1024px) { .container { grid-template-columns: 1fr; } }

/* ─── 左侧 — 暗色背景, 角色居中 ─── */
.leftPanel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: oklch(0.145 0 0);
  overflow: hidden;
  cursor: default;
}
@media (max-width: 1024px) { .leftPanel { display: none; } }

.leftContent {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

/* Stage: 550x400 容器 */
.stage {
  position: relative;
  width: 550px;
  height: 400px;
}

/* ─── 右侧 — 暗色背景, 白色文字 ─── */
.rightPanel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: oklch(0.145 0 0);
}

.formWrapper {
  width: 100%;
  max-width: 400px;
}

.brandName {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: oklch(0.985 0 0);
  margin-bottom: 48px;
}

.formHeader { margin-bottom: 32px; }
.formTitle {
  font-size: 30px;
  font-weight: 700;
  color: oklch(0.985 0 0);
  margin: 0 0 8px 0;
  line-height: 1.2;
}
.formSubtitle {
  font-size: 14px;
  color: oklch(0.708 0 0);
  margin: 0;
}

/* ═══ 表单 — CDN dark theme 风格 ═══ */
.fieldLabel {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: oklch(0.985 0 0);
  margin-bottom: 6px;
}

.form :deep(.ant-form-item) { margin-bottom: 20px; }

.form :deep(.ant-input-affix-wrapper),
.form :deep(.ant-input) {
  height: 48px !important;
  background: oklch(0.145 0 0) !important;
  border: 1px solid oklab(1 0 0 / 0.06) !important;
  border-radius: 8px !important;
  color: oklch(0.985 0 0) !important;
  font-size: 14px !important;
  transition: border-color 0.2s, box-shadow 0.2s !important;
}
.form :deep(.ant-input-affix-wrapper:hover) {
  border-color: oklab(1 0 0 / 0.15) !important;
}
.form :deep(.ant-input-affix-wrapper:focus),
.form :deep(.ant-input-affix-wrapper-focused) {
  border-color: oklch(0.623 0.214 259.815) !important; /* blue */
  box-shadow: 0 0 0 3px oklch(0.623 0.214 259.815 / 0.15) !important;
}
.form :deep(.ant-input) { background: transparent !important; }
.form :deep(.ant-input::placeholder) { color: oklch(0.552 0 0) !important; }
.form :deep(.ant-input-prefix) { color: oklch(0.552 0 0) !important; }
.form :deep(.ant-input-suffix) { color: oklch(0.552 0 0) !important; }

.errorBox {
  padding: 10px 14px;
  font-size: 13px;
  color: #fca5a5;
  background: rgba(220, 38, 38, 0.1);
  border: 1px solid rgba(220, 38, 38, 0.25);
  border-radius: 8px;
  margin-bottom: 16px;
}

/* ═══ 按钮 — 白色背景/暗色文字（黑白对比） ═══ */
.submitBtn {
  height: 48px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: 8px !important;
  background: oklch(0.922 0 0) !important;
  border-color: oklch(0.922 0 0) !important;
  color: oklch(0.205 0 0) !important;
  letter-spacing: 1px;
  transition: background 0.2s, opacity 0.2s !important;
}
.submitBtn:hover { background: oklch(0.89 0 0) !important; border-color: oklch(0.89 0 0) !important; }
.submitBtn:active { opacity: 0.85 !important; }

.fp-link { text-align: center; margin-top: 16px; }
.fp-link a { color: rgba(255,255,255,0.5); font-size: 13px; text-decoration: none; }
.fp-link a:hover { color: rgba(255,255,255,0.8); }

/* ═══ 角色样式 (100% 原版) ═══ */
.monster-purple {
  position: absolute; bottom: 0; left: 70px;
  width: 180px; height: 400px;
  background: #6C3FF5;
  border-radius: 10px 10px 0 0;
  z-index: 1;
  transform-origin: bottom center;
  will-change: transform;
}
.purple-face { position: absolute; display: flex; gap: 32px; left: 45px; top: 40px; }

.monster-black {
  position: absolute; bottom: 0; left: 240px;
  width: 120px; height: 310px;
  background: #2D2D2D;
  border-radius: 8px 8px 0 0;
  z-index: 2;
  transform-origin: bottom center;
  will-change: transform;
}
.black-face { position: absolute; display: flex; gap: 24px; left: 26px; top: 32px; }

.monster-orange {
  position: absolute; bottom: 0; left: 0;
  width: 240px; height: 200px;
  background: #FF9B6B;
  border-radius: 120px 120px 0 0;
  z-index: 3;
  transform-origin: bottom center;
  will-change: transform;
}
.orange-face { position: absolute; display: flex; gap: 32px; left: 82px; top: 90px; }

.monster-yellow {
  position: absolute; bottom: 0; left: 310px;
  width: 140px; height: 230px;
  background: #E8D754;
  border-radius: 70px 70px 0 0;
  z-index: 4;
  transform-origin: bottom center;
  will-change: transform;
}
.yellow-face { position: absolute; display: flex; gap: 24px; left: 52px; top: 40px; }
.yellow-mouth {
  position: absolute;
  width: 80px; height: 4px;
  background: #2D2D2D;
  border-radius: 9999px;
  left: 40px; top: 88px;
}

.pupil {
  border-radius: 50%;
}
.eyeball {
  border-radius: 50%;
  background: white;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.eyeball-pupil { border-radius: 50%; }
</style>
