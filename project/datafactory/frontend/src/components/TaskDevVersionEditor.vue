<template>
  <a-modal
    :open="open"
    @update:open="(val) => { if (!val) emit('close'); }"
    @cancel="emit('close')"
    :closable="true"
    :title="readonly ? '版本详情查看' : '开发环境 - 版本编辑'"
    :width="1360"
    :footer="null"
    :centered="true"
    :maskClosable="false"
    :keyboard="false"
    destroyOnClose
    wrap-class-name="env-modal-fixed"
  >
    <div class="editor-wrap">
      <div class="top-bar">
        <div class="top-tip">{{ readonly ? '当前为只读模式' : '版本号将由系统自动生成' }}</div>
        <a-button @click="toggleJsonMode">{{ jsonMode ? '切换到可视化' : '切换到JSON' }}</a-button>
      </div>

      <template v-if="!jsonMode">
        <div class="editor-body">
          <div v-if="!readonly" class="node-panel">
            <div class="panel-title">组件库</div>
            <div class="panel-tip">每个流程必须且仅包含1个开始组件和1个结束组件，其他组件可按需添加。</div>
            <div v-for="group in groupedPalette" :key="group.code" class="palette-group">
              <div class="palette-group-title">{{ group.name }}</div>
              <div
                v-for="component in group.items"
                :key="(component.code || component.type) + component.label"
                class="node-item"
                :class="{ disabled: !isComponentAvailable(component.type) }"
                :draggable="isComponentAvailable(component.type)"
                @dragstart="isComponentAvailable(component.type) ? onDragStart(component, $event) : null"
              >
                <div class="node-item-title">{{ component.label }}</div>
              </div>
            </div>
            <div class="panel-tip" style="margin-top: 12px;">
              操作提示：双击节点编辑属性；右键节点删除；右键连线删除。
            </div>
          </div>

          <div class="canvas-panel" :class="{ 'full-width': readonly }">
            <div class="canvas-toolbar">
              <a-button size="small" @click="zoomIn">放大</a-button>
              <a-button size="small" @click="zoomOut">缩小</a-button>
              <a-button size="small" @click="resetZoom">重置</a-button>
              <a-button v-if="!readonly" size="small" @click="autoLayout">自动布局</a-button>
              <a-button v-if="!readonly" size="small" @click="clearCanvas">清空</a-button>
            </div>

            <div
              ref="canvasRef"
              class="canvas"
              tabindex="0"
              @click="canvasFocused = true"
              @focus="canvasFocused = true"
              @blur="canvasFocused = false"
              @dragover.prevent
              @drop="readonly ? null : onDrop($event)"
              @mousedown="startPan($event)"
              @mousemove="onCanvasMouseMove"
              @mouseup="onCanvasMouseUp"
              @mouseleave="onCanvasMouseUp"
              @wheel.prevent="onCanvasWheel"
            >
              <div class="graph-stage" :style="stageStyle">
                <svg class="edge-layer">
                  <defs>
                    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                      <path d="M0,0 L0,6 L9,3 z" fill="#5b8ff9" />
                    </marker>
                  </defs>
                  <line
                    v-for="e in edgeLines"
                    :key="e.id"
                    :x1="e.x1"
                    :y1="e.y1"
                    :x2="e.x2"
                    :y2="e.y2"
                    class="edge-line"
                    :class="{ selected: selectedEdgeId === e.id }"
                    marker-end="url(#arrow)"
                    @click.stop="selectEdge(e.id)"
                    @contextmenu.prevent.stop="readonly ? null : onEdgeContextMenu(e.id)"
                  />
                  <line v-if="draftEdge" :x1="draftEdge.x1" :y1="draftEdge.y1" :x2="draftEdge.x2" :y2="draftEdge.y2" class="edge-line draft" />
                </svg>

                <div
                  v-for="item in nodes"
                  :key="item.id"
                  class="flow-node"
                  :style="nodeStyle(item)"
                  @mousedown.stop="readonly ? null : startMove($event, item.id)"
                  @dblclick="openNodeEditor(item.id)"
                  @contextmenu.prevent="readonly ? null : onNodeContextMenu(item.id)"
                  :class="{ selected: selectedId === item.id }"
                >
                  {{ item.name }}
                  <span v-if="showInputPort(item)" class="port in" :class="{ hover: hoverInputNodeId === item.id }" title="输入" />
                  <span
                    v-if="showOutputPort(item)"
                    class="port out"
                    title="输出"
                    @mousedown.stop.prevent="startEdgeDrag($event, item.id)"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <a-textarea v-model:value="jsonText" :rows="26" class="json-editor" :disabled="readonly" />
      </template>

      <div class="footer-actions">
        <a-button @click="emit('close')">{{ readonly ? '关闭' : '取消' }}</a-button>
        <a-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</a-button>
      </div>
    </div>

    <a-modal
      v-model:open="nodeEditorVisible"
      :title="'组件属性'"
      :width="1120"
      :footer="null"
      :centered="true"
      :maskClosable="false"
      destroyOnClose
      @cancel="onNodeEditorCancel"
    >
      <div v-if="selectedNode" class="node-editor-form">
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="组件ID"><a-input :value="selectedNode.id" disabled /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="组件名称"><a-input v-model:value="selectedNode.name" :disabled="readonly" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="组件类型"><a-input :value="typeName(selectedNode.type)" disabled /></a-form-item></a-col>
        </a-row>

        <a-tabs v-model:activeKey="nodeEditorTab">
          <a-tab-pane key="field" tab="组件字段">
            <div class="field-preview-grid">
              <div v-for="record in sortedNodeFields" :key="record.fieldCode" class="field-preview-card">
                <div class="field-preview-title">{{ record.fieldName || record.fieldCode }}（{{ valueTypeLabelMap[record.valueType || 'STRING'] || (record.valueType || 'STRING') }}）</div>
                <div class="field-preview-control">
                  <a-switch
                    v-if="resolveWidgetType(record) === 'SWITCH'"
                    v-model:checked="record.fieldValue"
                    :disabled="readonly"
                  />
                  <a-textarea
                    v-else-if="resolveWidgetType(record) === 'TEXTAREA'"
                    v-model:value="record.fieldValue"
                    :disabled="readonly"
                    :rows="2"
                  />
                  <template v-else-if="resolveWidgetType(record) === 'MULTI_SELECT'">
                    <a-select
                      v-model:value="record.fieldValue"
                      mode="multiple"
                      :disabled="readonly"
                      style="width:100%"
                      :options="resolveWidgetOptions(record)"
                      :placeholder="readonly ? '-' : '请选择'"
                      :loading="record._optionsLoading"
                    />
                  </template>
                  <a-date-picker
                    v-else-if="resolveWidgetType(record) === 'DATE_PICKER'"
                    v-model:value="record.fieldValue"
                    :disabled="readonly"
                    style="width: 100%"
                    value-format="YYYY-MM-DD"
                  />
                  <a-textarea v-else v-model:value="record.fieldValue" :disabled="readonly" :rows="1" />
                </div>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="input" tab="输入参数">
            <a-space style="margin-bottom:8px;">
              <a-button v-if="!readonly" type="dashed" size="small" @click="addNodeInputParam">+ 新增输入参数</a-button>
            </a-space>
            <a-table :columns="unifiedParamColumns" :data-source="nodeInputParams" :pagination="false" row-key="__rowId" :scroll="{ x: 'max-content' }">
              <template #bodyCell="{ column, record }">
                <a-input v-if="column.dataIndex === 'paramCode'" v-model:value="record.paramCode" :disabled="readonly" />
                <a-input v-else-if="column.dataIndex === 'paramName'" v-model:value="record.paramName" :disabled="readonly" />
                <a-select v-else-if="column.dataIndex === 'dataType'" v-model:value="record.dataType" :disabled="readonly" style="min-width:120px">
                  <a-select-option v-for="item in valueTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
                </a-select>
                <a-select v-else-if="column.dataIndex === 'sourceType'" v-model:value="record.sourceType" :disabled="readonly" style="min-width:160px" @change="onParamSourceTypeChange(record)">
                  <a-select-option v-for="item in sourceTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
                </a-select>
                <template v-else-if="column.dataIndex === 'sourceValue'">
                  <a-tree-select
                    v-if="record.sourceType === 'UPSTREAM_OUTPUT'"
                    :value="normalizeSourceValueForTree(record.sourceValue)"
                    @update:value="(val) => record.sourceValue = parseUpstreamValue(val)"
                    :disabled="readonly"
                    style="width:100%"
                    :tree-data="buildUpstreamTreeData(selectedNode?.id)"
                    :tree-default-expand-all="true"
                    placeholder="选择上游输出字段"
                  />
                  <a-input v-else v-model:value="record.sourceValue" :disabled="readonly" placeholder="输入常量值或表达式" />
                </template>
                <a-switch v-else-if="column.dataIndex === 'requiredFlag'" v-model:checked="record.requiredBool" :disabled="readonly" />
                <a-button v-else-if="column.dataIndex === 'op'" danger size="small" :disabled="readonly" @click="removeNodeInputParam(record.__rowId)">删除</a-button>
              </template>
            </a-table>
          </a-tab-pane>
          <a-tab-pane key="output" tab="输出参数">
            <a-space style="margin-bottom:8px;">
              <a-button v-if="!readonly" type="dashed" size="small" @click="addNodeOutputParam">+ 新增输出参数</a-button>
            </a-space>
            <!-- 非START/END节点：输出参数可编辑；START/END节点：输出参数只读（由输入参数同步） -->
            <template v-if="isStartOrEndNode()">
              <a-table :columns="syncedOutputColumns" :data-source="displayOutputParams" :pagination="false" row-key="__rowId" :scroll="{ x: 'max-content' }">
                <template #bodyCell="{ column, record }">
                  <a-input v-if="column.dataIndex === 'paramCode'" :value="record.paramCode" disabled />
                  <span v-else-if="column.dataIndex === 'paramName'" class="param-fixed-text">{{ record.paramName || '-' }}</span>
                  <span v-else-if="column.dataIndex === 'dataType'" class="param-fixed-text">{{ valueTypeLabelMap[record.dataType] || record.dataType }}</span>
                  <span v-else-if="column.dataIndex === 'sourceType'" class="param-fixed-text">{{ sourceTypeLabelMap[record.sourceType] || record.sourceType || '常量' }}</span>
                  <span v-else-if="column.dataIndex === 'sourceValue'" class="param-fixed-text">{{ formatSourceValue(record.sourceValue) }}</span>
                  <a-switch v-else-if="column.dataIndex === 'requiredFlag'" v-model:checked="record.requiredBool" :disabled="readonly" />
                </template>
              </a-table>
            </template>
            <template v-else>
              <a-table :columns="outputParamColumns" :data-source="nodeOutputParams" :pagination="false" row-key="__rowId" :scroll="{ x: 'max-content' }">
                <template #bodyCell="{ column, record }">
                  <a-input v-if="column.dataIndex === 'paramCode'" v-model:value="record.paramCode" :disabled="readonly" />
                  <a-input v-else-if="column.dataIndex === 'paramName'" v-model:value="record.paramName" :disabled="readonly" />
                  <a-select v-else-if="column.dataIndex === 'dataType'" v-model:value="record.dataType" :disabled="readonly" style="min-width:120px">
                    <a-select-option v-for="item in valueTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
                  </a-select>
                  <a-switch v-else-if="column.dataIndex === 'requiredFlag'" v-model:checked="record.requiredBool" :disabled="readonly" />
                  <a-button v-else-if="column.dataIndex === 'op'" danger size="small" :disabled="readonly" @click="removeNodeOutputParam(record.__rowId)">删除</a-button>
                </template>
              </a-table>
            </template>
          </a-tab-pane>
        </a-tabs>
      </div>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { componentApi } from '../api/componentApi';
