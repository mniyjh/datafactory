<template>
  <div class="page-wrap">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入API名称和编码" />
      <a-button type="primary" @click="loadData">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建API</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :pagination="{ pageSize: 10 }"
      row-key="id"
      size="middle"
      :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag color="success" v-if="record.status === '启用'">启用</a-tag>
          <a-tag color="default" v-else>禁用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" @click="openDetail(record)">查看</a-button>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" type="primary" ghost @click="openEnvModal(record, 'dev')">环境</a-button>
            <a-button size="small" @click="toggleStatus(record)">
              {{ record.status === '启用' ? '禁用' : '启用' }}
            </a-button>
            <a-popconfirm title="确定删除该API吗？" ok-text="确定" cancel-text="取消" @confirm="deleteApi(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑API' : '新建API'" :width="760" :footer="null" destroyOnClose>
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '130px' } }" class="api-form">
        <a-form-item label="API编码" required>
          <a-input v-model:value="formState.code" :disabled="isEdit" placeholder="例如：API_WEATHER_001" />
        </a-form-item>

        <a-form-item label="API名称" required>
          <a-input v-model:value="formState.name" placeholder="例如：天气查询API" />
        </a-form-item>

        <a-form-item label="API类型" required>
          <a-select v-model:value="formState.type" :options="apiTypeOptions" placeholder="请选择API类型" />
        </a-form-item>

        <a-form-item label="状态">
          <a-radio-group v-model:value="formState.status">
            <a-radio value="启用">启用</a-radio>
            <a-radio value="禁用">禁用</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="描述">
          <a-textarea v-model:value="formState.desc" :rows="5" placeholder="请输入描述" />
        </a-form-item>
      </a-form>

      <div class="modal-actions">
        <a-button @click="formVisible = false">Cancel</a-button>
        <a-button type="primary" @click="submitForm">OK</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="detailVisible" title="API详情" :width="760" :footer="null" destroyOnClose>
      <a-descriptions bordered :column="1" size="middle">
        <a-descriptions-item label="ID">{{ detailRow.id }}</a-descriptions-item>
        <a-descriptions-item label="编码">{{ detailRow.code }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ detailRow.name }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ detailRow.type }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detailRow.status === '启用' ? 'success' : 'default'">{{ detailRow.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述">{{ detailRow.desc || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detailRow.createdAt }}</a-descriptions-item>
      </a-descriptions>

      <div class="modal-actions">
        <a-button @click="detailVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <a-modal
      v-model:open="envVisible"
      :title="`${envName} - API环境管理`"
      :width="1100"
      :footer="null"
      :centered="true"
      :maskClosable="false"
      :keyboard="false"
      destroyOnClose
      wrap-class-name="env-modal-fixed"
    >
      <div class="env-modal-body">
        <a-tabs v-model:activeKey="activeEnvTab" @change="handleEnvTabChange">
          <a-tab-pane key="dev" tab="开发环境" />
          <a-tab-pane key="test" tab="测试环境" />
          <a-tab-pane key="prod" tab="生产环境" />
        </a-tabs>

        <a-alert class="env-alert" type="info" show-icon :message="envTips.api[activeEnvTab]" />

        <div class="toolbar env-toolbar">
          <a-button v-if="activeEnvTab === 'dev'" type="primary" @click="openEnvVersionCreate('dev')">+ 新建版本</a-button>
          <a-button v-if="activeEnvTab === 'test'" type="primary" @click="openEnvVersionCreate('test')">+ 新建版本</a-button>
          <a-button v-if="activeEnvTab === 'prod'" type="primary" @click="openEnvVersionCreate('prod')">+ 新建版本</a-button>
        </div>

        <div class="env-table-wrap">
          <a-table
            :columns="dynamicEnvColumns"
            :data-source="currentEnvRows"
            :pagination="false"
            row-key="id"
            size="middle"
            :scroll="{ x: 980, y: 'calc(100vh - 360px)' }"
            table-layout="fixed"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'pick'">
                <a-radio :checked="selectedVersionId === record.id" @change="() => selectVersionOnly(record)" />
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === '已发布' ? 'success' : 'default'">{{ record.status }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'current'">
                <a-tag v-if="record.current === '是'" color="blue">当前版本</a-tag>
                <span v-else>否</span>
              </template>
              <template v-else-if="column.dataIndex === 'op'">
                <a-button size="small" @click="viewVersion(record)">查看</a-button>
                <a-button size="small" @click="editVersion(record)">编辑</a-button>
                <a-button size="small" type="primary" ghost @click="testVersion(record)">连通测试</a-button>
                <a-button size="small" type="default" @click="selectCurrent(record)" :disabled="record.current === '是'">
                  {{ record.current === '是' ? '已选中' : '选中' }}
                </a-button>
              </template>
            </template>
          </a-table>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="testResultVisible" title="API连通测试结果" :width="920" :footer="null" :centered="true" destroyOnClose wrap-class-name="result-modal-fixed">
      <div class="result-modal-body">
        <a-descriptions bordered :column="1" size="middle">
          <a-descriptions-item label="版本">{{ testResult.version }}</a-descriptions-item>
          <a-descriptions-item label="环境">{{ testResult.environment }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="testResult.success ? 'success' : 'error'">
              {{ testResult.success ? '成功' : '失败' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="状态码">{{ testResult.status ?? '-' }}</a-descriptions-item>
          <a-descriptions-item label="耗时">{{ testResult.duration != null ? `${testResult.duration}ms` : '-' }}</a-descriptions-item>
          <a-descriptions-item label="返回信息">{{ testResult.message || '-' }}</a-descriptions-item>
        </a-descriptions>

        <div class="result-json-title">
          <span>返回数据</span>
          <a-space :size="8">
            <a-button size="small" type="primary" ghost @click="copyTestResult">复制</a-button>
            <a-button size="small" @click="toggleFormattedTestResult">
              {{ testResultFormatted ? '原始JSON数据' : '格式化JSON数据' }}
            </a-button>
          </a-space>
        </div>
        <a-alert
          v-if="!testResult.success && !testResult.payload"
          class="result-alert"
          type="error"
          show-icon
          :message="testResult.error || '测试失败，未返回有效数据'"
        />
        <a-card size="small" class="result-json-card" :body-style="{ padding: '0' }">
          <pre class="result-json"><code>{{ displayedTestResult }}</code></pre>
        </a-card>
      </div>

      <div class="modal-actions result-modal-actions">
        <a-button type="primary" @click="testResultVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <ApiVersionEditor
      :open="versionCreateVisible"
      :title="versionCreateTitle"
      :initialData="versionEditorData"
      :readonly="versionCreateReadOnly"
      :canTest="false"
      @close="versionCreateVisible = false"
      @save="saveEnvVersion"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import ApiVersionEditor from '../components/ApiVersionEditor.vue';
import { externalApi } from '../api/externalApi';

const keyword = ref('');
const loading = ref(false);
const formVisible = ref(false);
const formRef = ref();
const formRules = {
  code: [{ required: true, message: '请输入API编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入API名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择API类型', trigger: 'change' }]
};
const detailVisible = ref(false);
const envVisible = ref(false);
const isEdit = ref(false);
const editingId = ref(null);
const activeEnvTab = ref('dev');
const envName = ref('');
const currentApiId = ref(null);
const currentApiBasicInfo = ref({});
const selectedVersionId = ref(null);
const currentEnvRows = ref([]);

const versionCreateVisible = ref(false);
const versionCreateEnv = ref('dev');
const versionEditorData = ref(null);
const versionCreateReadOnly = ref(false);

const testResultVisible = ref(false);
const testResultFormatted = ref(false);
const testResult = reactive({
  version: '',
  environment: '',
  success: false,
  status: null,
  duration: null,
  message: '',
  error: '',
  payload: null
});

const rows = ref([]);
const total = ref(0);
const currentPage = ref(1);

const apiTypeOptions = [
  { label: 'REST', value: 'REST' },
  { label: 'SOAP', value: 'SOAP' },
  { label: 'GraphQL', value: 'GraphQL' },
  { label: 'Other', value: 'Other' }
];

const formState = reactive({
  code: '',
  name: '',
  type: 'REST',
  status: '启用',
  desc: ''
});

const detailRow = reactive({
  id: '',
  code: '',
  name: '',
  type: '',
  status: '启用',
  desc: '',
  createdAt: ''
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '描述', dataIndex: 'desc' },
  { title: '创建时间', dataIndex: 'createdAt', width: 190 },
  { title: '操作', dataIndex: 'op', width: 280 }
];

const envColumns = [
  { title: '', dataIndex: 'pick', width: 56 },
  { title: '版本', dataIndex: 'version', width: 90 },
  { title: '当前版本', dataIndex: 'current', width: 100 },
  { title: '变更说明', dataIndex: 'remark', width: 180 },
  { title: '创建人', dataIndex: 'creator', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'op', width: 180 }
];

const dynamicEnvColumns = computed(() => envColumns.filter((col) => !(activeEnvTab.value === 'prod' && col.dataIndex === 'status')));

const envTips = {
  api: {
    dev: '开发环境用于联调第三方API和参数调试。',
    test: '测试环境用于回归与验收，发布前请确认开发环境稳定。',
    prod: '生产环境仅允许已验证版本，变更需要审慎发布。'
  }
};

const versionCreateTitle = computed(() => {
  const map = { dev: '开发环境 - 新建版本', test: '测试环境 - 新建版本', prod: '生产环境 - 新建版本' };
  return map[versionCreateEnv.value] || 'API版本新建';
});

const tryParseJsonString = (value) => {
  if (typeof value !== 'string') return value;
  const trimmed = value.trim();
  if (!trimmed || (!trimmed.startsWith('{') && !trimmed.startsWith('['))) return value;
  try {
    return JSON.parse(trimmed);
  } catch (e) {
    return value;
  }
};

const normalizePrettyJson = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => normalizePrettyJson(item));
  }
  if (value && typeof value === 'object') {
    return Object.keys(value).reduce((acc, key) => {
      acc[key] = normalizePrettyJson(tryParseJsonString(value[key]));
      return acc;
    }, {});
  }
  return tryParseJsonString(value);
};

