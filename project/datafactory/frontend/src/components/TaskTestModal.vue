<template>
  <a-modal v-model:open="visible" title="任务测试执行" :width="1200" :footer="null" :centered="true" :maskClosable="false"
    destroyOnClose @cancel="handleClose">
    <div class="test-modal-content">
      <div class="test-modal-layout">
        <!-- 左侧配置区 -->
        <div class="config-side">
          <div class="env-selector">
            <span class="label">当前环境：</span>
            <a-input :value="`${currentEnv === 'PROD' ? '生产环境' : '测试环境'} / 版本 ${displayVersion}`" style="width: 100%" readonly />
          </div>

          <div class="config-management" style="margin-top: 8px; display: flex; align-items: center; gap: 8px;">
            <span class="label">测试配置：</span>
            <a-select v-model:value="selectedConfigId" style="flex: 1" placeholder="请选择测试配置" @change="loadConfig"
              :loading="configsLoading">
              <a-select-option v-for="conf in testConfigs" :key="conf.id" :value="conf.id">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>{{ conf.name }}</span>
                  <a-popconfirm title="确定删除该配置吗？" @confirm.stop="handleDeleteConfig(conf.id)">
                    <span class="delete-icon" @click.stop>×</span>
                  </a-popconfirm>
                </div>
              </a-select-option>
            </a-select>
            <a-button @click="addNewConfig">+ 新增</a-button>
            <a-button type="primary" ghost @click="saveCurrentConfig">保存配置</a-button>
          </div>

          <!-- Flow Viewer -->
          <div class="flow-viewer-section" style="margin-top: 8px; height: 260px;">
            <FlowViewer :dsl-content="dslContent" @node-click="onFlowNodeClick" />
          </div>

          <!-- Selected Node Params -->
          <div class="node-params-section" style="margin-top: 12px;">
            <div v-if="selectedFlowNode" class="section-title">
              选中节点: {{ selectedFlowNode.name || selectedFlowNode.id }}
            </div>
            <div v-else class="section-title">请点击画布中的节点查看参数</div>
            <a-table
              v-if="filteredIoParams.length > 0"
              :columns="ioColumns"
              :data-source="filteredIoParams"
              :pagination="false"
              size="small"
              bordered
              row-key="id"
              :scroll="{ y: 240 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'ioType'">
                  <a-tag :color="record.ioType === 'INPUT' ? 'processing' : 'success'">
                    {{ record.ioType === 'INPUT' ? '输入参数' : '输出参数' }}
                  </a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'requiredFlag'">
                  <a-tag :color="Number(record.requiredFlag || 0) === 1 ? 'error' : 'default'">
                    {{ Number(record.requiredFlag || 0) === 1 ? '必填' : '可选' }}
                  </a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'sourceType'">
                  <a-tag>{{ record.sourceType || '-' }}</a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'sourceValue'">
                  <a-input
                    v-if="record.ioType === 'INPUT'"
                    v-model:value="record.testValue"
                    :placeholder="formatDisplayValue(record.sourceValue)"
                    size="small"
                  />
                  <span v-else>{{ syncedOrSourceValue(record) }}</span>
                </template>
              </template>
            </a-table>
            <a-empty v-else-if="selectedFlowNode" description="该节点暂无参数" />
          </div>
        </div>

        <!-- 右侧结果区 -->
        <div class="result-side">
          <div class="section-title">执行结果</div>
          <div class="result-container">
            <div v-if="executionResult" class="result-success-banner">
              <span class="success-icon">✓</span> 任务执行成功
            </div>
            <div v-else-if="executionError" class="result-error-banner">
              <span class="error-icon">×</span> 任务执行失败: {{ executionError }}
            </div>
            <div v-else-if="executing" class="result-loading-banner">
              <a-spin /> 正在执行中...
            </div>
            <div v-else class="result-empty-banner">
              请在左侧配置完成后点击 OK 执行测试
            </div>

            <div v-if="outputJson" class="output-json-area">
              <pre>{{ outputJson }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer" style="margin-top: 24px; display: flex; justify-content: flex-end; gap: 12px;">
        <a-button @click="handleClose">Cancel</a-button>
        <a-button type="primary" :loading="executing" @click="handleExecute">OK</a-button>
      </div>
    </div>

    <!-- 新增配置弹窗 -->
    <a-modal v-model:open="newConfigVisible" title="新增测试配置" @ok="handleAddNewConfig">
      <a-form layout="vertical">
        <a-form-item label="配置名称" required>
          <a-input v-model:value="newConfigName" placeholder="例如：测试用例-01" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue';
import { message } from 'ant-design-vue';
import { taskApi } from '../api/task';
import { componentApi } from '../api/componentApi';
import FlowViewer from './FlowViewer.vue';

const props = defineProps({
  open: Boolean,
  taskId: [String, Number],
  version: String,
  versionId: [String, Number],
  dslContent: String,
  environment: {
    type: String,
    default: 'TEST'
  }
});

const emit = defineEmits(['update:open', 'close']);

const visible = ref(false);
const currentEnv = ref(props.environment);
const displayVersion = computed(() => props.version || '-');
const ioParamList = ref([]);
const executing = ref(false);
const executionResult = ref(false);
const executionError = ref('');
const outputJson = ref('');

const testConfigs = ref([]);
const configsLoading = ref(false);
const selectedConfigId = ref(null);
const newConfigVisible = ref(false);
const newConfigName = ref('');

const selectedFlowNode = ref(null);
const filteredIoParams = computed(() => {
  if (!selectedFlowNode.value) return [];
  return ioParamList.value.filter(p => p.nodeId === selectedFlowNode.value.id);
});

const ioColumns = [
  { title: '分类', dataIndex: 'ioType', width: 80 },
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 140 },
  { title: '数据类型', dataIndex: 'dataType', width: 90 },
  { title: '参数来源', dataIndex: 'sourceType', width: 100 },
  { title: '来源值/测试值', dataIndex: 'sourceValue', width: 240 },
  { title: '必填', dataIndex: 'requiredFlag', width: 70 }
];

