<template>
  <div class="page-wrap">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入接口名称和编码" />
      <a-button type="primary" @click="loadData">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建接口</a-button>
    </div>

    <a-table :columns="columns" :data-source="rows" :pagination="{ pageSize: 10 }" row-key="id" size="middle"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag color="success" v-if="record.status === '启用'">启用</a-tag>
          <a-tag color="default" v-else>禁用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" @click="openDetail(record)">查看</a-button>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" type="primary" ghost @click="generateKey(record)">生成密钥</a-button>
            <a-button size="small" type="primary" @click="openInvoke(record)">调用</a-button>
            <a-button size="small" @click="toggleStatus(record)">
              {{ record.status === '启用' ? '禁用' : '启用' }}
            </a-button>
            <a-popconfirm title="确定删除该接口吗？" ok-text="确定" cancel-text="取消" @confirm="deleteOpenApi(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑接口' : '新建接口'" :width="1200" :footer="null" destroyOnClose>
      <div class="openapi-modal-content">
        <div class="layout-left">
          <div class="section-card">
            <div class="section-title">基本信息</div>
            <a-form ref="formRef" :model="formState" :rules="formRules" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="接口编码" required name="code">
                    <a-input v-model:value="formState.code" :disabled="isEdit" placeholder="例如：OPEN_API_USER_001" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="接口名称" required name="name">
                    <a-input v-model:value="formState.name" placeholder="例如：用户查询接口" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="16">
                  <a-form-item label="接口路径" required name="path">
                    <a-input v-model:value="formState.path" placeholder="例如：/api/user/query" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="请求方法">
                    <a-input v-model:value="formState.method" disabled />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="关联任务">
                <a-select v-model:value="formState.taskId" :options="taskOptions" placeholder="请选择关联任务" allow-clear />
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="认证类型">
                    <a-select v-model:value="formState.authType" :options="authTypeOptions" placeholder="请选择认证类型" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="限流次数">
                    <a-input-number v-model:value="formState.limit" :min="0" style="width: 100%" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="状态">
                <a-radio-group v-model:value="formState.status">
                  <a-radio value="启用">启用</a-radio>
                  <a-radio value="禁用">禁用</a-radio>
                </a-radio-group>
              </a-form-item>

              <a-form-item label="描述">
                <a-textarea v-model:value="formState.desc" :rows="3" placeholder="请输入描述" />
              </a-form-item>
            </a-form>
          </div>
        </div>

        <div class="layout-right">
          <div class="section-card" v-if="formState.taskId">
            <div class="section-title">关联任务与组件信息</div>
            <a-descriptions bordered :column="1" size="small" style="margin-bottom: 10px;">
              <a-descriptions-item label="任务">{{ taskDetailInfo.taskName || '-' }}</a-descriptions-item>
              <a-descriptions-item label="任务编码">{{ taskDetailInfo.taskCode || '-' }}</a-descriptions-item>
              <a-descriptions-item label="版本">{{ taskDetailInfo.taskVersion || '-' }}</a-descriptions-item>
              <a-descriptions-item label="组件数量">{{ taskDetailInfo.componentCount }}</a-descriptions-item>
            </a-descriptions>
            <a-table :data-source="taskComponentRows" size="small" :pagination="false" :columns="componentColumns" :scroll="{ y: 220 }" row-key="id">
              <template #expandedRowRender="{ record }">
                <a-row :gutter="12">
                  <a-col :span="12">
                    <div style="font-weight:500;margin-bottom:6px;">输入参数明细</div>
                    <a-table size="small" :pagination="false" :data-source="record.inputParams || []" :columns="componentIoColumns" row-key="__rowKey" :scroll="{ y: 120 }" />
                  </a-col>
                  <a-col :span="12">
                    <div style="font-weight:500;margin-bottom:6px;">输出参数明细</div>
                    <a-table size="small" :pagination="false" :data-source="record.outputParams || []" :columns="componentIoColumns" row-key="__rowKey" :scroll="{ y: 120 }" />
                  </a-col>
                </a-row>
              </template>
            </a-table>
          </div>

          <div class="section-card" v-if="formState.taskId" style="margin-top: 16px;">
            <div class="section-title">入参配置（生产版本提取）</div>
            <div class="schema-config">
              <a-table :data-source="inputTableData" size="small" :pagination="false" :columns="schemaColumns"
                :scroll="{ x: 'max-content', y: 150 }" />
              <a-textarea v-model:value="formState.inputSchema" :rows="5" style="margin-top: 8px" readonly />
            </div>
          </div>

          <div class="section-card" v-if="formState.taskId" style="margin-top: 16px;">
            <div class="section-title">出参配置（生产版本提取）</div>
            <div class="schema-config">
              <a-table :data-source="outputTableData" size="small" :pagination="false" :columns="schemaColumns"
                :scroll="{ x: 'max-content', y: 150 }" />
              <a-textarea v-model:value="formState.outputSchema" :rows="5" style="margin-top: 8px" readonly />
            </div>
          </div>

          <div class="empty-placeholder" v-else>
            <a-empty description="请先选择关联任务以查看参数配置" />
          </div>
        </div>
      </div>

      <div class="modal-footer-actions">
        <a-button @click="formVisible = false">Cancel</a-button>
        <a-button type="primary" @click="submitForm">OK</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="detailVisible" title="查看接口" :width="1200" :footer="null" destroyOnClose>
      <div class="openapi-modal-content">
        <div class="layout-left">
          <div class="section-card">
            <div class="section-title">基本信息</div>
            <a-form :model="detailRow" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="接口编码">
                    <a-input :value="detailRow.code" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="接口名称">
                    <a-input :value="detailRow.name" disabled />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="16">
                  <a-form-item label="接口路径">
                    <a-input :value="detailRow.path" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="请求方法">
                    <a-input :value="detailRow.method" disabled />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="关联任务">
                <a-input :value="taskOptions.find(t => t.value === detailRow.taskId)?.label || '-'" disabled />
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="认证类型">
                    <a-input :value="detailRow.authType" disabled />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="限流次数">
                    <a-input :value="detailRow.limit" disabled />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="状态">
                <a-radio-group :value="detailRow.status" disabled>
                  <a-radio value="启用">启用</a-radio>
                  <a-radio value="禁用">禁用</a-radio>
                </a-radio-group>
              </a-form-item>

              <a-form-item label="描述">
                <a-textarea :value="detailRow.desc || '-'" :rows="3" readonly />
              </a-form-item>
            </a-form>
          </div>
        </div>

        <div class="layout-right">
          <div class="section-card" v-if="detailRow.taskId">
            <div class="section-title">关联任务与组件信息</div>
            <a-descriptions bordered :column="1" size="small" style="margin-bottom: 10px;">
              <a-descriptions-item label="任务">{{ taskDetailInfo.taskName || '-' }}</a-descriptions-item>
              <a-descriptions-item label="任务编码">{{ taskDetailInfo.taskCode || '-' }}</a-descriptions-item>
              <a-descriptions-item label="版本">{{ taskDetailInfo.taskVersion || '-' }}</a-descriptions-item>
              <a-descriptions-item label="组件数量">{{ taskDetailInfo.componentCount }}</a-descriptions-item>
            </a-descriptions>
          </div>

          <div class="section-card" v-if="detailRow.taskId" style="margin-top: 16px;">
            <div class="section-title">入参配置（生产版本提取）</div>
            <div class="schema-config">
              <a-table :data-source="inputTableData" size="small" :pagination="false" :columns="schemaColumns" :scroll="{ x: 'max-content', y: 150 }" />
              <a-textarea :value="detailRow.inputSchema" :rows="5" style="margin-top: 8px" readonly />
            </div>
          </div>

          <div class="section-card" v-if="detailRow.taskId" style="margin-top: 16px;">
            <div class="section-title">出参配置（生产版本提取）</div>
            <div class="schema-config">
              <a-table :data-source="outputTableData" size="small" :pagination="false" :columns="schemaColumns" :scroll="{ x: 'max-content', y: 150 }" />
              <a-textarea :value="detailRow.outputSchema" :rows="5" style="margin-top: 8px" readonly />
            </div>
          </div>

          <div class="empty-placeholder" v-else>
            <a-empty description="暂无关联任务" />
          </div>
        </div>
      </div>

      <div class="modal-footer-actions">
        <a-button @click="detailVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="invokeVisible" title="调用开放接口" :width="800" :footer="null" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="接口编码">
          <a-input :value="invokeForm.code" disabled />
        </a-form-item>
        <a-form-item label="X-App-Secret（可选）">
          <a-input v-model:value="invokeForm.appSecret" placeholder="若接口配置了密钥，请输入" />
        </a-form-item>
        <a-form-item label="请求体(JSON)">
          <a-textarea v-model:value="invokeForm.payloadText" :rows="10" />
        </a-form-item>
      </a-form>
      <div class="modal-footer-actions">
        <a-button @click="invokeVisible = false">取消</a-button>
        <a-button type="primary" :loading="invokeLoading" @click="submitInvoke">发送调用</a-button>
      </div>
      <div v-if="invokeResultText" style="margin-top:12px;">
        <div style="font-weight:500;margin-bottom:6px;">调用结果</div>
        <a-textarea :value="invokeResultText" :rows="8" readonly />
      </div>
      <div v-if="invokeExecutionId" style="margin-top:8px; color:#1677ff; cursor:pointer;" @click="openLogDetail(invokeExecutionId)">
        执行ID：{{ invokeExecutionId }}（点击查看执行日志）
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import { openApi } from '../api/openApi';
import { taskApi } from '../api/task';