const serializeTestResult = () => {
  if (testResult.payload === null || testResult.payload === undefined || testResult.payload === '') {
    return testResult.error ? JSON.stringify({ error: testResult.error }, null, 2) : '暂无返回数据';
  }
  const normalized = tryParseJsonString(testResult.payload);
  if (typeof normalized === 'string') {
    return normalized;
  }
  try {
    return JSON.stringify(normalizePrettyJson(normalized), null, 2);
  } catch (e) {
    return String(testResult.payload);
  }
};

const formattedTestResult = computed(() => serializeTestResult());
const displayedTestResult = computed(() => {
  if (!testResultFormatted.value) {
    if (testResult.payload === null || testResult.payload === undefined || testResult.payload === '') {
      return testResult.error ? JSON.stringify({ error: testResult.error }, null, 2) : '暂无返回数据';
    }
    return typeof testResult.payload === 'string' ? testResult.payload : JSON.stringify(testResult.payload, null, 2);
  }
  return formattedTestResult.value;
});

const toggleFormattedTestResult = () => {
  testResultFormatted.value = !testResultFormatted.value;
};

const resetTestResult = () => {
  testResult.version = '';
  testResult.environment = '';
  testResult.success = false;
  testResult.status = null;
  testResult.duration = null;
  testResult.message = '';
  testResult.error = '';
  testResult.payload = null;
  testResultFormatted.value = false;
};