import { taskApi } from '../api/task';
import { Modal, message } from 'ant-design-vue';

const props = defineProps({ open: Boolean, versionData: [Object, String], initialDsl: [Object, String], environment: String, readonly: Boolean });

// 判断节点是否为开始/结束（type可能丢失，用componentCode兜底）
function isStartNode(n) { return n && (n.type === 'START' || String(n.componentCode || '').toUpperCase().includes('START')); }
function isEndNode(n)   { return n && (n.type === 'END'   || String(n.componentCode || '').toUpperCase().includes('END')); }
const emit = defineEmits(['save', 'close']);

const NODE_WIDTH = 160;
const NODE_HEIGHT = 56;

const jsonMode = ref(false);
const canvasRef = ref(null);
const selectedId = ref(null);
const selectedEdgeId = ref(null);
const canvasFocused = ref(false);
const nodeEditorVisible = ref(false);
const nodeEditorTab = ref('field');
const componentPalette = ref([]);
const componentMetaMap = ref({});
const canvasDatasourceOptions = ref([]);
const canvasApiOptions = ref([]);
const canvasScriptOptions = ref([]);
const componentTypeNameMap = {
  START: '开始组件',
  END: '结束组件',
  DB: '数据库组件',
  BRANCH: '条件分支组件',
  COMMON_TASK: '通用任务组件',
  SCRIPT: '脚本组件',
  API: '接口组件',
  FILTER: '数据过滤组件'
};

const valueTypeOptions = [
  { value: 'STRING', label: '字符串' },
  { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'JSON', label: 'JSON' }
];
const widgetTypeOptions = [
  { value: 'INPUT', label: '单行输入框' },
  { value: 'TEXTAREA', label: '多行文本框' },
  { value: 'NUMBER', label: '数字输入框' },
  { value: 'SWITCH', label: '开关' },
  { value: 'SELECT', label: '下拉选择' },
  { value: 'MULTI_SELECT', label: '多选下拉' },
  { value: 'DATE_PICKER', label: '日期选择器' }
];
const sourceTypeOptions = [
  { value: 'CONST', label: '常量' },
  { value: 'UPSTREAM_OUTPUT', label: '上游输出' },
  { value: 'EXPRESSION', label: '表达式' }
];

const valueTypeLabelMap = valueTypeOptions.reduce((acc, item) => { acc[item.value] = item.label; return acc; }, {});
const widgetTypeLabelMap = widgetTypeOptions.reduce((acc, item) => { acc[item.value] = item.label; return acc; }, {});
const sourceTypeLabelMap = sourceTypeOptions.reduce((acc, item) => { acc[item.value] = item.label; return acc; }, {});

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
      if (parts.length >= 2) return { value: parts[0].trim(), label: parts.slice(1).join(':').trim() };
      return { value: item, label: item };
    });
};
const parseWidgetProps = (record = {}) => {
  if (Array.isArray(record.options)) return record.options;
  const raw = record.widgetProps || record.optionContent || '';
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (Array.isArray(parsed)) return parsed;
    if (parsed?.options && Array.isArray(parsed.options)) return parsed.options;
    if (parsed?.optionList && Array.isArray(parsed.optionList)) return parsed.optionList;
    if (parsed?.options) return normalizeOptionList(parsed.options);
  } catch (_) {}
  return normalizeOptionList(raw);
};
const getFieldOptions = (record = {}) => parseWidgetProps(record);
const getFieldInputComponent = (record = {}) => {
  const widgetType = String(record.widgetType || 'TEXTAREA').toUpperCase();
  if (widgetType === 'SWITCH') return 'switch';
  if (widgetType === 'NUMBER') return 'number';
  if (widgetType === 'TEXTAREA') return 'textarea';
  if (widgetType === 'DATE_PICKER' || widgetType === 'DATETIME_PICKER') return 'date';
  return 'input';
};
const groupedPalette = computed(() => {
  const groups = {};
  componentPalette.value.forEach((item) => {
    const code = item.groupCode || 'UNCLASSIFIED';
    if (!groups[code]) {
      groups[code] = {
        code,
        name: item.groupName || componentTypeNameMap[code] || code,
        items: []
      };
    }
    groups[code].items.push(item);
  });
  return Object.values(groups);
});

const nodes = ref([]);
const edges = ref([]);
const jsonText = ref('');
const dragPayload = ref(null);
const scale = ref(1);
const pan = ref({ x: 0, y: 0 });
const movingState = ref(null);
const panningState = ref(null);
const edgeDragState = ref(null);
const draftEdge = ref(null);
const hoverInputNodeId = ref(null);
const saving = ref(false);

const stageStyle = computed(() => ({
  transform: `translate(${pan.value.x}px, ${pan.value.y}px) scale(${scale.value})`,
  transformOrigin: '0 0'
}));

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedId.value) || null);
const typeName = (t) => componentPalette.value.find((c) => c.type === t)?.label || t;

const nodeFields = ref([]);
const nodeInputParams = ref([]);
const nodeOutputParams = ref([]);
// 过滤模式字段分组
const FILTER_MODE_FIELDS = {
  EXPRESSION: new Set(['filterMode', 'sourceNodeId', 'expression', 'result_var']),
  SIMPLE:     new Set(['filterMode', 'sourceNodeId', 'condition_field', 'condition_op', 'condition_value', 'result_var']),
  COLUMN:     new Set(['filterMode', 'sourceNodeId', 'columns', 'result_var']),
};

const filterModeKey = ref(0);

const getCurrentFilterMode = () => {
  // 访问 filterModeKey 确保重新计算
  void filterModeKey.value;
  const fv = nodeFields.value.find(f => f.fieldCode === 'filterMode');
  return fv?.fieldValue || '';
};