const keyword = ref('');
const loading = ref(false);
const formRef = ref();
const formRules = {
  code: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入接口路径', trigger: 'blur' }]
};
const formVisible = ref(false);
const detailVisible = ref(false);
const isEdit = ref(false);
const editingId = ref(null);

const authTypeOptions = [
  { label: '无认证', value: 'None' },
  { label: 'API Key', value: 'ApiKey' },
  { label: 'OAuth2', value: 'OAuth2' }
];

const router = useRouter();
const taskOptions = ref([]);
const invokeVisible = ref(false);
const invokeLoading = ref(false);
const taskDetailInfo = reactive({ taskName: '', taskCode: '', taskVersion: '', componentCount: 0 });
const taskComponentRows = ref([]);
const taskInputParamRows = ref([]);
const taskOutputParamRows = ref([]);
const invokeResultText = ref('');
const invokeExecutionId = ref('');
const invokeForm = reactive({
  code: '',
  appSecret: '',
  payloadText: '{\n  "input": "demo"\n}'
});

const formState = reactive({
  code: '',
  name: '',
  path: '',
  method: 'POST',
  taskId: undefined,
  inputSchema: '',
  outputSchema: '',
  authType: 'None',
  limit: 0,
  status: '启用',
  desc: ''
});

const detailRow = reactive({
  id: '',
  code: '',
  name: '',
  path: '',
  method: '',
  taskId: null,
  inputSchema: '',
  outputSchema: '',
  authType: 'None',
  limit: 0,
  status: '启用',
  appSecret: '',
  desc: '',
  createdAt: ''
});

