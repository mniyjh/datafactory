<template>
  <a-modal :open="open" @update:open="(val) => { if (!val) emit('close'); }" :width="1360" :footer="null"
    :centered="true" :maskClosable="false" :keyboard="false" destroyOnClose wrap-class-name="env-modal-fixed">
    <template #title>
      <div class="modal-title">
        <arrow-left-outlined class="back-icon" @click="emit('close')" />
        <span>{{ envName }} - {{ readonly ? '查看API版本配置' : '开发环境 - 版本编辑' }}</span>
      </div>
    </template>
    <div class="editor-wrap">
      <div class="top-bar">
        <div class="top-tip">{{ readonly ? '当前为只读模式' : '版本号将由系统自动生成' }}</div>
      </div>

      <div class="editor-body">
        <a-row :gutter="16">
          <!-- 左侧配置 -->
          <a-col :span="14">
            <a-card title="基本信息" size="small" class="config-card">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="API编码">
                    <a-input v-model:value="formState.code" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="API名称">
                    <a-input v-model:value="formState.name" disabled />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="API类型">
                    <a-input v-model:value="formState.type" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="变更说明" :required="!readonly">
                    <a-input v-model:value="formState.changeLog" placeholder="请输入本次变更说明" :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-card>

            <a-card title="请求配置" size="small" class="config-card" style="margin-top: 16px;">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="请求方法" required>
                    <a-select v-model:value="formState.method" :options="methodOptions" :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="Content-Type" required>
                    <a-select v-model:value="formState.contentType" :options="contentTypeOptions"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="URL地址" required>
                <a-input v-model:value="formState.url" placeholder="例如：https://api.example.com/v1/users"
                  :disabled="readonly" />
              </a-form-item>
              <a-form-item label="请求头">
                <a-textarea v-model:value="formState.headers" :rows="3" :disabled="readonly"
                  placeholder='JSON格式，例如：{"Authorization": "Bearer token", "X-Custom-Header": "value"}' />
              </a-form-item>
              <a-form-item label="查询参数">
                <a-textarea v-model:value="formState.queryParams" :rows="3" :disabled="readonly"
                  placeholder='JSON格式，例如：{"page": 1, "size": 10}' />
              </a-form-item>
              <a-form-item label="请求体">
                <a-textarea v-model:value="formState.body" :rows="5" placeholder="JSON格式的请求体（POST/PUT时使用）"
                  :disabled="readonly" />
              </a-form-item>
            </a-card>
          </a-col>

          <!-- 右侧配置 -->
          <a-col :span="10">
            <a-card title="认证配置" size="small" class="config-card">
              <a-form-item label="认证类型">
                <a-select v-model:value="formState.authType" :options="authTypeOptions" :disabled="readonly" />
              </a-form-item>
              <a-form-item label="认证信息">
                <a-input v-model:value="formState.authInfo" placeholder="根据认证类型填写" :disabled="readonly" />
              </a-form-item>
            </a-card>

            <a-card title="超时与重试" size="small" class="config-card" style="margin-top: 16px;">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="连接超时(秒)">
                    <a-input-number v-model:value="formState.connectTimeout" :min="1" style="width: 100%"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="读取超时(秒)">
                    <a-input-number v-model:value="formState.readTimeout" :min="1" style="width: 100%"
                      :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="重试次数">
                <a-input-number v-model:value="formState.retryCount" :min="0" style="width: 100%"
                  :disabled="readonly" />
              </a-form-item>
            </a-card>

            <a-card title="其他配置" size="small" class="config-card" style="margin-top: 16px;">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="跟随重定向">
                    <a-switch v-model:checked="formState.followRedirects" :disabled="readonly" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="验证SSL证书">
                    <a-switch v-model:checked="formState.verifySsl" :disabled="readonly" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="描述">
                <a-textarea v-model:value="formState.description" :rows="4" placeholder="版本描述信息" :disabled="readonly" />
              </a-form-item>
            </a-card>
          </a-col>
        </a-row>
      </div>

      <div class="footer-actions">
        <a-button @click="emit('close')">{{ readonly ? '关闭' : '取消' }}</a-button>
        <a-button v-if="!readonly" type="primary" @click="handleSave">保存</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { reactive, watch } from 'vue';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined } from '@ant-design/icons-vue';