const visibleNodeFields = computed(() => {
  const raw = [...nodeFields.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  if (selectedNode.value?.componentCode !== 'COMP_FILTER') return raw;
  const modeFields = FILTER_MODE_FIELDS[getCurrentFilterMode()];
  if (!modeFields) return raw;
  return raw.filter(f => modeFields.has(f.fieldCode));
});

const sortedNodeFields = computed(() => visibleNodeFields.value);

// 监听过滤模式变化，强制刷新字段可见性
watch(nodeFields, (newFields) => {
  const fv = newFields.find(f => f.fieldCode === 'filterMode');
  if (fv) filterModeKey.value++;
}, { deep: true });

const isStartOrEndNode = () => {
  const node = selectedNode.value;
  return node && (isStartNode(node) || isEndNode(node));
};

// 将 nodeInputParams 同步到 nodeOutputParams（仅 START/END 节点）
const syncOutputFromInput = () => {
  if (!isStartOrEndNode()) return;
  nodeOutputParams.value = nodeInputParams.value.map((item, idx) => ({
    __rowId: `synced_out_${idx}`,
    paramName: item.paramName,
    paramCode: item.paramCode,
    dataType: item.dataType,
    sourceType: item.sourceType || 'CONST',
    sourceValue: typeof item.sourceValue === 'string' ? item.sourceValue : (item.sourceValue ? item.sourceValue : ''),
    defaultValue: typeof item.sourceValue === 'string' ? item.sourceValue : '',
    requiredBool: Number(item.requiredFlag || item.requiredBool || 0) === 1,
    readonlyParam: true
  }));
};

// 监听输入参数变化，自动同步输出参数
watch(nodeInputParams, () => syncOutputFromInput(), { deep: true });

// 格式化 sourceValue 展示：对象类型转为 JSON 字符串，空值显示 '-'
const formatSourceValue = (val) => {
  if (val === undefined || val === null || val === '') return '-';
  if (typeof val === 'object') return JSON.stringify(val);
  return String(val);
};

// 输出参数直接使用 nodeOutputParams（START/END 已通过 watcher 实时同步）
const displayOutputParams = computed(() => nodeOutputParams.value);

// 同步 START/END 节点的 outputParams，使其与 inputParams 保持一致
const syncStartEndOutputParams = () => {
  nodes.value.forEach(node => {
    if (isStartNode(node) || isEndNode(node)) {
      const inputParams = Array.isArray(node.inputParams) ? node.inputParams : [];
      node.outputParams = inputParams.map((item) => ({
        paramCode: item.paramCode,
        paramName: item.paramName,
        dataType: item.dataType,
        requiredFlag: item.requiredFlag || 0,
        sourceType: item.sourceType || 'CONST',
        sourceValue: typeof item.sourceValue === 'string' ? item.sourceValue : (item.sourceValue ? item.sourceValue : ''),
        defaultValue: typeof item.sourceValue === 'string' ? item.sourceValue : ''
      }));
    }
  });
};

const resolveWidgetType = (record = {}) => String(record.widgetType || 'TEXTAREA').toUpperCase();

const normalizeWidgetProps = (raw) => {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch (_) {
    return { options: normalizeOptionList(raw) };
  }
};

const resolveWidgetOptions = (record = {}) => {
  const widgetProps = normalizeWidgetProps(record.widgetProps);
  const rawOptions = widgetProps.options || widgetProps.optionList || record.optionList || record.options || record.optionContent || [];
  if (Array.isArray(record._resolvedOptions) && record._resolvedOptions.length > 0) {
    return record._resolvedOptions;
  }
  return normalizeOptionList(rawOptions);
};

const hasDynamicOptionSource = (record = {}) => {
  const widgetProps = normalizeWidgetProps(record.widgetProps);
  const src = widgetProps.optionsSource;
  return !!(src && src.sourceType);
};

const getDynamicSourceType = (record = {}) => {
  const widgetProps = normalizeWidgetProps(record.widgetProps);
  return widgetProps.optionsSource?.sourceType || '';
};

const loadCanvasOptions = async () => {
  try {
    const [dsRes, apiRes, scriptRes] = await Promise.all([
      componentApi.listDatasources(),
      componentApi.listExternalApis(),
      componentApi.listScripts()
    ]);
    canvasDatasourceOptions.value = dsRes?.data?.data || [];
    canvasApiOptions.value = apiRes?.data?.data || [];
    canvasScriptOptions.value = scriptRes?.data?.data || [];
  } catch (_) {
    canvasDatasourceOptions.value = [];
    canvasApiOptions.value = [];
    canvasScriptOptions.value = [];
  }
};

const loadDynamicOptions = async (record) => {
  const widgetProps = normalizeWidgetProps(record.widgetProps);
  const src = widgetProps.optionsSource;
  if (!src || !src.sourceType) return;
  record._optionsLoading = true;
  try {
    const payload = {
      sourceType: src.sourceType,
      environment: src.sourceType === 'SCRIPT' ? 'PROD' : (props.environment || props.versionData?.environment || 'DEV')
    };
    if (src.sourceType === 'DB_QUERY') {
      if (src.dbVersionId) {
        payload.dbVersionId = src.dbVersionId;
      } else if (src.dbId) {
        payload.dbId = src.dbId;
      }
      payload.query = src.query || '';
    } else if (src.sourceType === 'API_CALL') {
      if (src.apiVersionId) {
        payload.apiVersionId = src.apiVersionId;
      } else if (src.apiId) {
        payload.apiId = src.apiId;
      }
    } else if (src.sourceType === 'SCRIPT') {
      if (src.scriptVersionId) {
        payload.scriptVersionId = src.scriptVersionId;
      } else if (src.scriptId) {
        payload.scriptId = src.scriptId;
      }
    } else if (src.sourceType === 'TASK') {
      // TASK 源无需额外参数，直接加载已发布任务列表
    }
    const res = await componentApi.resolveFieldOptions(payload);
    const items = res?.data?.data || [];
    if (items.length === 0) {
      message.info('查询无结果');
      return;
    }
    record._resolvedOptions = items.map((item) => ({
      label: item.label ?? item.name ?? item.title ?? String(item.value ?? item),
      value: item.value ?? item.id ?? item.code ?? ''
    }));
    record.options = record._resolvedOptions;
    message.success('选项已加载');
  } catch (e) {
    const errMsg = e?.message || '请检查动态数据源配置';
    message.error(`选项加载失败: ${errMsg}`);
  } finally {
    record._optionsLoading = false;
  }
};

const resolveNodeInstanceId = async (nodeId) => {
  const taskId = props.versionData?.taskId || props.initialDsl?.taskId || null;
  if (!taskId || !nodeId) return null;
  try {
    const res = await taskApi.getNodeFields(taskId, nodeId);
    return Array.isArray(res.data?.data) ? res.data.data : [];
  } catch (_) {
    return null;
  }
};

const normalizeFieldValue = (record = {}) => {
  const widgetType = resolveWidgetType(record);
  const rawValue = record.fieldValue ?? record.defaultValue ?? '';
  if (widgetType === 'SWITCH') return Boolean(Number(rawValue) === 1 || rawValue === true || rawValue === 'true');
  if (widgetType === 'NUMBER') return rawValue === '' || rawValue === null || rawValue === undefined ? undefined : Number(rawValue);
  if (widgetType === 'MULTI_SELECT') {
    if (Array.isArray(rawValue)) return rawValue;
    if (typeof rawValue === 'string' && rawValue.trim()) return rawValue.split(',').map((item) => item.trim()).filter(Boolean);
    return [];
  }
  return rawValue;
};

const formatFieldDefaultValue = (record = {}) => {
  const widgetType = resolveWidgetType(record);
  const value = record.defaultValue;
  if (value === undefined || value === null || value === '') return '-';
  if (widgetType === 'MULTI_SELECT') {
    return Array.isArray(value) ? value.join('、') : String(value);
  }
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const unifiedParamColumns = [
  { title: '参数编码', dataIndex: 'paramCode', width: 200 },
  { title: '参数名称', dataIndex: 'paramName', width: 180 },
  { title: '数据类型', dataIndex: 'dataType', width: 120 },
  { title: '参数来源', dataIndex: 'sourceType', width: 150 },
  { title: '来源值', dataIndex: 'sourceValue', width: 240 },
  { title: '必填', dataIndex: 'requiredFlag', width: 90 },
  { title: '操作', dataIndex: 'op', width: 80 }
];
// 输出参数列：不需要参数来源/来源值（输出只是白名单，值由插件逻辑决定）
const outputParamColumnsDef = [
  { title: '参数编码', dataIndex: 'paramCode', width: 200 },
  { title: '参数名称', dataIndex: 'paramName', width: 180 },
  { title: '数据类型', dataIndex: 'dataType', width: 120 },
  { title: '必填', dataIndex: 'requiredFlag', width: 90 },
  { title: '操作', dataIndex: 'op', width: 80 }
];
// START/END 节点的输出参数列：隐藏操作列，参数由输入自动同步
const syncedOutputColumns = unifiedParamColumns.filter(
  c => c.dataIndex !== 'op'
);
const outputParamColumns = computed(() =>
  isStartOrEndNode() ? syncedOutputColumns : outputParamColumnsDef
);

const toStagePoint = (clientX, clientY) => {
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return { x: 0, y: 0 };
  return {
    x: (clientX - rect.left - pan.value.x) / scale.value,
    y: (clientY - rect.top - pan.value.y) / scale.value
  };
};

const getEdgeEndpoint = (edge, side) => {
  const endpoint = edge?.[side];
  if (endpoint && typeof endpoint === 'object') {
    return {
      nodeId: endpoint.nodeId || endpoint.id || null,
      port: endpoint.port || (side === 'source' ? 'out' : 'in')
    };
  }
  const legacyNodeId = side === 'source'
    ? edge?.sourceNodeId || null
    : edge?.targetNodeId || null;
  return legacyNodeId
    ? { nodeId: legacyNodeId, port: side === 'source' ? 'out' : 'in' }
    : { nodeId: null, port: side === 'source' ? 'out' : 'in' };
};

const getEdgeNodeId = (edge, side) => getEdgeEndpoint(edge, side)?.nodeId || null;
const getEdgePort = (edge, side) => getEdgeEndpoint(edge, side)?.port || (side === 'source' ? 'out' : 'in');
const buildEdgeModel = (edge) => ({
  id: edge?.id || `e_${Date.now()}_${Math.floor(Math.random() * 9999)}`,
  source: edge?.source && typeof edge.source === 'object'
    ? { nodeId: edge.source.nodeId || edge.source.id || null, port: edge.source.port || 'out' }
    : getEdgeEndpoint(edge, 'source'),
  target: edge?.target && typeof edge.target === 'object'
    ? { nodeId: edge.target.nodeId || edge.target.id || null, port: edge.target.port || 'in' }
    : getEdgeEndpoint(edge, 'target')
});
const getPortPoint = (node, port) => {
  if (!node) return { x: 0, y: 0 };
  if (port === 'in') return { x: node.x + NODE_WIDTH / 2, y: node.y };
  return { x: node.x + NODE_WIDTH / 2, y: node.y + NODE_HEIGHT };
};
const edgeLines = computed(() => edges.value.map((edge) => {
  const normalized = buildEdgeModel(edge);
  const sourceNode = nodes.value.find((n) => n.id === normalized.source.nodeId);
  const targetNode = nodes.value.find((n) => n.id === normalized.target.nodeId);
  if (!sourceNode || !targetNode) return { id: normalized.id, x1: 0, y1: 0, x2: 0, y2: 0 };
  const start = getPortPoint(sourceNode, normalized.source.port);
  const end = getPortPoint(targetNode, normalized.target.port);
  return {
    id: normalized.id,
    x1: start.x,
    y1: start.y,
    x2: end.x,
    y2: end.y
  };
}));

const nodeStyle = (item) => ({ left: `${item.x}px`, top: `${item.y}px`, width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` });

const isComponentAvailable = (type) => {
  if (type === 'START') return !nodes.value.some((n) => isStartNode(n));
  if (type === 'END') return !nodes.value.some((n) => isEndNode(n));
  return true;
};

const showInputPort = (node) => node.type !== 'START';
const showOutputPort = (node) => !props.readonly && node.type !== 'END';

const normalizeStatusToNumber = (status) => {
  if (status === 1 || status === '1' || status === '启用' || status === true) return 1;
  return 0;
};

const normalizeComponentType = (rawType, componentCode, componentName) => {
  const type = String(rawType || '').toUpperCase();
  const code = String(componentCode || '').toUpperCase();
  const name = String(componentName || '');
  if (type === 'START' || code.includes('START') || name.includes('开始')) return 'START';
  if (type === 'END' || code.includes('END') || name.includes('结束')) return 'END';
  return type || code || 'UNKNOWN';
};

const loadSelectableResources = async () => {
  // 参数来源已精简为 CONST/UPSTREAM_OUTPUT/EXPRESSION，不再需要预先加载资源列表
};

const loadPalette = async () => {
  try {
    const compRes = await componentApi.pageComponent({ current: 1, size: 1000 });
    const comps = (compRes.data?.data?.records || []).filter((item) => normalizeStatusToNumber(item.status ?? 1) === 1);

    componentMetaMap.value = comps.reduce((acc, item) => {
      const keyByCode = item.code || item.componentCode;
      const keyByType = item.type || item.componentType;
      if (keyByCode) acc[`code:${keyByCode}`] = item;
      if (keyByType && !acc[`type:${keyByType}`]) acc[`type:${keyByType}`] = item;
      return acc;
    }, {});

    componentPalette.value = comps.map((comp) => {
      const componentCode = comp.code || comp.componentCode;
      const typeCode = String(comp.type || comp.componentType || '').toUpperCase();
      const componentName = comp.componentName || comp.name || comp.label || componentCode;
      return {
        id: comp.id,
        type: typeCode,
        label: componentName,
        code: componentCode,
        componentId: comp.id,
        groupCode: typeCode,
        groupName: componentTypeNameMap[typeCode] || typeCode
      };
    }).filter((item) => item.type && item.label);
  } catch (_) {}
};

const onDragStart = (component, event) => {
  dragPayload.value = component;
  if (event?.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.dropEffect = 'copy';
    event.dataTransfer.setData('text/plain', JSON.stringify({
      type: component.type,
      code: component.code,
      label: component.label
    }));
  }
};

const onDrop = (event) => {
  let payload = dragPayload.value;
  if (!payload && event?.dataTransfer) {
    try {
      const text = event.dataTransfer.getData('text/plain');
      if (text) payload = JSON.parse(text);
    } catch (_) {}
  }
  if (!payload) return;

  const p = toStagePoint(event.clientX, event.clientY);
  const nodeId = `n_${Date.now()}_${Math.floor(Math.random() * 9999)}`;
  const matched = componentPalette.value.find(item => item.type === payload.type && item.label === payload.label);
  const normalizedType = normalizeComponentType(payload.type, payload.code, payload.label);
  nodes.value.push({
    id: nodeId,
    name: payload.label,
    type: normalizedType,
    componentCode: payload.code,
    componentId: payload.componentId || matched?.componentId || matched?.id,
    x: Math.max(0, p.x - NODE_WIDTH / 2),
    y: Math.max(0, p.y - NODE_HEIGHT / 2),
    fieldValues: {},
    inputParams: [],
    outputParams: []
  });
  dragPayload.value = null;
  jsonText.value = JSON.stringify(buildPersistedDslPayload(), null, 2);
};

const startMove = (event, nodeId) => {
  canvasFocused.value = true;
  selectedId.value = nodeId;
  selectedEdgeId.value = null;
  const node = nodes.value.find((n) => n.id === nodeId);
  if (!node) return;
  const p = toStagePoint(event.clientX, event.clientY);
  movingState.value = { nodeId, offsetX: p.x - node.x, offsetY: p.y - node.y };
};

const startPan = (event) => {
  canvasFocused.value = true;
  selectedId.value = null;
  selectedEdgeId.value = null;
  if (event.target?.closest('.flow-node')) return;
  if (edgeDragState.value) return;
  panningState.value = { startX: event.clientX, startY: event.clientY, baseX: pan.value.x, baseY: pan.value.y };
};

const startEdgeDrag = (event, nodeId) => {
  const node = nodes.value.find((n) => n.id === nodeId);
  if (!node) return;
  const start = { x: node.x + NODE_WIDTH / 2, y: node.y + NODE_HEIGHT };
  edgeDragState.value = { sourceNodeId: nodeId };
  draftEdge.value = { x1: start.x, y1: start.y, x2: start.x, y2: start.y };
};

const onCanvasMouseMove = (event) => {
  if (movingState.value) {
    const node = nodes.value.find((n) => n.id === movingState.value.nodeId);
    if (!node) return;
    const p = toStagePoint(event.clientX, event.clientY);
    node.x = Math.max(0, p.x - movingState.value.offsetX);
    node.y = Math.max(0, p.y - movingState.value.offsetY);
  }

  if (panningState.value) {
    pan.value.x = panningState.value.baseX + (event.clientX - panningState.value.startX);
    pan.value.y = panningState.value.baseY + (event.clientY - panningState.value.startY);
  }

  if (edgeDragState.value && draftEdge.value) {
    const p = toStagePoint(event.clientX, event.clientY);
    draftEdge.value.x2 = p.x;
    draftEdge.value.y2 = p.y;

    const PORT_HIT_RADIUS = 14;
    const hoverTarget = nodes.value.find((n) => {
      if (n.id === edgeDragState.value.sourceNodeId) return false;
      if (!showInputPort(n)) return false;
      const portX = n.x + NODE_WIDTH / 2;
      const portY = n.y;
      return Math.abs(p.x - portX) <= PORT_HIT_RADIUS && Math.abs(p.y - portY) <= PORT_HIT_RADIUS;
    });
    hoverInputNodeId.value = hoverTarget?.id || null;
  } else {
    hoverInputNodeId.value = null;
  }
};

const onCanvasMouseUp = (event) => {
  movingState.value = null;
  panningState.value = null;

  if (!edgeDragState.value) return;
  const p = toStagePoint(event.clientX, event.clientY);
  const PORT_HIT_RADIUS = 14;
  const target = nodes.value.find((n) => {
    if (n.id === edgeDragState.value.sourceNodeId) return false;
    if (!showInputPort(n)) return false;
    const portX = n.x + NODE_WIDTH / 2;
    const portY = n.y;
    return Math.abs(p.x - portX) <= PORT_HIT_RADIUS && Math.abs(p.y - portY) <= PORT_HIT_RADIUS;
  });

  if (target) {
    const sourceNode = nodes.value.find((n) => n.id === edgeDragState.value.sourceNodeId);
    if (sourceNode?.type === 'END') {
      message.warning('结束组件不能作为连线起点');
    } else if (target.type === 'START') {
      message.warning('开始组件不能作为连线终点');
    } else {
      const exists = edges.value.some((e) => getEdgeNodeId(e, 'source') === edgeDragState.value.sourceNodeId && getEdgeNodeId(e, 'target') === target.id);
      if (!exists) {
        edges.value.push({
          id: `e_${Date.now()}_${Math.floor(Math.random() * 9999)}`,
          source: { nodeId: edgeDragState.value.sourceNodeId, port: 'out' },
          target: { nodeId: target.id, port: 'in' }
        });
      }
    }
  }

  edgeDragState.value = null;
  draftEdge.value = null;
  hoverInputNodeId.value = null;
};

const onCanvasWheel = (event) => {
  const delta = event.deltaY > 0 ? -0.1 : 0.1;
  scale.value = Math.min(2, Math.max(0.5, Number((scale.value + delta).toFixed(2))));
};

const zoomIn = () => { scale.value = Math.min(2, Number((scale.value + 0.1).toFixed(2))); };
const zoomOut = () => { scale.value = Math.max(0.5, Number((scale.value - 0.1).toFixed(2))); };
const resetZoom = () => { scale.value = 1; pan.value = { x: 0, y: 0 }; };

const autoLayout = () => {
  if (!edges.value.length) {
    message.info('当前无连线，暂不执行自动布局');
    return;
  }

  const indegree = {};
  const nextMap = {};
  nodes.value.forEach((n) => {
    indegree[n.id] = 0;
    nextMap[n.id] = [];
  });
  edges.value.forEach((edge) => {
    const sourceId = getEdgeNodeId(edge, 'source');
    const targetId = getEdgeNodeId(edge, 'target');
    if (targetId !== null && indegree[targetId] !== undefined) indegree[targetId] += 1;
    if (sourceId && targetId && nextMap[sourceId]) nextMap[sourceId].push(targetId);
  });

  const queue = nodes.value.filter((n) => indegree[n.id] === 0).map((n) => ({ id: n.id, level: 0 }));
  const levels = {};
  while (queue.length) {
    const { id, level } = queue.shift();
    if (levels[id] !== undefined && levels[id] <= level) continue;
    levels[id] = level;
    (nextMap[id] || []).forEach((toId) => {
      queue.push({ id: toId, level: level + 1 });
    });
  }

  const levelBuckets = {};
  nodes.value.forEach((n) => {
    const lv = levels[n.id] ?? 0;
    if (!levelBuckets[lv]) levelBuckets[lv] = [];
    levelBuckets[lv].push(n);
  });

  const sortedLevels = Object.keys(levelBuckets).map(Number).sort((a, b) => a - b);
  const centerX = 600;
  const levelGapY = 140;
  const gapX = 220;

  sortedLevels.forEach((lv) => {
    const bucket = levelBuckets[lv];
    const totalWidth = (bucket.length - 1) * gapX;
    bucket.forEach((node, idx) => {
      node.x = centerX - totalWidth / 2 + idx * gapX - NODE_WIDTH / 2;
      node.y = 60 + lv * levelGapY;
    });
  });
};

const clearCanvas = () => {
  nodes.value = [];
  edges.value = [];
  selectedId.value = null;
  selectedEdgeId.value = null;
};

const pruneDanglingEdges = () => {
  const nodeIdSet = new Set(nodes.value.map((node) => node.id));
  edges.value = edges.value.filter((edge) => {
    const sourceNodeId = getEdgeNodeId(edge, 'source');
    const targetNodeId = getEdgeNodeId(edge, 'target');
    return sourceNodeId && targetNodeId && nodeIdSet.has(sourceNodeId) && nodeIdSet.has(targetNodeId);
  }).map((edge) => ({
    ...edge,
    source: getEdgeEndpoint(edge, 'source'),
    target: getEdgeEndpoint(edge, 'target')
  }));
};

const removeNodeById = (nodeId) => {
  nodes.value = nodes.value.filter((n) => n.id !== nodeId);
  pruneDanglingEdges();
  if (selectedId.value === nodeId) selectedId.value = null;
  if (selectedEdgeId.value && !edges.value.some((edge) => edge.id === selectedEdgeId.value)) {
    selectedEdgeId.value = null;
  }
};

const sanitizeNodeForPersist = (node) => ({
  id: node.id,
  name: node.name,
  type: node.type,
  componentCode: node.componentCode,
  componentId: node.componentId,
  x: node.x,
  y: node.y,
  fieldValues: node.fieldValues || {},
  inputParams: Array.isArray(node.inputParams)
    ? node.inputParams.map((item) => ({
      paramCode: item.paramCode,
      paramName: item.paramName,
      dataType: item.dataType,
      requiredFlag: item.requiredFlag,
      description: item.description,
      sourceType: item.sourceType,
      sourceValue: normalizeUpstreamSourceValue(item.sourceType, item.sourceValue)
    }))
    : [],
  outputParams: Array.isArray(node.outputParams)
    ? node.outputParams.map((item) => ({
      paramCode: item.paramCode,
      paramName: item.paramName,
      dataType: item.dataType,
      requiredFlag: item.requiredFlag,
      sourceType: item.sourceType || 'CONST',
      sourceValue: item.sourceValue || '',
      description: item.description,
      defaultValue: item.defaultValue
    }))
    : []
});

const sanitizeEdgeForPersist = (edge) => {
  const normalized = buildEdgeModel(edge);
  return {
    id: normalized.id,
    source: {
      nodeId: normalized.source.nodeId,
      port: normalized.source.port
    },
    target: {
      nodeId: normalized.target.nodeId,
      port: normalized.target.port
    }
  };
};

const normalizePersistedDsl = (raw) => {
  const source = typeof raw === 'string'
    ? (() => {
      try { return JSON.parse(raw || '{}'); } catch (_) { return {}; }
    })()
    : (raw || {});

  const normalizedNodes = Array.isArray(source.nodes) ? source.nodes : Array.isArray(source.graph?.nodes) ? source.graph.nodes : [];
  const normalizedEdges = Array.isArray(source.edges) ? source.edges : Array.isArray(source.graph?.edges) ? source.graph.edges : [];
  const normalizedViewport = source.viewport || source.graph?.viewport || { scale: 1, pan: { x: 0, y: 0 } };

  return {
    nodes: normalizedNodes.map((node) => ({
      ...node,
      type: normalizeComponentType(node.type, node.componentCode, node.name),
      inputParams: Array.isArray(node.inputParams) ? node.inputParams : (Array.isArray(node.ioParams) ? node.ioParams.filter(p => (p.paramType || '').toUpperCase() === 'INPUT') : []),
      outputParams: Array.isArray(node.outputParams) ? node.outputParams : (Array.isArray(node.ioParams) ? node.ioParams.filter(p => (p.paramType || '').toUpperCase() === 'OUTPUT') : [])
    })),
    edges: normalizedEdges.map((edge) => ({
      id: edge.id,
      source: getEdgeEndpoint(edge, 'source'),
      target: getEdgeEndpoint(edge, 'target')
    })),
    viewport: {
      scale: normalizedViewport?.scale || 1,
      pan: normalizedViewport?.pan || { x: 0, y: 0 }
    }
  };
};

const buildPersistedDslPayload = () => ({
  schemaVersion: 3,
  nodes: nodes.value.map(sanitizeNodeForPersist),
  edges: edges.value.map(sanitizeEdgeForPersist),
  viewport: { scale: scale.value, pan: pan.value }
});

const openNodeEditor = async (nodeId) => {
  selectedId.value = nodeId;
  nodeEditorTab.value = 'field';
  const node = nodes.value.find((item) => item.id === nodeId);
  if (!node) return;

  nodeFields.value = [];
  nodeInputParams.value = [];
  nodeOutputParams.value = [];

  loadCanvasOptions();

  // 先从本地节点对象加载（优先保留用户已编辑的数据）
  const localInputParams = Array.isArray(node.inputParams) ? node.inputParams : [];
  const localOutputParams = Array.isArray(node.outputParams) ? node.outputParams : [];
  if (localInputParams.length) {
    nodeInputParams.value = localInputParams.map((item, idx) => ({
      ...item,
      __rowId: `local_input_${idx}_${item.paramCode || ''}`,
      requiredBool: Number(item.requiredFlag || 0) === 1,
      sourceType: item.sourceType || 'CONST'
    }));
  }
  // START/END 节点的输出参数由输入参数自动同步，不从本地加载旧快照
  if (!(isStartNode(node) || isEndNode(node)) && localOutputParams.length) {
    nodeOutputParams.value = localOutputParams.map((item, idx) => ({
      ...item,
      __rowId: `local_output_${idx}_${item.paramCode || ''}`,
      requiredBool: Number(item.requiredFlag || 0) === 1,
      sourceType: 'CONST'
    }));
  }

  const taskId = props.versionData?.taskId || props.initialDsl?.taskId || null;
  let isOutdated = false;
  if (taskId && (!localInputParams.length || !nodeFields.value.length)) {
    try {
      const [fieldRes, ioRes, outdatedRes] = await Promise.all([
        taskApi.getNodeFields(taskId, nodeId),
        taskApi.getNodeIoParams(taskId, nodeId),
        taskApi.getOutdatedNodes(taskId, props.versionData?.environment || props.environment || 'DEV').catch(() => null)
      ]);
      const serverFields = fieldRes.data?.data || [];
      const serverIoParams = ioRes.data?.data || [];
      const outdatedNodes = Array.isArray(outdatedRes?.data?.data) ? outdatedRes.data.data : [];
      isOutdated = outdatedNodes.includes(nodeId);

      if (!nodeFields.value.length && serverFields.length) {
        nodeFields.value = serverFields.map((item) => {
          const srvProps = normalizeWidgetProps(item.widgetProps);
          return {
            ...item,
            fieldValue: normalizeFieldValue(item),
            requiredBool: Number(item.requiredFlag || 0) === 1,
            widgetType: item.widgetType || 'TEXTAREA',
            widgetProps: item.widgetProps || '',
            options: normalizeOptionList(srvProps.options || srvProps.optionList || []),
            _optionsLoading: false,
            _resolvedOptions: null,
            _selectedDbId: srvProps.optionsSource?.dbId || null,
            _selectedApiId: srvProps.optionsSource?.apiId || null,
            _selectedScriptId: srvProps.optionsSource?.scriptId || null
          };
        });
      }
      if (!localInputParams.length && serverIoParams.length) {
        const mappedServerParams = serverIoParams.map((item, idx) => ({
          ...item,
          __rowId: `server_${idx}_${item.paramCode || ''}`,
          requiredBool: Number(item.requiredFlag || 0) === 1,
          sourceType: item.sourceType || 'CONST'
        }));
        nodeInputParams.value = mappedServerParams.filter(p => (p.ioType || p.paramType || '').toUpperCase() === 'INPUT');
        // START/END 的输出参数由输入参数同步，不从服务端加载旧快照
        if (!(isStartNode(node) || isEndNode(node))) {
          nodeOutputParams.value = mappedServerParams.filter(p => (p.ioType || p.paramType || '').toUpperCase() === 'OUTPUT');
        }
      }
    } catch (_) {}
  }

  // 节点过期时，从组件定义刷新 widgetProps 等元数据（保留用户已填的 fieldValue）
  if (isOutdated && node.componentId && nodeFields.value.length) {
    try {
      const res = await componentApi.getMeta(node.componentId);
      const meta = res.data?.data || {};
      const metaFields = meta.fields || [];
      const metaMap = {};
      metaFields.forEach((f) => { metaMap[f.fieldCode] = f; });
      nodeFields.value.forEach((nf) => {
        const mf = metaMap[nf.fieldCode];
        if (mf) {
          const mfProps = normalizeWidgetProps(mf.widgetProps || '');
          const oldProps = normalizeWidgetProps(nf.widgetProps || '');
          const oldSrc = oldProps.optionsSource;
          nf.widgetType = mf.widgetType || nf.widgetType;
          nf.widgetProps = mf.widgetProps || '';
          if (oldSrc && (oldSrc.dbId || oldSrc.dbVersionId || oldSrc.apiId || oldSrc.apiVersionId)) {
            let newProps = normalizeWidgetProps(nf.widgetProps);
            if (!newProps.optionsSource) newProps.optionsSource = {};
            if (oldSrc.dbVersionId) newProps.optionsSource.dbVersionId = oldSrc.dbVersionId;
            else if (oldSrc.dbId) newProps.optionsSource.dbId = oldSrc.dbId;
            if (oldSrc.apiVersionId) newProps.optionsSource.apiVersionId = oldSrc.apiVersionId;
            else if (oldSrc.apiId) newProps.optionsSource.apiId = oldSrc.apiId;
            nf.widgetProps = JSON.stringify(newProps);
          }
          const finalProps = normalizeWidgetProps(nf.widgetProps);
          nf._selectedDbId = finalProps.optionsSource?.dbId || null;
          nf._selectedApiId = finalProps.optionsSource?.apiId || null;
          nf._selectedScriptId = finalProps.optionsSource?.scriptId || null;
          nf.valueType = mf.valueType || nf.valueType;
          nf.fieldName = mf.fieldName || nf.fieldName;
          nf.requiredFlag = Number(mf.requiredFlag ?? nf.requiredFlag ?? 0);
          nf.sortOrder = Number(mf.sortOrder || nf.sortOrder || 0);
          nf.options = normalizeOptionList(mfProps.options || mfProps.optionList || []);
          nf._resolvedOptions = null;
        }
      });
      message.info('组件定义已变更，字段选项已自动更新');
    } catch (_) {}
  }

  if (!nodeFields.value.length) {
    if (node.componentId) {
      try {
        const res = await componentApi.getMeta(node.componentId);
        const meta = res.data?.data || {};
        nodeFields.value = (meta.fields || []).map((item) => {
          const rawFieldValue = node.fieldValues?.[item.fieldCode] ?? item.defaultValue ?? '';
          const itemProps = normalizeWidgetProps(item.widgetProps || '');
          return {
            fieldCode: item.fieldCode,
            fieldName: item.fieldName,
            fieldType: item.fieldType,
            valueType: item.valueType || 'STRING',
            widgetType: item.widgetType || 'TEXTAREA',
            widgetProps: item.widgetProps || '',
            optionContent: item.optionContent || '',
            options: normalizeOptionList(itemProps.options || itemProps.optionList || []),
            defaultValue: item.defaultValue ?? '',
            sortOrder: Number(item.sortOrder || 0),
            requiredFlag: Number(item.requiredFlag || 0),
            fieldValue: normalizeFieldValue({ ...item, fieldValue: rawFieldValue }),
            _optionsLoading: false,
            _resolvedOptions: null,
            _selectedDbId: null,
            _selectedApiId: null,
            _selectedScriptId: null
          };
        });
      } catch (_) {}
    }
  }

  // 自动加载有动态选项数据源的字段选项
  nodeFields.value.forEach((f) => {
    if (hasDynamicOptionSource(f)) loadDynamicOptions(f);
  });

  nodeEditorVisible.value = true;
};

const onNodeEditorCancel = () => {
  // 关闭前保存到本地节点对象
  const node = nodes.value.find((item) => item.id === selectedId.value);
  if (node) {
    const fieldValues = {};
    nodeFields.value.forEach((item) => {
      let val = item.fieldValue;
      // MULTI_SELECT数组值转为单字符串（后端readFieldValue用asText取值）
      if (Array.isArray(val)) {
        val = val.length === 1 ? val[0] : val.join(',');
      }
      fieldValues[item.fieldCode] = val;
    });
    node.fieldValues = fieldValues;
    node.inputParams = nodeInputParams.value.map((item) => ({
      paramCode: item.paramCode,
      paramName: item.paramName,
      dataType: item.dataType,
      requiredFlag: item.requiredBool ? 1 : 0,
      sourceType: item.sourceType,
      sourceValue: item.sourceValue
    }));
    const outParams = displayOutputParams.value;
    node.outputParams = outParams.map((item) => ({
      paramCode: item.paramCode,
      paramName: item.paramName,
      dataType: item.dataType,
      requiredFlag: item.requiredBool ? 1 : 0,
      sourceType: item.sourceType || 'CONST',
      sourceValue: item.sourceValue || ''
    }));
  }
  nodeEditorVisible.value = false;
};

const buildUpstreamTreeData = (nodeId) => {
  if (!nodeId) return [];
  const upstreamIds = edges.value
    .filter((edge) => getEdgeNodeId(edge, 'target') === nodeId)
    .map((edge) => getEdgeNodeId(edge, 'source'));
  return upstreamIds.map((id) => {
    const upstreamNode = nodes.value.find(n => n.id === id);
    if (!upstreamNode) return null;
    // START/END 节点用 inputParams（输出和输入一致），其他节点用 outputParams
    let params = (upstreamNode.outputParams || []);
    if (!params.length && (upstreamNode.type === 'START' || upstreamNode.type === 'END')) {
      params = (upstreamNode.inputParams || []);
    }
    return {
      value: id,
      title: upstreamNode.name || id,
      selectable: false,
      children: params.map(p => ({
        value: JSON.stringify({ nodeId: id, paramCode: p.paramCode }),
        title: `${p.paramName || p.paramCode} (${p.paramCode})`
      }))
    };
  }).filter(item => item && item.children?.length);
};

const normalizeSourceValueForTree = (sourceValue) => {
  if (!sourceValue) return undefined;
  if (typeof sourceValue === 'object' && sourceValue.nodeId && sourceValue.paramCode) {
    return JSON.stringify(sourceValue);
  }
  if (typeof sourceValue === 'string') {
    if (sourceValue.startsWith('{') && sourceValue.includes('nodeId')) return sourceValue;
    // old dot notation: "nodeId.paramCode"
    const dotIdx = sourceValue.indexOf('.');
    if (dotIdx > 0) {
      return JSON.stringify({ nodeId: sourceValue.slice(0, dotIdx), paramCode: sourceValue.slice(dotIdx + 1) });
    }
  }
  return sourceValue;
};

// 确保 UPSTREAM_OUTPUT 的 sourceValue 始终是对象格式
const normalizeUpstreamSourceValue = (sourceType, sourceValue) => {
  if (sourceType !== 'UPSTREAM_OUTPUT') return sourceValue;
  if (!sourceValue) return sourceValue;
  if (typeof sourceValue === 'object') return sourceValue;
  if (typeof sourceValue === 'string') {
    try {
      const parsed = JSON.parse(sourceValue);
      if (parsed?.nodeId && parsed?.paramCode) return parsed;
    } catch (_) {}
  }
  return sourceValue;
};

// 将树选择器返回的值统一解析为 { nodeId, paramCode } 对象
const parseUpstreamValue = (val) => {
  if (!val) return '';
  if (typeof val === 'object' && val.nodeId && val.paramCode) return val;
  if (typeof val === 'string') {
    try {
      const parsed = JSON.parse(val);
      if (parsed?.nodeId && parsed?.paramCode) return parsed;
    } catch (_) {
      const dotIdx = val.indexOf('.');
      if (dotIdx > 0) {
        return { nodeId: val.slice(0, dotIdx), paramCode: val.slice(dotIdx + 1) };
      }
    }
  }
  return val;
};

const buildUpstreamCascaderOptions = (nodeId) => {
  if (!nodeId) return [];
  const directUpstreamIds = edges.value
    .filter((edge) => getEdgeNodeId(edge, 'target') === nodeId)
    .map((edge) => getEdgeNodeId(edge, 'source'));
  return directUpstreamIds.map((id) => {
    const node = nodes.value.find(n => n.id === id);
    const outputs = (node?.outputParams || []);
    return {
      value: node?.id,
      label: node?.name || id,
      children: outputs.map(p => ({
        value: p.paramCode,
        label: `${p.paramName || p.paramCode} (${p.paramCode})`
      }))
    };
  }).filter(item => item.value && item.children?.length);
};

const parseUpstreamRefFromSourceValue = (sourceValue) => {
  if (!sourceValue) return [];
  if (typeof sourceValue === 'object' && sourceValue.nodeId && sourceValue.paramCode) {
    return [sourceValue.nodeId, sourceValue.paramCode];
  }
  if (typeof sourceValue === 'string') {
    try {
      const parsed = JSON.parse(sourceValue);
      if (parsed?.nodeId && parsed?.paramCode) return [parsed.nodeId, parsed.paramCode];
    } catch (_) {
      const dotIdx = sourceValue.indexOf('.');
      if (dotIdx > 0) {
        return [sourceValue.slice(0, dotIdx), sourceValue.slice(dotIdx + 1)];
      }
    }
  }
  return [];
};

const onUpstreamRefChange = (record) => {
  if (!Array.isArray(record.sourceRef) || record.sourceRef.length !== 2) {
    record.sourceValue = null;
    return;
  }
  const [nodeId, paramCode] = record.sourceRef;
  record.sourceValue = { nodeId, paramCode };
};

const onParamSourceTypeChange = (record) => {
  if (record.sourceType === 'UPSTREAM_OUTPUT') {
    const options = buildUpstreamCascaderOptions(selectedNode.value?.id);
    if (!options.length) {
      record.sourceRef = [];
      record.sourceValue = '';
      message.warning('当前节点无上游输出参数可引用');
      return;
    }
    const firstNode = options[0];
    const firstParam = firstNode.children?.[0];
    record.sourceRef = [firstNode.value, firstParam.value];
    record.sourceValue = { nodeId: firstNode.value, paramCode: firstParam.value };
  } else if (record.sourceType === 'EXPRESSION') {
    record.sourceRef = [];
    record.sourceValue = '${}';
  } else if (record.sourceType === 'CONST') {
    record.sourceRef = [];
    record.sourceValue = record.sourceValue || '';
  }
};

const addNodeInputParam = () => {
  nodeInputParams.value.push({
    __rowId: `new_${Date.now()}_${Math.floor(Math.random() * 9999)}`,
    paramName: '',
    paramCode: '',
    dataType: 'STRING',
    sourceType: 'CONST',
    sourceValue: '',
    sourceRef: [],
    requiredBool: false,
    readonlyParam: false
  });
};

const removeNodeInputParam = (__rowId) => {
  nodeInputParams.value = nodeInputParams.value.filter(item => item.__rowId !== __rowId);
};

const addNodeOutputParam = () => {
  if (isStartOrEndNode()) {
    message.warning('开始/结束组件的输出参数由输入参数自动同步，无需手动添加');
    return;
  }
  nodeOutputParams.value.push({
    __rowId: `new_${Date.now()}_${Math.floor(Math.random() * 9999)}`,
    paramName: '',
    paramCode: '',
    dataType: 'STRING',
    requiredBool: false,
    readonlyParam: false
  });
};

const removeNodeOutputParam = (__rowId) => {
  if (isStartOrEndNode()) {
    message.warning('开始/结束组件的输出参数与输入参数自动同步，不能单独删除');
    return;
  }
  nodeOutputParams.value = nodeOutputParams.value.filter(item => item.__rowId !== __rowId);
};

const selectEdge = (edgeId) => {
  selectedEdgeId.value = edgeId;
  selectedId.value = null;
};

const confirmAndDeleteEdge = (edgeId) => {
  const edge = edges.value.find((item) => item.id === edgeId);
  if (!edge) return;
  const sourceNodeId = getEdgeNodeId(edge, 'source');
  const targetNodeId = getEdgeNodeId(edge, 'target');
  const sourceNode = nodes.value.find((node) => node.id === sourceNodeId);
  const targetNode = nodes.value.find((node) => node.id === targetNodeId);
  Modal.confirm({
    title: '删除连线',
    content: `确定删除连线【${sourceNode?.name || sourceNodeId} → ${targetNode?.name || targetNodeId}】吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      edges.value = edges.value.filter((item) => item.id !== edgeId);
      if (selectedEdgeId.value === edgeId) selectedEdgeId.value = null;
    }
  });
};