const inputTableData = computed(() => parseSchemaToTable(detailVisible.value ? detailRow.inputSchema : formState.inputSchema));
const outputTableData = computed(() => parseSchemaToTable(detailVisible.value ? detailRow.outputSchema : formState.outputSchema));

const normalizeDsl = (rawDsl) => {
  if (!rawDsl) return {};
  if (typeof rawDsl === 'string') {
    try {
      return JSON.parse(rawDsl);
    } catch (e) {
      return {};
    }
  }
  return rawDsl;
};

// 与画布编辑器保持一致的标签映射
const sourceTypeLabels = { CONST: '常量', UPSTREAM_OUTPUT: '上游输出', EXPRESSION: '表达式' };
const dataTypeLabels = { STRING: '字符串', NUMBER: '数字', BOOLEAN: '布尔' };

const normalizeJsonType = (dataType) => {
  const dt = String(dataType || '').toUpperCase();
  if (dt === 'NUMBER' || dt === 'INTEGER' || dt === 'LONG' || dt === 'DOUBLE') return 'number';
  if (dt === 'BOOLEAN' || dt === 'BOOL') return 'boolean';
  if (dt === 'OBJECT' || dt === 'MAP') return 'object';
  if (dt === 'ARRAY' || dt === 'LIST') return 'array';
  return 'string';
};