const copyTestResult = async () => {
  try {
    const text = formattedTestResult.value;
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    message.success('返回数据已复制');
  } catch (e) {
    message.error('复制失败，请检查浏览器权限');
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const res = await externalApi.pageApi({ current: currentPage.value, size: 10, keyword: keyword.value });
    rows.value = res.data?.data?.records || [];
    total.value = res.data?.data?.total || 0;
  } catch (e) {
    message.error('加载API列表失败');
  } finally {
    loading.value = false;
  }
};

const resetSearch = () => {
  keyword.value = '';
  currentPage.value = 1;
  loadData();
};

const resetForm = () => {
  Object.assign(formState, { code: '', name: '', type: 'REST', status: '启用', desc: '' });
};

const openCreate = () => {
  isEdit.value = false;
  editingId.value = null;
  resetForm();
  formVisible.value = true;
};

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(formState, { code: row.code, name: row.name, type: row.type, status: row.status, desc: row.desc });
  formVisible.value = true;
};

const openDetail = (row) => {
  Object.assign(detailRow, row);
  detailVisible.value = true;
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    if (isEdit.value) {
      await externalApi.updateApi(editingId.value, formState);
    } else {
      await externalApi.createApi(formState);
    }
    formVisible.value = false;
    await loadData();
    message.success('保存成功');
  } catch (e) {
    message.error(e.message || '保存失败');
  }
};

