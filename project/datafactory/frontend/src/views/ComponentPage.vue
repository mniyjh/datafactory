<template>
  <div class="page-wrap component-page">
    <div class="toolbar toolbar-wrap">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入组件名称或编码" />
      <a-button type="primary" @click="loadComponents">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建组件</a-button>
    </div>

    <a-table :columns="columns" :data-source="rows" :loading="loading" :pagination="{ pageSize: 10 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 1 ? 'success' : 'default'">{{ record.status === 1 ? '启用' : '禁用' }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'componentType'">
          {{ record.componentType || '-' }}
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space wrap>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" @click="openMetaEditor(record)">组件字段配置</a-button>
<a-button size="small" @click="toggleComponentStatus(record)">{{ record.status === 1 ? '禁用' : '启用' }}</a-button>
            <a-popconfirm title="确认删除该组件吗？删除后不可恢复。" ok-text="删除" cancel-text="取消" @confirm="deleteComponent(record)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑组件' : '新建组件'" :width="720" :footer="null" destroyOnClose>
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '120px' } }">
        <a-form-item label="组件编码" required>
          <a-input v-model:value="formState.componentCode" :disabled="isEdit" placeholder="请输入组件编码" />
        </a-form-item>
        <a-form-item label="组件名称" required>
          <a-input v-model:value="formState.componentName" placeholder="请输入组件名称" />
        </a-form-item>
        <a-form-item label="组件分类" required>
          <a-select v-model:value="formState.componentType" placeholder="请选择组件分类">
            <a-select-option value="数据接入">数据接入</a-select-option>
            <a-select-option value="数据处理">数据处理</a-select-option>
            <a-select-option value="流程控制">流程控制</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="版本">
          <a-input v-model:value="formState.version" placeholder="1.0.0" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="formState.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="formState.description" :rows="4" placeholder="请输入说明" />
        </a-form-item>
      </a-form>
      <div class="modal-actions">
        <a-button @click="formVisible = false">取消</a-button>
        <a-button type="primary" @click="submitForm">保存</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="metaVisible" :title="`组件字段管理 - ${metaTitle}`" :width="1450" :footer="null" destroyOnClose :bodyStyle="{ overflow: 'hidden' }">
      <div class="meta-layout">
        <div class="meta-section meta-section-top">
          <div class="meta-section-title">字段管理</div>
          <a-space style="margin-bottom: 8px;">
            <a-button type="dashed" @click="addFieldRow">+ 新增字段</a-button>
          </a-space>
          <div class="meta-table-wrap">
            <a-table :columns="fieldColumns" :data-source="metaFields" :pagination="false" row-key="__idx" :scroll="{ y: 420, x: 1250 }">
              <template #bodyCell="{ column, record, index }">
                <div v-if="column.dataIndex === 'drag'" draggable="true" @dragstart="onDragStart(index)" @dragover.prevent @drop="onDropRow(index)" style="cursor: move;">☰</div>
                <a-input v-else-if="column.dataIndex === 'fieldCode'" v-model:value="record.fieldCode" placeholder="如 user_name" />
                <a-input v-else-if="column.dataIndex === 'fieldName'" v-model:value="record.fieldName" placeholder="如 用户名" />
                <a-select v-else-if="column.dataIndex === 'valueType'" v-model:value="record.valueType" style="width: 100%" :getPopupContainer="triggerNode => triggerNode.closest('.ant-modal-content') || document.body">
                  <a-select-option v-for="t in valueTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
                </a-select>
                <a-select v-else-if="column.dataIndex === 'widgetType'" v-model:value="record.widgetType" style="width: 100%" :getPopupContainer="triggerNode => triggerNode.closest('.ant-modal-content') || document.body">
                  <a-select-option v-for="t in widgetTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
                </a-select>
                <div v-else-if="column.dataIndex === 'optionConfig' && shouldPersistWidgetProps(record)" style="display:flex;flex-direction:column;gap:4px;">
                  <a-radio-group v-model:value="record.optionsSourceType" size="small">
                    <a-radio-button value="">静态</a-radio-button>
                    <a-radio-button value="DB_QUERY">DB加载</a-radio-button>
                    <a-radio-button value="API_CALL">API加载</a-radio-button>
                    <a-radio-button value="SCRIPT">SCRIPT加载</a-radio-button>
                    <a-radio-button value="TASK">任务加载</a-radio-button>
                  </a-radio-group>
                  <a-textarea v-if="!record.optionsSourceType" v-model:value="record.optionContent" :rows="2" placeholder="如 A:选项A&#10;B:选项B" @change="syncFieldOptions(record)" />
                  <template v-if="record.optionsSourceType === 'DB_QUERY'">
                    <span style="color:#888;font-size:12px;">选项由数据库管理中选中的数据源的当前版本加载，具体数据源请在画布中选择</span>
                  </template>
                  <template v-if="record.optionsSourceType === 'API_CALL'">
                    <span style="color:#888;font-size:12px;">选项由三方API管理中选中的API的当前版本加载，具体API请在画布中选择</span>
                  </template>
                  <template v-if="record.optionsSourceType === 'SCRIPT'">
                    <span style="color:#888;font-size:12px;">选项由脚本管理中选中的脚本的当前版本执行结果加载，具体脚本请在画布中选择</span>
                  </template>
                  <template v-if="record.optionsSourceType === 'TASK'">
                    <span style="color:#888;font-size:12px;">选项由任务管理中已发布到生产环境的任务列表加载</span>
                  </template>
                </div>
                <span v-else-if="column.dataIndex === 'optionConfig'">-</span>
                <a-switch v-else-if="column.dataIndex === 'requiredFlag'" v-model:checked="record.requiredBool" />
                <a-button v-else-if="column.dataIndex === 'op'" danger size="small" @click="removeFieldRow(record.__idx)">删除</a-button>
              </template>
            </a-table>
          </div>
        </div>
      </div>
      <div class="modal-actions">
        <a-button @click="metaVisible = false">取消</a-button>
        <a-button type="primary" @click="saveMeta">保存</a-button>
      </div>
    </a-modal>

  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { componentApi } from '../api/componentApi';