const extractSchemaFromDsl = (dsl) => {
  const inputProperties = {};
  const outputProperties = {};
  const requiredInputs = [];
  const requiredOutputs = [];

  const nodes = Array.isArray(dsl?.nodes) ? dsl.nodes : [];
  const startNode = nodes.find((n) => String(n?.type || '').toUpperCase() === 'START');
  const endNode = nodes.find((n) => String(n?.type || '').toUpperCase() === 'END');

  const startParams = Array.isArray(startNode?.inputParams) ? startNode.inputParams : [];
  const endParams = Array.isArray(endNode?.outputParams) ? endNode.outputParams : [];

  startParams.forEach((input) => {
    const key = input?.paramCode || input?.paramName;
    if (!key) return;
    const rawDefault = input?.sourceValue ?? input?.defaultValue ?? '';
    const srcType = sourceTypeLabels[input?.sourceType] || input?.sourceType || '常量';
    inputProperties[key] = {
      type: normalizeJsonType(input?.dataType),
      description: `${input?.paramName || ''}（${srcType}）`,
      default: typeof rawDefault === 'object' ? JSON.stringify(rawDefault) : rawDefault
    };
    if (Number(input?.requiredFlag || 0) === 1) {
      requiredInputs.push(key);
    }
  });

  endParams.forEach((output) => {
    const key = output?.paramCode || output?.paramName;
    if (!key) return;
    const rawDefault = output?.sourceValue ?? output?.defaultValue ?? '';
    const srcType = sourceTypeLabels[output?.sourceType] || output?.sourceType || '常量';
    outputProperties[key] = {
      type: normalizeJsonType(output?.dataType),
      description: `${output?.paramName || ''}（${srcType}）`,
      default: typeof rawDefault === 'object' ? JSON.stringify(rawDefault) : rawDefault
    };
    if (Number(output?.requiredFlag || 0) === 1) {
      requiredOutputs.push(key);
    }
  });

  return { inputProperties, outputProperties, requiredInputs, requiredOutputs };
};

const jsonTypeLabels = { string: '字符串', number: '数字', boolean: '布尔', object: '对象', array: '数组' };

const parseSchemaToTable = (schemaStr) => {
  if (!schemaStr) return [];
  try {
    const schema = JSON.parse(schemaStr);
    const properties = schema.properties || {};
    return Object.keys(properties).map(key => {
      const desc = properties[key].description || '';
      // 描述格式为 "名称（来源类型）"，分离出纯名称
      const match = desc.match(/^(.+?)（(.+?)）$/);
      const paramName = match ? match[1] : desc;
      const sourceType = match ? match[2] : '';
      return {
        paramCode: key,
        paramName: paramName,
        sourceType: sourceType,
        dataType: jsonTypeLabels[properties[key].type] || properties[key].type || '字符串',
        required: schema.required?.includes(key) ? '是' : '可选',
        defaultValue: properties[key].default ?? ''
      };
    });
  } catch (e) {
    return [];
  }
};

const loadTasks = async () => {
  try {
    const res = await taskApi.page({ current: 1, size: 100 });
    const allTasks = res.data?.data?.records || [];
    // 过滤掉禁用的任务，且后续在选择时检查是否有生产版本
    taskOptions.value = allTasks
      .filter(t => String(t.status) === '1')
      .map(t => ({
        label: `${t.taskName} (${t.taskCode})`,
        value: t.id
      }));
  } catch (e) {
    console.error('Failed to load tasks', e);
  }
};