const props = defineProps({
  open: Boolean,
  envName: String,
  initialData: {
    type: Object,
    default: () => ({})
  },
  readonly: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['close', 'save']);

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' }
];

const contentTypeOptions = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
  { label: 'text/xml', value: 'text/xml' }
];

const authTypeOptions = [
  { label: '无认证', value: 'None' },
  { label: 'Basic Auth', value: 'Basic' },
  { label: 'Bearer Token', value: 'Bearer' },
  { label: 'API Key', value: 'ApiKey' }
];

const formState = reactive({
  code: '',
  name: '',
  type: '',
  changeLog: '',
  method: 'GET',
  contentType: 'application/json',
  url: '',
  headers: '',
  queryParams: '',
  body: '',
  authType: 'None',
  authInfo: '',
  connectTimeout: 30,
  readTimeout: 30,
  retryCount: 0,
  followRedirects: true,
  verifySsl: true,
  description: ''
});

watch(() => props.open, (newVal) => {
  if (newVal) {
    const data = props.initialData || {};
    let parsedDsl = {};

    try {
      const dslSource = data.dslContent || data.dsl;
      if (dslSource && typeof dslSource === 'string') {
        parsedDsl = JSON.parse(dslSource);
      } else if (dslSource && typeof dslSource === 'object') {
        parsedDsl = dslSource;
      }
    } catch (e) {
      parsedDsl = {};
    }

    Object.assign(formState, {
      code: data.code || data.apiCode || data.name || '',
      name: data.name || data.apiName || '',
      type: data.type || data.apiType || 'REST',
      changeLog: data.changeLog || data.remark || '',
      method: data.method || data.requestMethod || parsedDsl.method || 'GET',
      contentType: data.contentType || parsedDsl.contentType || 'application/json',
      url: data.url || data.requestUrl || parsedDsl.url || '',
      headers: data.headers || data.requestHeaders || parsedDsl.headers || '',
      queryParams: data.queryParams || parsedDsl.queryParams || '',
      body: data.body || data.requestBody || parsedDsl.body || '',
      authType: data.authType || parsedDsl.authType || 'None',
      authInfo: data.authInfo || data.authConfig || parsedDsl.authInfo || '',
      connectTimeout: data.connectTimeout || data.timeout || parsedDsl.connectTimeout || 30,
      readTimeout: data.readTimeout || data.timeout || parsedDsl.readTimeout || 30,
      retryCount: data.retryCount || parsedDsl.retryCount || 0,
      followRedirects: data.followRedirects !== false,
      verifySsl: data.verifySsl !== false,
      description: data.description || data.remark || ''
    });
  }
});

const handleSave = () => {
  if (!formState.changeLog) {
    message.warning('请输入变更说明');
    return;
  }
  if (!formState.url) {
    message.warning('请输入URL地址');
    return;
  }
  emit('save', { ...formState });
};
</script>

<style scoped>
.modal-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-icon {
  cursor: pointer;
  font-size: 16px;
  color: #666;
  transition: color 0.3s;
}

.back-icon:hover {
  color: #1890ff;
}

.editor-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.top-bar {
  padding: 8px 16px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  margin-bottom: 16px;
}

.top-tip {
  color: #1890ff;
  font-size: 14px;
}

.editor-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.config-card {
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.footer-actions {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 0 0;
  border-top: 1px solid #f0f0f0;
}

:deep(.ant-card-head-title) {
  font-weight: 600;
}

:deep(.ant-form-item) {
  margin-bottom: 12px;
}
</style>