const keyword = ref('');
const loading = ref(false);
const rows = ref([]);
const formVisible = ref(false);
const formRef = ref();
const formRules = {
  componentCode: [{ required: true, message: '请输入组件编码', trigger: 'blur' }],
  componentName: [{ required: true, message: '请输入组件名称', trigger: 'blur' }],
  componentType: [{ required: true, message: '请选择组件分类', trigger: 'change' }]
};
const isEdit = ref(false);
const editingId = ref(null);
const formState = reactive({
  componentCode: '',
  componentName: '',
  componentType: undefined,
  version: '1.0.0',
  status: 1,
  description: ''
});


const metaVisible = ref(false);
const metaComponentId = ref(null);
const metaComponentType = ref('');
const metaTitle = ref('');
const metaFields = ref([]);
const dragIndex = ref(-1);

const sortedPreviewFields = computed(() => [...metaFields.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)));

const normalizeOptionList = (raw) => {
  if (Array.isArray(raw)) {
    return raw.map((item) => ({
      label: item.label ?? item.text ?? String(item.value ?? item),
      value: item.value ?? item.label ?? String(item)
    }));
  }
  if (!raw) return [];
  return String(raw)
    .split(/[\n,;|]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const parts = item.split(':');
      if (parts.length >= 2) {
        return { label: parts.slice(1).join(':').trim(), value: parts[0].trim() };
      }
      return { label: item, value: item };
    });
};