const deleteApi = async (id) => {
  try {
    await externalApi.deleteApi(id);
    message.success('删除成功');
    await loadData();
  } catch (e) {
    message.error('删除失败');
  }
};

const toggleStatus = async (row) => {
  try {
    const nextStatus = row.status === '启用' ? '禁用' : '启用';
    await externalApi.toggleStatus(row.id);
    message.success(`${nextStatus}成功`);
    await loadData();
  } catch (e) {
    message.error(`状态更新失败：${e.message || '操作失败'}`);
  }
};

const loadVersions = async () => {
  if (!currentApiId.value) return;
  try {
    const res = await externalApi.listVersions(currentApiId.value, activeEnvTab.value.toUpperCase());
    currentEnvRows.value = res.data?.data || [];
    selectedVersionId.value = null;
  } catch (e) {
    currentEnvRows.value = [];
    message.error(e.message || '加载版本列表失败');
  }
};

const handleEnvTabChange = async () => {
  selectedVersionId.value = null;
  await loadVersions();
};

const openEnvVersionCreate = (env) => {
  versionCreateEnv.value = env;
  versionEditorData.value = {
    ...currentApiBasicInfo.value,
    environment: String(env || 'dev').toUpperCase()
  };
  versionCreateReadOnly.value = false;
  versionCreateVisible.value = true;
};

const selectVersionOnly = (record) => {
  selectedVersionId.value = selectedVersionId.value === record.id ? null : record.id;
};

const bumpVersion = (v = '1.0.0') => {
  const arr = String(v || '1.0.0').split('.').map((x) => Number(x) || 0);
  while (arr.length < 3) arr.push(0);
  arr[2] += 1;
  return `${arr[0]}.${arr[1]}.${arr[2]}`;
};

const parseDsl = (record) => {
  try {
    const source = record?.dslContent || record?.dsl;
    return source ? (typeof source === 'string' ? JSON.parse(source) : source) : {};
  } catch (e) {
    return {};
  }
};

const saveEnvVersion = async (dslPayload) => {
  if (!currentApiId.value) {
    message.warning('未识别API ID');
    return;
  }
  const latest = currentEnvRows.value[0]?.version || '1.0.0';
  const nextVersion = currentEnvRows.value.length ? bumpVersion(latest) : '1.0.0';

  try {
    await externalApi.createVersion({
      apiId: currentApiId.value,
      environment: versionCreateEnv.value.toUpperCase(),
      version: nextVersion,
      dslContent: JSON.stringify({ ...dslPayload, version: nextVersion }),
      requestMethod: dslPayload.method,
      requestUrl: dslPayload.url,
      contentType: dslPayload.contentType,
      requestHeaders: dslPayload.headers,
      queryParams: dslPayload.queryParams,
      requestBody: dslPayload.body,
      authType: dslPayload.authType,
      authConfig: dslPayload.authInfo,
      timeout: dslPayload.readTimeout,
      retryCount: dslPayload.retryCount,
      changeLog: dslPayload.changeLog
    });
    versionCreateVisible.value = false;
    message.success('版本保存成功');
    await loadVersions();
  } catch (e) {
    message.error(`版本保存失败：${e.message}`);
  }
};

const viewVersion = (record) => {
  versionCreateEnv.value = record.environment ? String(record.environment).toLowerCase() : activeEnvTab.value;
  versionEditorData.value = { ...currentApiBasicInfo.value, ...record, ...parseDsl(record) };
  versionCreateReadOnly.value = true;
  versionCreateVisible.value = true;
};