const isEditableElement = (el) => {
  if (!el) return false;
  const tag = el.tagName?.toLowerCase();
  if (el.isContentEditable) return true;
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return true;
  if (el.closest?.('.ant-input, .ant-input-affix-wrapper, .ant-select, .ant-cascader-picker')) return true;
  return false;
};

const handleKeydown = (event) => {
  if (event.key !== 'Delete' && event.key !== 'Backspace') return;
  if (isEditableElement(event.target)) return;
  if (!props.open || props.readonly || nodeEditorVisible.value || !canvasFocused.value) return;
  if (selectedEdgeId.value) {
    event.preventDefault();
    confirmAndDeleteEdge(selectedEdgeId.value);
    return;
  }
  if (selectedId.value) {
    event.preventDefault();
    const node = nodes.value.find((item) => item.id === selectedId.value);
    if (!node) return;
    Modal.confirm({
      title: '删除节点',
      content: `确定删除节点【${node.name}】吗？该节点关联的连线也会一并删除。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => removeNodeById(node.id)
    });
  }
};

const onNodeContextMenu = (nodeId) => {
  const node = nodes.value.find(n => n.id === nodeId);
  if (!node) return;
  Modal.confirm({
    title: '删除节点',
    content: `确定删除节点【${node.name}】吗？该节点关联的连线也会一并删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => removeNodeById(nodeId)
  });
};

const onEdgeContextMenu = (edgeId) => {
  confirmAndDeleteEdge(edgeId);
};

const validateGraphBeforeSave = async () => {
  const startCount = nodes.value.filter(n => isStartNode(n)).length;
  const endCount   = nodes.value.filter(n => isEndNode(n)).length;
  if (startCount !== 1 || endCount !== 1) {
    message.warning(`流程必须且仅包含1个开始组件和1个结束组件（当前: ${startCount}个开始, ${endCount}个结束）`);
    return false;
  }

  for (const node of nodes.value) {
    const incomingEdges = edges.value.filter((edge) => getEdgeNodeId(edge, 'target') === node.id);
    const outgoingEdges = edges.value.filter((edge) => getEdgeNodeId(edge, 'source') === node.id);
    if (!isStartNode(node) && incomingEdges.length === 0) {
      message.warning(`节点【${node.name}】缺少入边`);
      return false;
    }
    if (!isEndNode(node) && outgoingEdges.length === 0) {
      message.warning(`节点【${node.name}】缺少出边`);
      return false;
    }

    const componentId = node.componentId;
    if (componentId) {
      try {
        const res = await componentApi.getMeta(componentId);
        const meta = res.data?.data || {};
        const requiredFields = (meta.fields || []).filter((f) => Number(f.requiredFlag || 0) === 1);
        for (const rf of requiredFields) {
          const v = node.fieldValues?.[rf.fieldCode];
          if (v === undefined || v === null || String(v).trim() === '') {
            message.warning(`节点【${node.name}】字段【${rf.fieldName || rf.fieldCode}】为必填`);
            return false;
          }
        }
      } catch (_) {
        message.warning(`节点【${node.name}】字段校验失败，请重试`);
        return false;
      }
    }

    const inputParams = node.inputParams || [];
    for (const p of inputParams) {
      if (p.sourceType === 'UPSTREAM_OUTPUT') {
        const ref = parseUpstreamRefFromSourceValue(p.sourceValue);
        const [refNodeId, refParamCode] = ref;
        if (!refNodeId || !refParamCode) {
          message.warning(`节点【${node.name}】参数【${p.paramName || p.paramCode}】上游来源无效`);
          return false;
        }
        const validOptions = buildUpstreamCascaderOptions(node.id);
        const matchedNode = validOptions.find(opt => opt.value === refNodeId);
        const matchedParam = matchedNode?.children?.find(child => child.value === refParamCode);
        if (!matchedNode || !matchedParam) {
          message.warning(`节点【${node.name}】参数【${p.paramName || p.paramCode}】上游来源无效`);
          return false;
        }
      }
    }
  }
  return true;
};

const save = async () => {
  if (saving.value) return;
  saving.value = true;
  try {
    // JSON模式下先解析jsonText到nodes/edges，确保保存的是最新编辑内容
    if (jsonMode.value) {
      const parsed = normalizePersistedDsl(jsonText.value || '{}');
      nodes.value = Array.isArray(parsed.nodes) ? parsed.nodes : [];
      edges.value = Array.isArray(parsed.edges) ? parsed.edges : [];
    }
    if (!(await validateGraphBeforeSave())) return;
    pruneDanglingEdges();
    // 如果节点编辑器处于打开状态，先将编辑器中的参数刷新到画布节点
    const editingNode = nodes.value.find(n => n.id === selectedId.value);
    if (editingNode) {
      editingNode.inputParams = nodeInputParams.value.map((item) => ({
        paramCode: item.paramCode,
        paramName: item.paramName,
        dataType: item.dataType,
        requiredFlag: item.requiredBool ? 1 : 0,
        sourceType: item.sourceType,
        sourceValue: item.sourceValue
      }));
      editingNode.outputParams = nodeOutputParams.value.map((item) => ({
        paramCode: item.paramCode,
        paramName: item.paramName,
        dataType: item.dataType,
        requiredFlag: item.requiredBool ? 1 : 0,
        sourceType: item.sourceType || 'CONST',
        sourceValue: item.sourceValue || ''
      }));
    }
    // 保存前自动同步 START/END 节点的输出参数
    syncStartEndOutputParams();
    const payload = buildPersistedDslPayload();
    jsonText.value = JSON.stringify(payload, null, 2);
    emit('save', payload);
  } catch (e) {
    message.error(e?.message || '保存失败，请重试');
  } finally {
    saving.value = false;
  }
};

const toggleJsonMode = () => {
  if (!jsonMode.value) {
    jsonMode.value = true;
    jsonText.value = JSON.stringify(buildPersistedDslPayload(), null, 2);
    return;
  }

  try {
    const parsed = normalizePersistedDsl(jsonText.value || '{}');
    nodes.value = Array.isArray(parsed.nodes) ? parsed.nodes : [];
    edges.value = Array.isArray(parsed.edges) ? parsed.edges : [];
    scale.value = parsed.viewport?.scale || 1;
    pan.value = parsed.viewport?.pan || { x: 0, y: 0 };
    pruneDanglingEdges();
    syncStartEndOutputParams();
    jsonMode.value = false;
  } catch (_) {
    message.warning('JSON格式错误，无法切回可视化，请先修正JSON');
  }
};

watch(nodeEditorVisible, async (val) => {
  if (val) return;
  const node = nodes.value.find((item) => item.id === selectedId.value);
  if (!node) return;
  // onNodeEditorCancel 已处理本地保存, 此处负责服务端持久化
  const taskId = props.versionData?.taskId || props.initialDsl?.taskId || null;
  if (taskId && node.id) {
    try {
      const nodeInfo = {
        componentId: node.componentId,
        nodeName: node.name || node.id,
        nodeType: node.type
      };
      await taskApi.saveNodeFields(taskId, node.id, nodeFields.value, nodeInfo);
      await taskApi.saveNodeIoParams(taskId, node.id, nodeInputParams.value.concat(displayOutputParams.value), nodeInfo);
    } catch (error) {
      message.warning(error?.message || '保存节点字段失败');
    }
  }
});

watch(() => props.open, async (val) => {
  if (!val) return;
  await Promise.all([loadPalette(), loadSelectableResources()]);

  const raw = props.versionData ?? props.initialDsl ?? {};
  const data = normalizePersistedDsl(raw);

  nodes.value = Array.isArray(data.nodes) ? data.nodes : [];
  edges.value = Array.isArray(data.edges) ? data.edges : [];
  scale.value = data.viewport?.scale || 1;
  pan.value = data.viewport?.pan || { x: 0, y: 0 };

  nodes.value = nodes.value.map((node) => {
    const normalizedType = normalizeComponentType(node.type, node.componentCode, node.name);
    const byCode = node.componentCode ? componentMetaMap.value[`code:${node.componentCode}`] : null;
    const byType = componentMetaMap.value[`type:${normalizedType}`] || componentMetaMap.value[`type:${node.type}`];
    return { ...node, type: normalizedType, componentId: node.componentId || byCode?.id || byType?.id };
  });

  // 编辑器打开时同步 START/END 节点的输出参数
  syncStartEndOutputParams();

  jsonText.value = JSON.stringify(buildPersistedDslPayload(), null, 2);
});

onMounted(() => {
  loadPalette();
  loadSelectableResources();
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.editor-wrap { display: flex; flex-direction: column; gap: 12px; }
.top-bar { display: flex; align-items: center; justify-content: space-between; }
.top-tip { color: #666; font-size: 13px; }
.editor-body { display: flex; gap: 12px; height: 620px; }
.node-panel { width: 220px; background: #fafafa; border: 1px solid #eee; border-radius: 8px; padding: 12px; overflow-y: auto; }
.panel-title { font-weight: 600; margin-bottom: 8px; }
.panel-tip { font-size: 12px; color: #888; margin-bottom: 12px; line-height: 1.4; }
.palette-group { margin-bottom: 10px; }
.palette-group-title { font-size: 12px; color: #666; font-weight: 600; margin-bottom: 6px; }
.node-item { background: #fff; border: 1px solid #d9d9d9; border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; cursor: grab; }
.node-item.disabled { color: #aaa; background: #f5f5f5; cursor: not-allowed; }
.canvas-panel { flex: 1; border: 1px solid #eee; border-radius: 8px; overflow: hidden; }
.canvas-panel.full-width { width: 100%; }
.canvas-toolbar { border-bottom: 1px solid #f0f0f0; padding: 8px; display: flex; gap: 8px; }
.canvas { position: relative; width: 100%; height: 560px; background: #fcfcfc; overflow: hidden; }
.graph-stage { position: absolute; left: 0; top: 0; width: 4000px; height: 4000px; }
.edge-layer { position: absolute; left: 0; top: 0; width: 4000px; height: 4000px; }
.edge-line { stroke: #5b8ff9; stroke-width: 2; cursor: pointer; pointer-events: stroke; }
.edge-line.selected { stroke: #fa8c16; stroke-width: 3; }
.edge-line.draft { stroke-dasharray: 4 4; opacity: 0.8; }
.flow-node { position: absolute; border: 1px solid #87b3ff; background: #fff; border-radius: 8px; display: flex; align-items: center; justify-content: center; user-select: none; cursor: move; box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.flow-node.selected { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,.15); }
.port { position: absolute; width: 10px; height: 10px; border-radius: 50%; background: #1677ff; }
.port.in { left: calc(50% - 5px); top: -5px; }
.port.in.hover { background: #52c41a; box-shadow: 0 0 0 4px rgba(82, 196, 26, 0.2); }
.port.out { left: calc(50% - 5px); bottom: -5px; cursor: crosshair; }
.field-preview-grid { display: flex; flex-direction: column; gap: 16px; }
.field-preview-card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 14px 16px; background: #fff; transition: border-color .2s; }
.field-preview-card:hover { border-color: #1677ff; }
.field-preview-title { font-weight: 600; margin-bottom: 10px; color: #1f2937; font-size: 14px; }
.field-preview-control { margin-bottom: 4px; }
.field-preview-default { font-size: 12px; color: #6b7280; }
.param-fixed-text { color: #999; line-height: 32px; }
.footer-actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