const stringifyOptionList = (options) => (Array.isArray(options) ? options.map((item) => `${item.value}:${item.label}`).join('\n') : '');
const normalizeWidgetProps = (raw) => {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return { options: normalizeOptionList(raw) };
  }
};
const stringifyWidgetProps = (raw) => {
  if (!raw) return '';
  if (typeof raw === 'string') return raw;
  try {
    return JSON.stringify(raw, null, 2);
  } catch (_) {
    return '';
  }
};
const normalizeFieldOptions = (record = {}) => {
  const widgetProps = normalizeWidgetProps(record.widgetPropsText || record.widgetProps);
  const rawOptions = widgetProps.options || widgetProps.optionList || record.optionContent || record.options || record.optionList || [];
  return normalizeOptionList(rawOptions);
};
const shouldPersistWidgetProps = (record = {}) => {
  const widgetType = String(record.widgetType || 'TEXTAREA').toUpperCase();
  return widgetType === 'MULTI_SELECT';
};

const safeMessage = (e, fallback) => {
  const raw = e?.message || '';
  if (!raw || raw.includes('### Error querying database') || raw.includes('SQLSyntaxErrorException') || raw.includes('java.sql.') || raw.length > 100) {
    return fallback;
  }
  return raw.startsWith('服务异常: ') ? raw.slice(5) : raw;
};

const valueTypeOptions = [
  { value: 'STRING', label: '字符串' },
  { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'DATE', label: '日期' },
  { value: 'JSON', label: 'JSON' }
];
const widgetTypeOptions = [
  { value: 'INPUT', label: '输入框' },
  { value: 'TEXTAREA', label: '文本域' },
  { value: 'NUMBER', label: '数字输入' },
  { value: 'SWITCH', label: '开关' },
  { value: 'SELECT', label: '下拉框' },
  { value: 'MULTI_SELECT', label: '多选框' },
  { value: 'DATE_PICKER', label: '日期选择器' }
];
const categoryOptions = ['数据接入', '数据处理', '流程控制'];


const columns = [
  { title: '组件编码', dataIndex: 'componentCode' },
  { title: '组件名称', dataIndex: 'componentName' },
  { title: '组件分类', dataIndex: 'componentType', width: 100 },
  { title: '大类', dataIndex: 'category', width: 100 },
  { title: '版本', dataIndex: 'version', width: 120 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', dataIndex: 'op', width: 300 }
];

const fieldColumns = [
  { title: '拖拽', dataIndex: 'drag', width: 70 },
  { title: '字段编码', dataIndex: 'fieldCode', width: 170 },
  { title: '字段名称', dataIndex: 'fieldName', width: 160 },
  { title: '数据类型', dataIndex: 'valueType', width: 150 },
  { title: '控件类型', dataIndex: 'widgetType', width: 180 },
  { title: '选项配置', dataIndex: 'optionConfig', width: 340 },
  { title: '必填', dataIndex: 'requiredFlag', width: 90 },
  { title: '操作', dataIndex: 'op', width: 90 }
];
const loadComponents = async () => {
  loading.value = true;
  try {
    const res = await componentApi.pageComponent({ current: 1, size: 100, keyword: keyword.value || undefined });
    const rawRecords = res?.data?.data?.records;
    if (!Array.isArray(rawRecords)) {
      rows.value = [];
      return;
    }
    rows.value = rawRecords.map((x) => ({
      id: x?.id,
      componentCode: x?.componentCode ?? x?.code ?? '',
      componentName: x?.componentName ?? x?.name ?? '',
      componentType: x?.componentType ?? x?.type ?? null,
      version: x?.version || '1.0.0',
      status: (x?.status === 1 || x?.status === '启用') ? 1 : 0,
      description: x?.description ?? x?.desc ?? '',
      createdTime: x?.createdTime ?? null
    }));
  } catch (e) {
    message.error(`组件加载失败：${safeMessage(e, '请稍后重试')}`);
    rows.value = [];
  } finally {
    loading.value = false;
  }
};

const resetSearch = async () => { keyword.value = ''; await loadComponents(); };

const openCreate = () => {
  isEdit.value = false;
  editingId.value = null;
  Object.assign(formState, {
    componentCode: '',
    componentName: '',
    componentType: undefined,
    version: '1.0.0',
    status: 1,
    description: ''
  });
  formVisible.value = true;
};

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(formState, {
    componentCode: row.componentCode || '',
    componentName: row.componentName || '',
    componentType: row.componentType || row.type || undefined,
    version: row.version || '1.0.0',
    status: row.status === 1 ? 1 : 0,
    description: row.description || row.desc || ''
  });
  formVisible.value = true;
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  const payload = {
    code: formState.componentCode,
    name: formState.componentName,
    type: formState.componentType,
    category: formState.componentType || null,
    version: formState.version || '1.0.0',
    status: formState.status === 1 ? '启用' : '禁用',
    desc: formState.description || ''
  };
  try {
    if (isEdit.value) {
      await componentApi.updateComponent(editingId.value, payload);
      message.success('编辑成功');
      formVisible.value = false;
      await loadComponents();
    } else {
      const res = await componentApi.createComponent(payload);
      const newId = res?.data?.data;
      message.success('新建成功，请配置组件字段');
      formVisible.value = false;
      await loadComponents();
      if (newId) {
        loadAndOpenMetaEditor(newId, formState.componentName, formState.componentType);
      }
    }
  } catch (e) {
    message.error(`保存失败：${e.message}`);
  }
};