const rows = ref([]);
const total = ref(0);
const currentPage = ref(1);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await openApi.pageApi({
      current: currentPage.value,
      size: 10,
      keyword: keyword.value
    });
    rows.value = res.data?.data?.records || [];
    total.value = res.data?.data?.total || 0;
  } catch (e) {
    message.error('加载列表失败');
  } finally {
    loading.value = false;
  }
};

const resetSearch = () => {
  keyword.value = '';
  currentPage.value = 1;
  loadData();
};

const openCreate = () => {
  isEdit.value = false;
  editingId.value = null;
  Object.assign(formState, {
    code: '', name: '', path: '', method: 'POST', taskId: undefined,
    inputSchema: '', outputSchema: '', authType: 'None', limit: 0, status: '启用', desc: ''
  });
  formVisible.value = true;
};

watch(
  () => formState.taskId,
  async (newVal, oldVal) => {
    if (!newVal || newVal === oldVal) {
      if (!isEdit.value) {
        formState.inputSchema = '';
        formState.outputSchema = '';
      }
      return;
    }

    try {
      message.loading({ content: '正在同步任务参数...', key: 'sync-schema', duration: 0 });
      const schemaResult = await refreshTaskSchema(newVal);
      if (!schemaResult) {
        message.warning({ content: '该任务暂无已发布的生产版本，请先前往任务管理发布', key: 'sync-schema', duration: 3 });
        if (!isEdit.value) {
          formState.taskId = undefined;
          formState.inputSchema = '';
          formState.outputSchema = '';
        }
        return;
      }

      formState.inputSchema = schemaResult.inputSchema;
      formState.outputSchema = schemaResult.outputSchema;
      message.success({ content: '参数同步成功', key: 'sync-schema', duration: 2 });
    } catch (e) {
      console.error('Sync Task Schema Error:', e);
      message.error({ content: '获取任务配置失败: ' + (e.message || '网络错误'), key: 'sync-schema', duration: 3 });
    }
  }
);

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(formState, {
    code: row.code, name: row.name, path: row.path, method: row.method,
    taskId: row.taskId, inputSchema: row.inputSchema, outputSchema: row.outputSchema,
    authType: row.authType || 'None', limit: row.limit || 0, status: row.status, desc: row.desc
  });
  formVisible.value = true;
};

const openDetail = async (row) => {
  Object.assign(detailRow, {
    ...row,
    authType: row.authType || 'None',
    limit: row.limit ?? 0,
    desc: row.desc || ''
  });
  if (row.taskId) {
    try {
      await refreshTaskSchema(row.taskId);
    } catch (e) {
      console.error('Load detail task schema error:', e);
    }
  } else {
    Object.assign(taskDetailInfo, { taskName: '', taskCode: '', taskVersion: '', componentCount: 0 });
    taskComponentRows.value = [];
    taskInputParamRows.value = [];
    taskOutputParamRows.value = [];
  }
  detailVisible.value = true;
};