const editVersion = (record) => {
  versionCreateEnv.value = record.environment ? String(record.environment).toLowerCase() : activeEnvTab.value;
  versionEditorData.value = { ...currentApiBasicInfo.value, ...record, ...parseDsl(record) };
  versionCreateReadOnly.value = false;
  versionCreateVisible.value = true;
};

const testVersion = async (record) => {
  try {
    resetTestResult();
    const dsl = parseDsl(record);
    const payload = {
      requestMethod: record.requestMethod || dsl.method,
      requestUrl: record.requestUrl || dsl.url,
      contentType: record.contentType || dsl.contentType,
      requestHeaders: record.requestHeaders || dsl.headers,
      queryParams: record.queryParams || dsl.queryParams,
      requestBody: record.requestBody || dsl.body,
      authType: record.authType || dsl.authType,
      authConfig: record.authConfig || dsl.authInfo,
      timeout: record.timeout || dsl.readTimeout
    };
    if (!payload.requestUrl) {
      message.warning('当前版本缺少可用于连通测试的请求地址');
      return;
    }
    const res = await externalApi.testConnection(payload);
    const data = res.data?.data ?? res.data ?? {};
    testResult.version = record.version || '-';
    testResult.environment = record.environment || activeEnvTab.value.toUpperCase();
    testResult.success = Boolean(data.success ?? data.ok ?? false);
    testResult.status = data.status ?? data.code ?? res.status ?? null;
    testResult.duration = data.duration ?? data.costTime ?? data.elapsedTime ?? null;
    testResult.message = data.message || data.msg || '测试完成';
    testResult.payload = data.responseData ?? data.data ?? data.payload ?? data;
    testResultVisible.value = true;
    if (testResult.success) {
      message.success(`版本 ${record.version} 测试成功！状态码: ${testResult.status ?? '-'}，耗时: ${testResult.duration ?? '-'}ms`);
    } else {
      message.warning(`版本 ${record.version} 测试完成，请查看返回数据`);
    }
  } catch (e) {
    testResult.version = record.version || '-';
    testResult.environment = record.environment || activeEnvTab.value.toUpperCase();
    testResult.success = false;
    testResult.error = e.message || '测试失败';
    testResult.payload = null;
    testResultVisible.value = true;
    message.error(`测试失败：${e.message}`);
  }
};

const selectCurrent = async (record) => {
  if (!record) {
    message.warning('未找到要设置的版本');
    return;
  }
  try {
    await externalApi.setCurrentVersion(record.id);
    message.success('已设为当前执行版本');
    await loadVersions();
  } catch (e) {
    message.error(`设置失败：${e.message}`);
  }
};

const openEnvModal = (record, env = 'dev') => {
  currentApiId.value = record.id;
  currentApiBasicInfo.value = { ...record };
  envName.value = record.name || record.code;
  activeEnvTab.value = env;
  selectedVersionId.value = null;
  envVisible.value = true;
  loadVersions();
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.api-form {
  padding-top: 8px;
}

.modal-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.env-toolbar {
  margin-top: 14px;
  margin-bottom: 14px;
}

.env-modal-body {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 240px);
  min-height: 560px;
}

.env-table-wrap {
  flex: 1;
  min-height: 0;
}

.env-alert {
  margin-bottom: 10px;
}

.result-modal-fixed .ant-modal-body {
  overflow: hidden;
}

.result-modal-body {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 180px);
  overflow: hidden;
}

.result-json-title {
  margin: 16px 0 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-alert {
  margin-bottom: 10px;
}

.result-json-card {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.result-json {
  margin: 0;
  padding: 16px;
  max-height: none;
  min-height: 220px;
  max-height: calc(100vh - 500px);
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.6;
}

.result-modal-actions {
  margin-top: 14px;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>

<style>
.env-modal-fixed .ant-modal {
  width: 1100px !important;
  max-width: calc(100vw - 48px);
}

.env-modal-fixed .ant-modal-content {
  height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
}

.env-modal-fixed .ant-modal-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.result-modal-fixed .ant-modal {
  max-width: calc(100vw - 48px);
}

.result-modal-fixed .ant-modal-content {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
}

.result-modal-fixed .ant-modal-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