// 自动加载逻辑
watch(() => props.open, (newVal) => {
  visible.value = newVal;
  if (newVal) {
    currentEnv.value = props.environment || 'TEST';
    initModal();
  }
});

watch(() => props.environment, (val) => {
  currentEnv.value = val || 'TEST';
});

// 监听 taskId 变化，确保异步加载时能正确渲染
watch(() => props.taskId, (newVal) => {
  if (newVal && visible.value) {
    initModal();
  }
});

const initModal = async () => {
  if (!props.taskId) {
    console.warn('initModal: taskId is missing');
    return;
  }

  // 重置状态（避免串任务/串版本展示）
  testConfigs.value = [];
  selectedConfigId.value = null;
  executionResult.value = false;
  executionError.value = '';
  outputJson.value = '';

  await loadTestConfigs();
  await loadDefaultParams();

  if (testConfigs.value.length > 0) {
    // 优先选中默认配置，如果没有默认则选第一个
    const defaultConf = testConfigs.value.find(c => c.isDefault === 1) || testConfigs.value[0];
    selectedConfigId.value = defaultConf.id;
    loadConfig(defaultConf.id);
  }
};

const loadTestConfigs = async () => {
  const tid = props.taskId;
  if (!tid) return;

  configsLoading.value = true;
  try {
    const res = await taskApi.getTestConfigs(tid, props.versionId);
    const list = res.data?.data || [];
    testConfigs.value = list;
    console.log('loadTestConfigs: loaded', list.length, 'configs for taskId/versionId', tid, props.versionId);
  } catch (e) {
    console.error('Failed to load test configs', e);
    message.error(`加载测试配置失败: ${e.message || '未知错误'}`);
  } finally {
    configsLoading.value = false;
  }
};

const handleClose = () => {
  emit('update:open', false);
  emit('close');
};

