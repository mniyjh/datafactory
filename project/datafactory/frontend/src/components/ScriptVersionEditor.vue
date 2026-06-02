<template>
  <a-modal :open="open" @update:open="(val) => { if (!val) emit('cancel'); }"
    :title="readonly ? '查看脚本版本配置' : `${environmentName}环境 - 版本编辑`" :width="1200" :footer="null" :centered="true"
    :maskClosable="false" :keyboard="false" destroyOnClose wrap-class-name="env-modal-fixed">
    <div class="editor-content-wrap">
      <div v-if="readonly" class="top-tip-readonly">当前为只读模式</div>
      <div class="editor-content">
        <div class="layout-left">
          <!-- 基本信息 -->
          <div class="section-card">
            <div class="section-title">基本信息</div>
            <a-form :model="formState" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="脚本编码">
                    <a-input :value="formState.code" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="脚本名称">
                    <a-input :value="formState.name" disabled />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="脚本语言">
                    <a-tag color="green">{{ formState.type }}</a-tag>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="变更说明" :required="!readonly">
                    <a-input v-model:value="formState.changeLog" placeholder="请输入本次变更说明" :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </div>

          <!-- 执行配置 -->
          <div class="section-card">
            <div class="section-title">执行配置</div>
            <a-form :model="formState" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="工作目录">
                    <a-input v-model:value="formState.workDir" placeholder="." :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="解释器路径">
                    <a-input v-model:value="formState.interpreterPath" placeholder="python"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="最大内存(MB)">
                    <a-input-number v-model:value="formState.maxMemory" style="width: 100%" :min="64" :max="4096"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="CPU限制">
                    <a-input-number v-model:value="formState.cpuLimit" style="width: 100%" :min="0.1" :max="4.0"
                      :step="0.1" :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </div>
        </div>

        <div class="layout-right">
          <!-- 脚本内容 -->
          <div class="section-card full-height">
            <div class="section-title">脚本内容</div>
            <a-form :model="formState" layout="vertical">
              <a-form-item label="脚本代码" required>
                <a-textarea v-model:value="formState.scriptCode" :rows="22" placeholder="请输入脚本代码" class="code-textarea"
                  :disabled="readonly" />
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="超时时间(秒)">
                    <a-input-number v-model:value="formState.timeout" style="width: 100%" :min="1"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="重试次数">
                    <a-input-number v-model:value="formState.retryCount" style="width: 100%" :min="0"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="依赖库">
                <a-textarea v-model:value="formState.dependencies" :rows="2" :disabled="readonly"
                  placeholder='JSON格式，例如：["requests==2.28.0", "pandas==1.5.0"]' />
              </a-form-item>

              <a-form-item label="环境变量">
                <a-textarea v-model:value="formState.envVars" :rows="2" :disabled="readonly"
                  placeholder='JSON格式，例如：{"PATH": "/usr/bin", "HOME": "/root"}' />
              </a-form-item>

              <a-form-item label="描述">
                <a-textarea v-model:value="formState.description" :rows="2" placeholder="版本描述信息" :disabled="readonly" />
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
      <div class="footer-actions">
        <a-button @click="emit('cancel')">{{ readonly ? '关闭' : '取消' }}</a-button>
        <a-button v-if="!readonly" type="primary" @click="handleSave">保存</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { reactive, onMounted, computed, watch } from 'vue';
import { message } from 'ant-design-vue';

const props = defineProps({
  open: Boolean,
  versionData: {
    type: Object,
    default: () => ({})
  },
  environment: {
    type: String,
    default: 'dev'
  },
  readonly: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['save', 'cancel']);

const environmentName = computed(() => {
  const map = { dev: '开发', test: '测试', prod: '生产' };
  return map[props.environment] || '开发';
});

const formState = reactive({
  id: null,
  code: '',
  name: '',
  type: 'PYTHON',
  changeLog: '',
  scriptCode: '',
  timeout: 30,
  retryCount: 0,
  dependencies: '[]',
  envVars: '{}',
  description: '',
  workDir: '/tmp',
  interpreterPath: 'python',
  maxMemory: 512,
  cpuLimit: 1.0
});

const initForm = () => {
  if (props.versionData) {
    Object.assign(formState, {
      id: props.versionData.id || null,
      code: props.versionData.code || '',
      name: props.versionData.name || '',
      type: props.versionData.type || 'PYTHON',
      changeLog: props.versionData.changeLog || props.versionData.remark || '',
      scriptCode: props.versionData.scriptCode || '',
      timeout: props.versionData.timeout || 30,
      retryCount: props.versionData.retryCount || 0,
      dependencies: props.versionData.dependencies || '[]',
      envVars: props.versionData.envVars || '{}',
      description: props.versionData.description || '',
      workDir: props.versionData.workDir || '.',
      interpreterPath: props.versionData.interpreterPath || 'python',
      maxMemory: props.versionData.maxMemory || 512,
      cpuLimit: props.versionData.cpuLimit || 1.0,
      version: props.versionData.version || ''
    });
  }
};

watch(() => props.open, (newVal) => {
  if (newVal) {
    initForm();
  }
}, { immediate: true });

const handleSave = () => {
  if (!formState.changeLog.trim()) {
    message.warning('请输入变更说明');
    return;
  }
  if (!formState.scriptCode.trim()) {
    message.warning('请输入脚本代码');
    return;
  }

  // 校验 JSON 格式
  try {
    if (formState.dependencies) JSON.parse(formState.dependencies);
    if (formState.envVars) JSON.parse(formState.envVars);
  } catch (e) {
    message.error('依赖库或环境变量格式错误，请检查是否为有效的JSON');
    return;
  }

  emit('save', { ...formState });
};
</script>

<style scoped>
.editor-content-wrap {
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}

.top-tip-readonly {
  padding: 8px 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  color: #faad14;
  border-radius: 4px;
  margin-bottom: 8px;
  font-size: 14px;
}

.editor-content {
  flex: 1;
  padding: 12px 0;
  display: flex;
  gap: 16px;
  overflow-y: auto;
}

.layout-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.layout-right {
  flex: 1;
  overflow-y: auto;
  max-height: calc(80vh - 150px);
}

.section-card {
  background: #fbfbfb;
  padding: 16px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.section-title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 16px;
  border-left: 4px solid #1890ff;
  padding-left: 10px;
  line-height: 1;
}

.full-height {
  height: 100%;
}

.code-textarea {
  font-family: 'Courier New', Courier, monospace;
}

.footer-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>