const refreshTaskSchema = async (taskId) => {
  if (!taskId) {
    Object.assign(taskDetailInfo, { taskName: '', taskCode: '', taskVersion: '', componentCount: 0 });
    taskComponentRows.value = [];
    taskInputParamRows.value = [];
    taskOutputParamRows.value = [];
    return { inputSchema: '', outputSchema: '' };
  }

  const res = await taskApi.current(taskId, 'PROD');
  const versionData = res.data?.data;
  if (!versionData) {
    return null;
  }

  const dsl = normalizeDsl(versionData.dslContent || versionData.dslJson || '{}');
  const nodes = Array.isArray(dsl?.nodes) ? dsl.nodes : [];
  const taskOpt = taskOptions.value.find(t => String(t.value) === String(taskId));
  Object.assign(taskDetailInfo, {
    taskName: taskOpt?.label?.split(' (')[0] || '',
    taskCode: taskOpt?.label?.match(/\((.*)\)/)?.[1] || '',
    taskVersion: versionData.version || versionData.versionNo || versionData.id || '-',
    componentCount: nodes.length
  });
  const inputRows = [];
  const outputRows = [];

  taskComponentRows.value = nodes.map((n) => {
    const nodeInputParams = Array.isArray(n.inputParams) ? n.inputParams : [];
    const nodeOutputParams = Array.isArray(n.outputParams) ? n.outputParams : [];
    const inputParams = nodeInputParams
      .map((p, idx) => {
        const sv = p.sourceValue ?? p.defaultValue ?? '-';
        const displaySv = typeof sv === 'object' ? JSON.stringify(sv) : String(sv);
        const row = {
          __rowKey: `${n.id}_in_${idx}`,
          nodeId: n.id,
          nodeName: n.name || '-',
          nodeType: n.type || '-',
          paramType: 'INPUT',
          paramCode: p.paramCode || '-',
          paramName: p.paramName || '-',
          dataType: dataTypeLabels[p.dataType] || p.dataType || '-',
          sourceType: sourceTypeLabels[p.sourceType] || p.sourceType || '-',
          sourceValue: displaySv,
          requiredFlag: Number(p.requiredFlag || 0) === 1 ? '是' : '可选',
          description: p.description || '-'
        };
        inputRows.push(row);
        return row;
      });
    const outputParams = nodeOutputParams
      .map((p, idx) => {
        const sv = p.sourceValue ?? p.defaultValue ?? '-';
        const displaySv = typeof sv === 'object' ? JSON.stringify(sv) : String(sv);
        const row = {
          __rowKey: `${n.id}_out_${idx}`,
          nodeId: n.id,
          nodeName: n.name || '-',
          nodeType: n.type || '-',
          paramType: 'OUTPUT',
          paramCode: p.paramCode || '-',
          paramName: p.paramName || '-',
          dataType: dataTypeLabels[p.dataType] || p.dataType || '-',
          sourceType: sourceTypeLabels[p.sourceType] || p.sourceType || '-',
          sourceValue: displaySv,
          requiredFlag: Number(p.requiredFlag || 0) === 1 ? '是' : '可选',
          description: p.description || '-'
        };
        outputRows.push(row);
        return row;
      });
    return {
      id: n.id,
      name: n.name || '-',
      type: n.type || '-',
      inputCount: inputParams.length,
      outputCount: outputParams.length,
      inputParams,
      outputParams
    };
  });

  taskInputParamRows.value = inputRows;
  taskOutputParamRows.value = outputRows;

  const { inputProperties, outputProperties, requiredInputs, requiredOutputs } = extractSchemaFromDsl(dsl);

  return {
    inputSchema: JSON.stringify({
      type: 'object',
      properties: inputProperties,
      ...(requiredInputs.length ? { required: requiredInputs } : {})
    }, null, 2),
    outputSchema: JSON.stringify({
      type: 'object',
      properties: outputProperties,
      ...(requiredOutputs.length ? { required: requiredOutputs } : {})
    }, null, 2)
  };
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    if (formState.taskId) {
      const schemaResult = await refreshTaskSchema(formState.taskId);
      if (!schemaResult) {
        message.warning('该任务暂无已发布的生产版本，请先前往任务管理发布');
        return;
      }
      formState.inputSchema = schemaResult.inputSchema;
      formState.outputSchema = schemaResult.outputSchema;
    }

    if (isEdit.value) {
      await openApi.updateApi(editingId.value, formState);
      message.success('修改成功');
    } else {
      await openApi.createApi(formState);
      message.success('创建成功');
    }
    formVisible.value = false;
    loadData();
  } catch (e) {
    message.error(e.message || '保存失败');
  }
};

const deleteOpenApi = async (id) => {
  try {
    await openApi.deleteApi(id);
    message.success('删除成功');
    loadData();
  } catch (e) {
    message.error('删除失败');
  }
};

const toggleStatus = async (row) => {
  try {
    const newStatus = row.status === '启用' ? '禁用' : '启用';
    await openApi.toggleStatus(row.id);
    message.success(`${newStatus}成功`);
    await loadData();
  } catch (e) {
    message.error(`状态更新失败：${e.message || '操作失败'}`);
  }
};