const extractNodeIoParams = (node) => {
  const inputParams = Array.isArray(node?.inputParams)
    ? node.inputParams.map(p => ({ ...p, ioType: 'INPUT' }))
    : [];
  const outputParams = Array.isArray(node?.outputParams)
    ? node.outputParams.map(p => ({ ...p, ioType: 'OUTPUT' }))
    : [];
  // 兼容旧格式 ioParams
  const legacyIoParams = Array.isArray(node?.ioParams)
    ? node.ioParams.map(p => ({ ...p, ioType: (p.paramType || 'INPUT').toUpperCase() }))
    : [];

  if (inputParams.length > 0 || outputParams.length > 0) {
    return [...inputParams, ...outputParams];
  }
  if (legacyIoParams.length > 0) return legacyIoParams;

  const fv = node?.fieldValues || {};
  const fvInput = Array.isArray(fv.inputParams)
    ? fv.inputParams.map(p => ({ ...p, ioType: 'INPUT' }))
    : [];
  const fvOutput = Array.isArray(fv.outputParams)
    ? fv.outputParams.map(p => ({ ...p, ioType: 'OUTPUT' }))
    : [];

  return [...fvInput, ...fvOutput];
};

const getOrderedNodeIds = (dsl) => {
  const nodes = dsl?.nodes || [];
  const edges = dsl?.edges || [];
  const nodeIds = nodes.map(n => n.id);
  const indegree = {};
  const nextMap = {};

  nodeIds.forEach(id => {
    indegree[id] = 0;
    nextMap[id] = [];
  });

  const getNodeId = (endpoint, fallback) => {
    if (endpoint && typeof endpoint === 'object') return endpoint.nodeId || endpoint.id || null;
    return fallback || null;
  };

  edges.forEach((e) => {
    const sourceId = getNodeId(e.source, e.sourceNodeId);
    const targetId = getNodeId(e.target, e.targetNodeId);
    if (sourceId && targetId && indegree[targetId] !== undefined) {
      indegree[targetId] += 1;
      nextMap[sourceId].push(targetId);
    }
  });

  const queue = nodeIds.filter(id => indegree[id] === 0);
  const result = [];

  while (queue.length) {
    const id = queue.shift();
    result.push(id);
    (nextMap[id] || []).forEach((toId) => {
      indegree[toId] -= 1;
      if (indegree[toId] === 0) queue.push(toId);
    });
  }

  const remained = nodeIds.filter(id => !result.includes(id));
  return [...result, ...remained];
};

