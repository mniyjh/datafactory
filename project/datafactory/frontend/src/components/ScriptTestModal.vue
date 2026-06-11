<template>
  <div ref="root"><a-modal :getContainer="() => root" :zIndex="1050" :open="open" @update:open="(val) => { if (!val) emit('update:open', false); }"
    title="脚本测试" :width="1100" :footer="null" :centered="true"
    :maskClosable="false" :keyboard="false" destroyOnClose>
    <div class="test-modal-layout">
      <div class="test-left">
        <div class="section-card">
          <div class="section-title">脚本信息</div>
          <a-descriptions :column="2" size="small" bordered>
            <a-descriptions-item label="脚本编码">{{ versionData?.code || '-' }}</a-descriptions-item>
            <a-descriptions-item label="脚本名称">{{ versionData?.name || '-' }}</a-descriptions-item>
            <a-descriptions-item label="脚本语言">
              <a-tag :color="langColor(versionData?.type)">{{ versionData?.type || '-' }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="版本">{{ versionData?.version || '-' }}</a-descriptions-item>
          </a-descriptions>
        </div>
        <div class="section-card">
          <div class="section-title">输入参数（可选）</div>
          <a-textarea v-model:value="inputJson" :rows="6" placeholder='JSON 格式，如 {"dbEmployees": [...], "apiUsers": [...]}'
            :disabled="executing" style="font-family: monospace;" />
          <a-button size="small" style="margin-top:6px" @click="formatInput" :disabled="executing">格式化</a-button>
        </div>
      </div>
      <div class="test-right">
        <div class="section-card full-height">
          <div class="section-title">执行结果</div>
          <div v-if="!executed && !executing" class="placeholder">点击「执行」按钮开始测试</div>

          <div v-if="executing" class="status-banner running">
            <LoadingOutlined spin /> {{ scriptTypeLabel }}脚本执行中...
          </div>

          <div v-if="executed && !executing" class="status-banner" :class="resultSuccess ? 'success' : 'failure'">
            {{ resultSuccess ? '执行成功' : '执行失败' }}
            <span v-if="resultData?.exitCode !== undefined"> &nbsp;|&nbsp; Exit Code: {{ resultData.exitCode }}</span>
          </div>

          <div v-if="executed || executing" class="result-section">
            <div class="result-label">标准输出 (stdout)</div>
            <div class="output-box"><pre>{{ resultData?.stdout || resultData?.output || '(无输出)' }}</pre></div>
          </div>

          <div v-if="executed" class="result-section">
            <div class="result-label error-label">标准错误 (stderr)</div>
            <div class="output-box error-box"><pre>{{ resultData?.stderr || resultData?.error || '(无)' }}</pre></div>
          </div>
        </div>
      </div>
    </div>
    <div class="footer-actions">
      <a-button @click="emit('update:open', false)">{{ executed ? '关闭' : '取消' }}</a-button>
      <a-button type="primary" @click="handleExecute" :loading="executing">执行</a-button>
    </div>
  </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { LoadingOutlined } from '@ant-design/icons-vue';
import { scriptApi } from '../api/scriptApi';
const root = ref(null);

const props = defineProps({
  open: Boolean,
  versionData: { type: Object, default: () => ({}) }
});

const emit = defineEmits(['update:open']);

const inputJson = ref('');
const executing = ref(false);
const executed = ref(false);
const resultSuccess = ref(false);
const resultData = ref({});

const langColor = (t) => {
  const map = { PYTHON: 'green', SHELL: 'blue', SQL: 'orange' };
  return map[(t || '').toUpperCase()] || 'default';
};

const scriptTypeLabel = computed(() => {
  const t = (props.versionData?.type || '').toUpperCase();
  if (t === 'SQL') return 'SQL';
  if (t === 'SHELL') return 'Shell';
  return 'Python';
});

const formatInput = () => {
  try {
    const obj = JSON.parse(inputJson.value);
    inputJson.value = JSON.stringify(obj, null, 2);
  } catch (_) {
    message.warning('JSON 格式不正确');
  }
};

const handleExecute = async () => {
  executing.value = true;
  executed.value = false;
  resultData.value = {};
  try {
    const res = await scriptApi.testVersion(props.versionData.id, inputJson.value);
    const data = res?.data?.data || {};
    resultData.value = data;
    resultSuccess.value = data.success === true || data.exitCode === 0;
    executed.value = true;
    if (resultSuccess.value) {
      message.success(`${scriptTypeLabel.value}脚本执行完成`);
    } else {
      message.error(data.error || data.stderr || `${scriptTypeLabel.value}脚本执行返回非零退出码`);
    }
  } catch (e) {
    resultData.value = { error: e.message };
    resultSuccess.value = false;
    executed.value = true;
    message.error(`执行失败：${e.message}`);
  } finally {
    executing.value = false;
  }
};

watch(() => props.open, (val) => {
  if (val) {
    inputJson.value = '';
    executed.value = false;
    resultSuccess.value = false;
    resultData.value = {};
  }
});
</script>

<style scoped>
.test-modal-layout { display: flex; gap: 16px; min-height: 420px; }
.test-left { flex: 1; display: flex; flex-direction: column; gap: 16px; }
.test-right { flex: 1; }
.section-card { background: #fbfbfb; padding: 16px; border-radius: 4px; border: 1px solid #f0f0f0; }
.section-title { font-size: 15px; font-weight: 500; margin-bottom: 12px; border-left: 4px solid #1890ff; padding-left: 10px; line-height: 1; }
.full-height { height: 100%; display: flex; flex-direction: column; }
.placeholder { color: #bbb; text-align: center; padding: 60px 0; font-size: 14px; }

.status-banner { padding: 10px 16px; border-radius: 4px; font-size: 14px; font-weight: 500; margin-bottom: 12px; }
.status-banner.running { background: #e6f7ff; border: 1px solid #91d5ff; color: #1890ff; }
.status-banner.success { background: #f6ffed; border: 1px solid #b7eb8f; color: #52c41a; }
.status-banner.failure { background: #fff2f0; border: 1px solid #ffccc7; color: #ff4d4f; }

.result-section { margin-bottom: 10px; }
.result-label { font-size: 13px; font-weight: 500; margin-bottom: 4px; color: #333; }
.result-label.error-label { color: #ff4d4f; }
.output-box { background: #1e1e1e; border-radius: 4px; padding: 10px 14px; max-height: 180px; overflow: auto; }
.output-box pre { color: #d4d4d4; font-family: 'Courier New', monospace; font-size: 13px; line-height: 1.6; margin: 0; white-space: pre-wrap; word-break: break-all; }
.output-box.error-box { max-height: 120px; }
.output-box.error-box pre { color: #f48771; }

.footer-actions { margin-top: 16px; display: flex; justify-content: flex-end; gap: 12px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
</style>