const loadAndOpenMetaEditor = async (componentId, componentName, componentType) => {
  metaComponentId.value = componentId;
  metaTitle.value = componentName;
  metaComponentType.value = componentType || '';
  try {
    const res = await componentApi.getMeta(componentId);
    const data = res?.data?.data;
    const fields = Array.isArray(data?.fields)
      ? data.fields
      : (Array.isArray(data) ? data : []);

    const sorted = [...fields].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
    metaFields.value = sorted.map((f, i) => ({
      ...f,
      __idx: `${Date.now()}_${i}`,
      valueType: f.valueType || 'STRING',
      widgetType: f.widgetType || 'INPUT',
      optionContent: f.widgetProps ? stringifyOptionList(normalizeFieldOptions(f)) : '',
      widgetPropsText: stringifyWidgetProps(f.widgetProps),
      options: normalizeFieldOptions(f),
      optionsSourceType: f.optionsSourceType || (normalizeWidgetProps(f.widgetProps)?.optionsSource?.sourceType) || '',
      optionsLoading: false,
      defaultValue: f.defaultValue || '',
      requiredBool: Number(f.requiredFlag ?? 0) === 1,
      sortOrder: i + 1
    }));
    metaVisible.value = true;
  } catch (e) {
    message.error(`组件字段加载失败：${e.message || '请稍后重试'}`);
  }
};

const openMetaEditor = async (row) => {
  await loadAndOpenMetaEditor(row.id, row.componentName, row.componentType || row.type);
};

const addFieldRow = () => {
  metaFields.value.push({
    __idx: `${Date.now()}_${Math.random()}`,
    fieldCode: '',
    fieldName: '',
    valueType: 'STRING',
    widgetType: 'TEXTAREA',
    optionContent: '',
    widgetPropsText: '',
    options: [],
    optionsSourceType: '',
    optionsLoading: false,
    defaultValue: '',
    requiredBool: false,
    sortOrder: metaFields.value.length + 1
  });
};

const syncFieldOptions = (record) => {
  record.options = normalizeOptionList(record.optionContent || '');
  if (shouldPersistWidgetProps(record)) {
    const currentProps = normalizeWidgetProps(record.widgetPropsText);
    currentProps.options = record.options;
    record.widgetPropsText = stringifyWidgetProps(currentProps);
  }
};

const syncWidgetProps = (record) => {
  try {
    const parsed = normalizeWidgetProps(record.widgetPropsText);
    record.optionContent = stringifyOptionList(parsed.options || parsed.optionList || []);
    record.options = normalizeOptionList(parsed.options || parsed.optionList || []);
  } catch (_) {
    record.optionContent = '';
  }
};

const removeFieldRow = (idx) => { metaFields.value = metaFields.value.filter((x) => x.__idx !== idx); };

const onDragStart = (idx) => { dragIndex.value = idx; };
const onDropRow = (targetIdx) => {
  if (dragIndex.value < 0 || dragIndex.value === targetIdx) return;
  const arr = [...metaFields.value];
  const [moved] = arr.splice(dragIndex.value, 1);
  arr.splice(targetIdx, 0, moved);
  metaFields.value = arr.map((item, idx) => ({ ...item, sortOrder: idx + 1 }));
  dragIndex.value = targetIdx;
};