const loadDefaultParams = async () => {
  if (!props.dslContent) {
    ioParamList.value = [];
    return;
  }

  try {
    const dsl = JSON.parse(props.dslContent);
    const orderedNodeIds = getOrderedNodeIds(dsl);
    const nodes = dsl.nodes || [];
    const nodeMap = new Map(nodes.map(n => [n.id, n]));
    const rows = [];

    const componentIds = Array.from(new Set(nodes.map(n => n.componentId).filter(Boolean)));
    const metaMap = new Map();
    await Promise.all(componentIds.map(async (cid) => {
      try {
        const res = await componentApi.getMeta(cid);
        metaMap.set(cid, res.data?.data || null);
      } catch (e) {
        metaMap.set(cid, null);
      }
    }));

    orderedNodeIds.forEach((nodeId) => {
      const node = nodeMap.get(nodeId);
      if (!node) return;

      // START/END 节点的输出参数从输入参数同步
      const isStartOrEnd = (n) =>
        n.type === 'START' || n.type === 'END' ||
        String(n.componentCode || '').toUpperCase().includes('START') ||
        String(n.componentCode || '').toUpperCase().includes('END');
      if (isStartOrEnd(node)) {
        const inputs = Array.isArray(node.inputParams) ? node.inputParams : [];
        node.outputParams = inputs.map(item => ({
          paramCode: item.paramCode,
          paramName: item.paramName,
          dataType: item.dataType,
          requiredFlag: item.requiredFlag || 0,
          sourceType: item.sourceType || 'CONST',
          sourceValue: typeof item.sourceValue === 'string' ? item.sourceValue : '',
          defaultValue: typeof item.sourceValue === 'string' ? item.sourceValue : '',
        }));
      }

      const nodeParams = extractNodeIoParams(node);
      const meta = node.componentId ? metaMap.get(node.componentId) : null;
      const metaParams = [
        ...(meta?.inputParams || []).map(p => ({ ...p, ioType: 'INPUT' })),
        ...(meta?.outputParams || []).map(p => ({ ...p, ioType: 'OUTPUT' }))
      ];

      const byKey = new Map();
      [...metaParams, ...nodeParams].forEach((p, idx) => {
        const t = String(p.ioType || p.paramType || 'INPUT').toUpperCase();
        const k = `${t}::${p.paramCode || p.paramName || p.name || idx}`;
        byKey.set(k, { ...(byKey.get(k) || {}), ...p, ioType: t });
      });

      Array.from(byKey.values()).forEach((param, idx) => {
        const upperType = String(param.ioType || 'INPUT').toUpperCase();
        const key = param.paramCode || param.name || `${upperType === 'OUTPUT' ? 'output' : 'param'}_${idx + 1}`;
        rows.push({
          ...param,
          id: `${node.id}_${upperType}_${key}_${idx}`,
          ioType: upperType,
          paramCode: key,
          paramName: param.paramName || param.name || key,
          description: param.description || '',
          dataType: param.dataType || param.type || 'STRING',
          sourceType: param.sourceType || 'CONST',
          sourceValue: param.sourceValue ?? param.defaultValue ?? '',
          requiredFlag: Number(param.requiredFlag || 0),
          nodeId: node.id,
          nodeName: node.name,
          nodeType: node.type || '',
          componentCode: node.componentCode || '',
          testValue: param.sourceValue ?? param.defaultValue ?? ''
        });
      });
    });

    ioParamList.value = rows;
  } catch (e) {
    console.error('Failed to parse DSL for test params', e);
  }
};

const onFlowNodeClick = (node) => {
  selectedFlowNode.value = node;
};

const formatDisplayValue = (val) => {
  if (val === undefined || val === null || val === '') return '-';
  if (typeof val === 'object') return JSON.stringify(val);
  return String(val);
};

// START/END 节点的输出值联动对应输入参数的测试值
const isStartOrEndType = (nodeType, componentCode) => {
  return nodeType === 'START' || nodeType === 'END' ||
    String(componentCode || '').toUpperCase().includes('START') ||
    String(componentCode || '').toUpperCase().includes('END');
};
const syncedOrSourceValue = (record) => {
  if (record.ioType !== 'OUTPUT') return formatDisplayValue(record.sourceValue);
  // 非 START/END 节点直接展示原始值
  if (!isStartOrEndType(record.nodeType, record.componentCode)) {
    return formatDisplayValue(record.sourceValue);
  }
  // START/END 节点输出值从对应输入参数的测试值联动
  const inputMatch = ioParamList.value.find(
    p => p.ioType === 'INPUT' && p.nodeId === record.nodeId && p.paramCode === record.paramCode
  );
  if (inputMatch && (inputMatch.testValue !== undefined && inputMatch.testValue !== null && inputMatch.testValue !== '')) {
    return String(inputMatch.testValue);
  }
  return formatDisplayValue(record.sourceValue);
};


const addNewConfig = () => {
  newConfigName.value = '';
  newConfigVisible.value = true;
};