const generateKey = async (row) => {
  try {
    await openApi.generateKey(row.id);
    message.success(`已为接口 [${row.name}] 重新生成密钥`);
    loadData();
  } catch (e) {
    message.error('生成密钥失败');
  }
};

const openInvoke = (row) => {
  invokeForm.code = row.code;
  invokeForm.appSecret = '';
  invokeForm.payloadText = '{\n  "input": "demo"\n}';
  invokeResultText.value = '';
  invokeExecutionId.value = '';
  invokeVisible.value = true;
};

const submitInvoke = async () => {
  try {
    invokeLoading.value = true;
    let payload = {};
    try {
      payload = JSON.parse(invokeForm.payloadText || '{}');
    } catch (e) {
      message.warning('请求体不是合法JSON');
      return;
    }
    const res = await openApi.invokeByCode(invokeForm.code, payload, invokeForm.appSecret || undefined);
    const data = res.data?.data || {};
    invokeExecutionId.value = data.executionId || '';
    invokeResultText.value = JSON.stringify(data, null, 2);
    message.success('调用成功');
  } catch (e) {
    message.error(`调用失败：${e.message || '未知错误'}`);
  } finally {
    invokeLoading.value = false;
  }
};

const openLogDetail = (executionId) => {
  if (!executionId) return;
  router.push({ path: '/execution-log', query: { executionId } });
};

onMounted(() => {
  loadData();
  loadTasks();
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '路径', dataIndex: 'path' },
  { title: '方法', dataIndex: 'method', width: 90 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '描述', dataIndex: 'desc' },
  { title: '创建时间', dataIndex: 'createdAt', width: 190 },
  { title: '操作', dataIndex: 'op', width: 340 }
];

const schemaColumns = [
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 100 },
  { title: '数据类型', dataIndex: 'dataType', width: 90 },
  { title: '参数来源', dataIndex: 'sourceType', width: 90 },
  { title: '是否必填', dataIndex: 'required', width: 80 },
  { title: '默认值', dataIndex: 'defaultValue', width: 120 }
];

const componentColumns = [
  { title: '组件ID', dataIndex: 'id', width: 140 },
  { title: '组件名称', dataIndex: 'name', width: 180 },
  { title: '组件类型', dataIndex: 'type', width: 120 },
  { title: '入参数量', dataIndex: 'inputCount', width: 90 },
  { title: '出参数量', dataIndex: 'outputCount', width: 90 }
];

const componentIoColumns = [
  { title: '参数编码', dataIndex: 'paramCode', width: 120 },
  { title: '参数名称', dataIndex: 'paramName', width: 120 },
  { title: '数据类型', dataIndex: 'dataType', width: 90 },
  { title: '参数来源', dataIndex: 'sourceType', width: 90 },
  { title: '来源值', dataIndex: 'sourceValue', width: 120 },
  { title: '必填', dataIndex: 'requiredFlag', width: 70 }
];

const taskParamColumns = [
  { title: '组件ID', dataIndex: 'nodeId', width: 140 },
  { title: '组件名称', dataIndex: 'nodeName', width: 180 },
  { title: '组件类型', dataIndex: 'nodeType', width: 120 },
  { title: '参数类型', dataIndex: 'paramType', width: 100 },
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 160 },
  { title: '数据类型', dataIndex: 'dataType', width: 110 },
  { title: '参数来源', dataIndex: 'sourceType', width: 120 },
  { title: '来源值', dataIndex: 'sourceValue', width: 180 },
  { title: '必填', dataIndex: 'requiredFlag', width: 90 },
  { title: '描述', dataIndex: 'description', width: 180 }
];
</script>

<style scoped>
.openapi-modal-content {
  display: flex;
  gap: 20px;
  height: 70vh;
  overflow: hidden;
  padding: 10px 0;
}

.layout-left {
  flex: 1;
  min-width: 400px;
  overflow: hidden;
}

.layout-right {
  flex: 1;
  min-width: 500px;
  height: 100%;
  overflow-y: auto;
  padding-right: 6px;
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

.empty-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}

.modal-footer-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>