const buildWidgetProps = (f) => {
  const base = {};
  if (shouldPersistWidgetProps(f)) {
    if (f.widgetPropsText && String(f.widgetPropsText).trim()) {
      try {
        Object.assign(base, JSON.parse(String(f.widgetPropsText).trim()));
      } catch (_) {}
    }
    const options = normalizeOptionList(f.optionContent || f.options || []);
    if (options.length > 0) base.options = options;
    if (f.optionsSourceType) {
      base.optionsSource = {
        sourceType: f.optionsSourceType
      };
    } else {
      delete base.optionsSource;
    }
  }
  return Object.keys(base).length > 0 ? JSON.stringify(base) : '';
};

const saveFieldOrder = async () => {
  if (!metaComponentId.value) return;
  const payload = sortedPreviewFields.value.map((f, i) => ({
    fieldCode: String(f.fieldCode || '').trim(),
    fieldName: String(f.fieldName || '').trim(),
    valueType: (f.valueType || 'STRING').toUpperCase(),
    widgetType: (f.widgetType || 'INPUT').toUpperCase(),
    widgetProps: buildWidgetProps(f),
    defaultValue: String(f.defaultValue || '').trim(),
    requiredFlag: f.requiredBool ? 1 : 0,
    sortOrder: i + 1,
    description: String(f.description || '').trim()
  }));

  if (payload.some((f) => !f.fieldCode)) {
    throw new Error('字段编码不能为空');
  }
  if (payload.some((f) => !f.fieldName)) {
    throw new Error('字段名称不能为空');
  }

  const duplicateCodes = payload
    .map((f) => f.fieldCode)
    .filter((code, idx, arr) => arr.indexOf(code) !== idx)
    .filter((code, idx, arr) => arr.indexOf(code) === idx);
  if (duplicateCodes.length > 0) {
    throw new Error(`字段编码重复：${duplicateCodes.join('、')}`);
  }

  await componentApi.saveFields(metaComponentId.value, payload);
};

const saveMeta = async () => {
  try {
    await saveFieldOrder();
    metaVisible.value = false;
    message.success('字段保存成功');
  } catch (e) {
    message.error(`保存失败：${safeMessage(e, '请检查字段配置')}`);
  }
};


const toggleComponentStatus = async (record) => {
  try {
    await componentApi.toggleStatus(record.id);
    message.success(record.status === 1 ? '已禁用' : '已启用');
    await loadComponents();
  } catch (e) {
    message.error(`状态更新失败：${safeMessage(e, '请稍后重试')}`);
  }
};

const deleteComponent = async (record) => {
  try {
    await componentApi.deleteComponent(record.id);
    message.success('删除成功');
    await loadComponents();
  } catch (e) {
    message.error(`删除失败：${safeMessage(e, '请稍后重试')}`);
  }
};

loadComponents();
</script>

<style scoped>
.meta-layout {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: auto;
}
.meta-section {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  padding: 10px;
  overflow: hidden;
}
.meta-section-top {
  display: flex;
  flex-direction: column;
}
.meta-section-bottom {
  height: 250px;
  display: flex;
  flex-direction: column;
}
.meta-section-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.meta-table-wrap {
  overflow: hidden;
}

.meta-table-wrap :deep(.ant-select),
.meta-table-wrap :deep(.ant-input) {
  min-width: 0;
}

.meta-table-wrap :deep(.ant-select-selector),
.meta-table-wrap :deep(.ant-input),
.meta-table-wrap :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.meta-table-wrap :deep(.ant-select-dropdown) {
  min-width: 220px;
  z-index: 2000;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

/* scrollbar */
.meta-table-wrap::-webkit-scrollbar {
  height: 10px;
  width: 10px;
}
.meta-table-wrap::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 8px;
}
.meta-table-wrap::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
.meta-table-wrap::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 8px;
}
</style>