const handleAddNewConfig = async () => {
  if (!newConfigName.value.trim()) {
    message.warning('请输入配置名称');
    return;
  }

  try {
    const jsonObj = {};
    ioParamList.value
      .filter(p => p.ioType === 'INPUT')
      .forEach(p => {
        if (!jsonObj[p.nodeId]) jsonObj[p.nodeId] = {};
        jsonObj[p.nodeId][p.paramCode] = p.testValue;
      });
    const configData = JSON.stringify(jsonObj);
    const res = await taskApi.saveTestConfig({
      taskId: props.taskId,
      versionId: props.versionId,
      name: newConfigName.value,
      configMode: 'INPUT',
      configData: configData,
      isDefault: testConfigs.value.length === 0 ? 1 : 0
    });

    const newConfig = res.data?.data;
    newConfigVisible.value = false;
    message.success('配置新增成功');

    // 立即更新列表并选中
    await loadTestConfigs();
    if (newConfig && newConfig.id) {
      selectedConfigId.value = newConfig.id;
      loadConfig(newConfig.id);
    } else if (testConfigs.value.length > 0) {
      // 如果后端没返回新对象，则尝试根据名称找或者选第一个
      const found = testConfigs.value.find(c => c.name === newConfigName.value);
      selectedConfigId.value = found ? found.id : testConfigs.value[0].id;
      loadConfig(selectedConfigId.value);
    }
  } catch (e) {
    console.error('Failed to add config:', e);
    message.error(`新增配置失败: ${e.message || '未知错误'}`);
  }
};


const saveCurrentConfig = async () => {
  const config = testConfigs.value.find(c => c.id === selectedConfigId.value);
  if (!config) {
    message.warning('请先选择或新增一个配置');
    return;
  }

  try {
    const jsonObj = {};
    ioParamList.value
      .filter(p => p.ioType === 'INPUT')
      .forEach(p => {
        if (!jsonObj[p.nodeId]) jsonObj[p.nodeId] = {};
        jsonObj[p.nodeId][p.paramCode] = p.testValue;
      });
    const data = JSON.stringify(jsonObj);

    message.loading({ content: '正在保存配置...', key: 'saving' });
    await taskApi.saveTestConfig({
      ...config,
      configMode: 'INPUT',
      configData: data
    });
    message.success({ content: '配置保存成功', key: 'saving' });
    await loadTestConfigs();
  } catch (e) {
    message.error({ content: '保存配置失败', key: 'saving' });
  }
};

const loadConfig = (id) => {
  if (!id) return;
  const config = testConfigs.value.find(c => String(c.id) === String(id));
  if (config) {
    // 测试配置只影响入参值
    try {
      const obj = JSON.parse(config.configData || '{}');
      ioParamList.value.forEach(p => {
        if (p.ioType === 'INPUT' && obj[p.nodeId] && obj[p.nodeId].hasOwnProperty(p.paramCode)) {
          p.testValue = obj[p.nodeId][p.paramCode];
        }
      });
    } catch (e) {
      console.error('Failed to parse configData on load', e);
    }
  }
};

const handleDeleteConfig = async (id) => {
  try {
    await taskApi.deleteTestConfig(id);
    message.success('配置删除成功');
    await loadTestConfigs();
    if (String(selectedConfigId.value) === String(id)) {
      selectedConfigId.value = testConfigs.value.length > 0 ? testConfigs.value[0].id : null;
      if (selectedConfigId.value) {
        loadConfig(selectedConfigId.value);
      } else {
        loadDefaultParams();
      }
    }
  } catch (e) {
    message.error('删除配置失败');
  }
};

const handleExecute = async () => {
  if (!props.taskId) return;
  executing.value = true;
  executionResult.value = false;
  executionError.value = '';
  outputJson.value = '';

  try {
    // Build payload from input params' testValues
    const payload = {};
    ioParamList.value
      .filter(p => p.ioType === 'INPUT' && p.testValue)
      .forEach(p => {
        payload[p.paramCode] = p.testValue;
      });
    if (props.versionId) {
      payload.versionId = props.versionId;
    }

    const isProd = currentEnv.value === 'PROD';
    const actionName = isProd ? '任务执行' : '任务测试';
    message.loading({ content: `${actionName}中...`, key: 'executing' });

    const res = isProd
      ? await taskApi.execute(props.taskId, payload)
      : await taskApi.test(props.taskId, payload);

    const executionId = res.data?.data;

    if (executionId) {
      // 轮询获取结果，增加最大尝试次数
      let attempts = 0;
      const maxAttempts = 30; // 最多等待 30 * 2s = 60s

      const pollTimer = setInterval(async () => {
        attempts++;
        try {
          const logRes = await taskApi.getLogDetail(executionId);
          const log = logRes.data?.data;

          if (log && (log.status === 'SUCCESS' || log.status === 'FAILURE')) {
            clearInterval(pollTimer);
            executionResult.value = log.status === 'SUCCESS';
            executionError.value = log.status === 'FAILURE' ? (log.errorMessage || '执行失败') : '';

            // 填充测试执行结果到 outputJson 区域
            if (log.status === 'FAILURE') {
              outputJson.value = JSON.stringify({
                status: 'FAILURE',
                error: log.errorMessage || '未知执行错误',
                environment: log.environment,
                executionId: executionId
              }, null, 2);
            } else {
              try {
                outputJson.value = log.outputResult ? JSON.stringify(JSON.parse(log.outputResult), null, 2) : '{}';
              } catch (_) {
                outputJson.value = log.outputResult || '{}';
              }
            }

            if (log.status === 'SUCCESS') {
              message.success({ content: '测试执行完成', key: 'executing' });
            } else {
              message.error({ content: '测试执行失败', key: 'executing' });
            }
            executing.value = false;
            return;
          }
        } catch (e) {
          console.warn('Polling error, waiting for log to appear...', e);
        }

        if (attempts >= maxAttempts) {
          clearInterval(pollTimer);
          executionError.value = '执行超时，请稍后在执行日志中查看结果';
          outputJson.value = JSON.stringify({
            status: 'TIMEOUT',
            message: '轮询超时，任务可能仍在后台运行',
            executionId: executionId
          }, null, 2);
          message.error({ content: '执行超时', key: 'executing' });
          executing.value = false;
        }
      }, 2000);
    } else {
      const errorMsg = '未返回执行ID';
      executionError.value = errorMsg;
      outputJson.value = JSON.stringify({ error: errorMsg }, null, 2);
      message.error({ content: `执行失败：${errorMsg}`, key: 'executing' });
      executing.value = false;
    }
  } catch (e) {
    const errorMsg = e.message || '未知错误';
    executionError.value = errorMsg;
    outputJson.value = JSON.stringify({
      error: errorMsg,
      tip: '请检查后端服务是否正常运行'
    }, null, 2);
    message.error({ content: `执行失败：${errorMsg}`, key: 'executing' });
    executing.value = false;
  }
};


</script>

<style scoped>
.test-modal-content {
  padding: 12px 0;
}

.test-modal-layout {
  display: flex;
  gap: 24px;
}

.config-side {
  flex: 1;
  min-width: 0;
}

.result-side {
  width: 400px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.result-container {
  flex: 1;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 16px;
  background: #fafafa;
  margin-top: 12px;
  min-height: 612px;
}

.result-success-banner {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #52c41a;
  padding: 10px 16px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-weight: 500;
}

.result-error-banner {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  padding: 10px 16px;
  border-radius: 4px;
  margin-bottom: 16px;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.result-loading-banner,
.result-empty-banner {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.output-json-area {
  background: #fff;
  border: 1px solid #eee;
  padding: 12px;
  border-radius: 4px;
  max-height: 480px;
  overflow: auto;
}

.output-json-area pre {
  margin: 0;
  font-family: monospace;
  font-size: 13px;
}

.success-icon {
  display: inline-block;
  width: 18px;
  height: 18px;
  background: #52c41a;
  color: #fff;
  border-radius: 50%;
  text-align: center;
  line-height: 18px;
  margin-right: 8px;
  font-size: 12px;
}

.label {
  font-weight: 500;
  color: #333;
  width: 80px;
  flex-shrink: 0;
}

.env-selector {
  display: flex;
  align-items: center;
}

.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #333;
}

:deep(.ant-table-thead > tr > th) {
  background: #fafafa;
  font-weight: 600;
}

.delete-icon {
  color: #ff4d4f;
  cursor: pointer;
  font-size: 18px;
  margin-left: 8px;
  line-height: 1;
}

.delete-icon:hover {
  color: #ff7875;
}
</style>
