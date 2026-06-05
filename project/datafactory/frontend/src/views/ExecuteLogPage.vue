<template>
  <div class="page-wrap">
    <div class="toolbar toolbar-wrap">
      <span class="keyword-label">任务ID：</span>
      <a-input v-model:value="filters.taskId" placeholder="请输入任务ID" style="width: 160px" />
      <span class="keyword-label">状态：</span>
      <a-select v-model:value="filters.status" placeholder="请选择状态" style="width: 140px" allow-clear>
        <a-select-option value="SUCCESS">成功</a-select-option>
        <a-select-option value="FAILURE">失败</a-select-option>
        <a-select-option value="RUNNING">执行中</a-select-option>
      </a-select>
      <span class="keyword-label">时间范围：</span>
      <a-range-picker v-model:value="timeRange" style="width: 300px" show-time />
      <a-button type="primary" @click="loadLogs">搜索</a-button>
      <a-button class="btn-reset" @click="resetFilters">重置</a-button>
    </div>

    <div class="stat-row" style="grid-template-columns: repeat(4, 1fr); margin-top: 4px; margin-bottom: 8px;">
      <div class="stat-card" style="padding:8px 0 6px 0;border:none;">
        <div class="stat-title">总执行次数</div>
        <div class="stat-value" style="font-size:34px">{{ stats.total }}</div>
      </div>
      <div class="stat-card" style="padding:8px 0 6px 0;border:none;">
        <div class="stat-title">成功次数</div>
        <div class="stat-value" style="font-size:34px;color:#389e0d">{{ stats.success }}</div>
      </div>
      <div class="stat-card" style="padding:8px 0 6px 0;border:none;">
        <div class="stat-title">失败次数</div>
        <div class="stat-value" style="font-size:34px;color:#cf1322">{{ stats.failure }}</div>
      </div>
      <div class="stat-card" style="padding:8px 0 6px 0;border:none;">
        <div class="stat-title">成功率</div>
        <div class="stat-value" style="font-size:34px">{{ stats.rate }}%</div>
      </div>
    </div>

    <a-table :columns="columns" :data-source="rows" :pagination="pagination" row-key="id" size="middle"
      :loading="loading" @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag color="success" v-if="record.status === 'SUCCESS'">成功</a-tag>
          <a-tag color="error" v-else-if="record.status === 'FAILURE'">失败</a-tag>
          <a-tag color="processing" v-else>执行中</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'environment'">
          <a-tag :color="record.environment === 'PROD' ? 'red' : 'orange'">{{ record.environment }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'cost'">
          <span>{{ record.durationMs ? record.durationMs + 'ms' : '-' }}</span>
        </template>
        <template v-else-if="column.key === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" type="primary" @click="showDetail(record)">详情</a-button>
            <a-button size="small" @click="showNodeLogs(record)">组件日志</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 执行详情弹窗 -->
    <a-modal v-model:open="detailVisible" title="执行详情" :width="900" :footer="null"
      :body-style="{ maxHeight: '70vh', overflowY: 'auto', padding: '16px 24px' }">
      <!-- 同批任务切换下拉框 -->
      <div v-if="scheduleLogs.length >= 1" style="margin-bottom:12px;display:flex;gap:8px;align-items:center;">
        <span style="font-size:13px;color:#666;white-space:nowrap;">关联任务 ({{ scheduleLogs.length }}) :</span>
        <a-select v-model:value="currentScheduleExecId" style="flex:1;max-width:400px;" size="small"
          @change="switchScheduleExecById">
          <a-select-option v-for="slog in scheduleLogs" :key="slog.id" :value="slog.executionId">
            {{ slog.taskName }} v{{ slog.taskVersion }}
            — {{ statusLabel(slog.status) }}
            — {{ slog.startTime }}
          </a-select-option>
        </a-select>
      </div>
      <div v-if="scheduleLoading" style="margin-bottom:12px;color:#999;font-size:13px;">加载关联任务中...</div>
      <div>
      <a-descriptions bordered :column="2" size="small">
        <a-descriptions-item label="执行ID" :span="2">{{ currentLog.executionId }}</a-descriptions-item>
        <a-descriptions-item label="任务ID">{{ currentLog.taskId }}</a-descriptions-item>
        <a-descriptions-item label="任务名称">{{ currentLog.taskName }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="currentLog.status === 'SUCCESS' ? 'success' : 'error'">{{ currentLog.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="任务版本">{{ currentLog.taskVersion }}</a-descriptions-item>
        <a-descriptions-item label="触发方式">{{ currentLog.triggerType }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ currentLog.startTime }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ currentLog.endTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{ currentLog.durationMs }}ms</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ currentLog.createdBy }}</a-descriptions-item>
        <a-descriptions-item label="环境">{{ currentLog.environment }}</a-descriptions-item>
        <a-descriptions-item label="输入参数(JSON)" :span="2">
          <pre class="log-json">{{ formatJson(currentLog.inputParams) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="输入参数(表格)" :span="2">
          <a-table
            size="small"
            :pagination="false"
            :data-source="toKvRows(currentLog.inputParams)"
            :columns="ioKvColumns"
            :scroll="{ x: 'max-content', y: 180 }"
            row-key="key"
          />
        </a-descriptions-item>
        <a-descriptions-item label="输出结果(JSON)" :span="2">
          <pre class="log-json">{{ formatJson(currentLog.outputResult) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="输出结果(表格)" :span="2">
          <a-table
            size="small"
            :pagination="false"
            :data-source="toKvRows(currentLog.outputResult)"
            :columns="ioKvColumns"
            :scroll="{ x: 'max-content', y: 180 }"
            row-key="key"
          />
        </a-descriptions-item>
        <a-descriptions-item v-if="currentLog.status === 'FAILURE'" label="失败原因" :span="2">
          <pre class="log-json error-json">{{ currentLog.errorMessage || '未知错误' }}</pre>
        </a-descriptions-item>
      </a-descriptions>
      </div>
    </a-modal>

    <!-- 组件日志可视化弹窗 -->
    <a-modal v-model:open="nodeLogVisible" title="组件执行日志" :width="1000" :footer="null"
      :body-style="{ maxHeight: '70vh', overflowY: 'auto', padding: '16px 24px' }">
      <div>
      <div class="node-log-layout">
        <!-- 左侧流程示意 -->
        <div class="node-flow-side">
          <div v-for="(node, index) in flowNodes" :key="node.id" class="flow-item-wrap">
            <div :class="['flow-node', node.status.toLowerCase()]" @click="selectedNode = node">
              <div class="node-display-name">{{ node.nodeName }}</div>
              <div class="node-display-id">{{ node.nodeId }}</div>
            </div>
            <div v-if="index < nodeLogs.length - 1" class="flow-arrow">↓</div>
          </div>
        </div>
        <!-- 右侧节点详情 -->
        <div class="node-detail-side">
          <a-descriptions v-if="selectedNode" bordered :column="1" size="small">
            <a-descriptions-item label="组件运行ID">{{ selectedNode.nodeId }}</a-descriptions-item>
            <a-descriptions-item label="组件名称">{{ selectedNode.nodeName }}</a-descriptions-item>
            <a-descriptions-item label="组件类型">
              <a-tag color="blue">{{ selectedNode.nodeType }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="组件编码">{{ selectedNode.componentCode || '-' }}</a-descriptions-item>
            <a-descriptions-item label="组件ID（业务组件）">{{ selectedNode.componentId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="selectedNode.status === 'SUCCESS' ? 'success' : 'error'">{{ selectedNode.status }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="开始时间">{{ selectedNode.startTime }}</a-descriptions-item>
            <a-descriptions-item label="结束时间">{{ selectedNode.endTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="耗时">{{ selectedNode.durationMs }}ms</a-descriptions-item>
            <a-descriptions-item label="重试次数">{{ selectedNode.retryCount }}</a-descriptions-item>
            <a-descriptions-item label="组件配置字段快照">
              <pre class="log-json-small">{{ formatJson(resolveFieldSnapshot(selectedNode)) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="IO参数定义快照">
              <pre class="log-json-small">{{ formatJson(resolveIoSchema(selectedNode)) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="入边来源(edgeFrom)">
              <pre class="log-json-small">{{ formatJson(resolveEdgeFrom(selectedNode)) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="出边去向(edgeTo)">
              <pre class="log-json-small">{{ formatJson(resolveEdgeTo(selectedNode)) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="输入参数">
              <pre class="log-json-small">{{ formatJson(selectedNode.inputData) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="输入参数(表格)">
              <a-table
                size="small"
                :pagination="false"
                :data-source="toKvRows(selectedNode.inputData)"
                :columns="ioKvColumns"
                :scroll="{ x: 'max-content', y: 140 }"
                row-key="key"
              />
            </a-descriptions-item>
            <a-descriptions-item label="输出结果">
              <pre class="log-json-small">{{ formatJson(selectedNode.outputData) }}</pre>
            </a-descriptions-item>
            <a-descriptions-item label="输出结果(表格)">
              <a-table
                size="small"
                :pagination="false"
                :data-source="toKvRows(selectedNode.outputData)"
                :columns="ioKvColumns"
                :scroll="{ x: 'max-content', y: 140 }"
                row-key="key"
              />
            </a-descriptions-item>
          </a-descriptions>
          <div v-else class="node-placeholder">请点击左侧节点查看详情</div>
        </div>
      </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import dayjs from 'dayjs';
import { taskApi } from '../api/task';
import { scheduleApi } from '../api/scheduleApi';
import { executionStore } from '../store/execution';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const rows = ref([]);
const stats = reactive({ total: 0, success: 0, failure: 0, rate: '0.00' });
const filters = reactive({ taskId: '', status: undefined });
const timeRange = ref(null);
const pagination = reactive({ total: 0, current: 1, pageSize: 10, showSizeChanger: true });

const detailVisible = ref(false);
const currentLog = ref({});
const scheduleLogs = ref([]);
const scheduleLoading = ref(false);
const currentScheduleExecId = ref(null);
const nodeLogVisible = ref(false);
const nodeLogs = ref([]);
const selectedNode = ref(null);
const flowNodes = computed(() => {
  const logs = Array.isArray(nodeLogs.value) ? nodeLogs.value : [];
  if (!logs.length) return [];

  // 优先按 DSL 连线排序（如果日志中包含 fromNodeId/toNodeId）
  const edges = logs
    .filter((n) => n.fromNodeId && n.toNodeId)
    .map((n) => ({ from: n.fromNodeId, to: n.toNodeId }));

  if (!edges.length) {
    return [...logs].sort((a, b) => {
      const ta = a?.startTime ? new Date(a.startTime).getTime() : 0;
      const tb = b?.startTime ? new Date(b.startTime).getTime() : 0;
      return ta - tb;
    });
  }

  const idToNode = new Map(logs.map((n) => [n.nodeId || n.id, n]));
  const indegree = {};
  const next = {};
  logs.forEach((n) => {
    const id = n.nodeId || n.id;
    indegree[id] = 0;
    next[id] = [];
  });
  edges.forEach((e) => {
    if (indegree[e.to] !== undefined) indegree[e.to] += 1;
    if (next[e.from]) next[e.from].push(e.to);
  });

  const q = Object.keys(indegree).filter((id) => indegree[id] === 0);
  const ordered = [];
  while (q.length) {
    const id = q.shift();
    const node = idToNode.get(id);
    if (node) ordered.push(node);
    (next[id] || []).forEach((to) => {
      indegree[to] -= 1;
      if (indegree[to] === 0) q.push(to);
    });
  }

  // 若存在环或缺失边信息，补齐未排序节点
  if (ordered.length < logs.length) {
    const exists = new Set(ordered.map((n) => n.nodeId || n.id));
    const rest = logs.filter((n) => !exists.has(n.nodeId || n.id));
    rest.sort((a, b) => {
      const ta = a?.startTime ? new Date(a.startTime).getTime() : 0;
      const tb = b?.startTime ? new Date(b.startTime).getTime() : 0;
      return ta - tb;
    });
    ordered.push(...rest);
  }

  return ordered;
});

const columns = [
  { title: '执行ID', dataIndex: 'executionId', width: 220 },
  { title: '任务名称', dataIndex: 'taskName' },
  { title: '环境', dataIndex: 'environment', width: 100 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', width: 180 },
  { title: '结束时间', dataIndex: 'endTime', width: 180 },
  { title: '耗时', dataIndex: 'cost', width: 100 },
  { title: '操作', key: 'op', width: 180 }
];

const ioKvColumns = [
  { title: '参数名', dataIndex: 'key', width: 180 },
  { title: '参数值', dataIndex: 'valueText' }
];

const loadLogs = async () => {
  loading.value = true;
  try {
    const params = {
      ...filters,
      current: pagination.current,
      size: pagination.pageSize
    };
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0].format('YYYY-MM-DD HH:mm:ss');
      params.endTime = timeRange.value[1].format('YYYY-MM-DD HH:mm:ss');
    }
    const res = await taskApi.pageLogs(params);
    const data = res.data?.data;
    if (data) {
      rows.value = data.records || [];
      pagination.total = data.total || 0;
    }

    // 获取真实统计数据
    const statsRes = await taskApi.getExecutionStats();
    if (statsRes.data?.data) {
      const statsData = statsRes.data.data;
      stats.total = statsData.total || 0;
      stats.success = statsData.success || 0;
      stats.failure = statsData.failure || 0;
      stats.rate = statsData.rate ? Number(statsData.rate).toFixed(2) : '0.00';
    }
  } catch (e) {
    message.error('加载日志失败：' + e.message);
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  Object.assign(filters, { taskId: '', status: undefined });
  timeRange.value = null;
  pagination.current = 1;
  loadLogs();
};

const handleTableChange = (pag) => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  loadLogs();
};

const showDetail = async (record) => {
  currentLog.value = record;
  currentScheduleExecId.value = record.executionId;
  if (record.scheduleJobId) {
    await loadScheduleLogs(record.scheduleJobId);
  } else {
    scheduleLogs.value = [];
  }
  detailVisible.value = true;
};

const loadScheduleLogs = async (scheduleJobId) => {
  scheduleLoading.value = true;
  try {
    const res = await scheduleApi.executions(scheduleJobId);
    const all = res.data?.data || [];
    console.log('scheduleLogs total:', scheduleJobId, all.length, 'records');
    // 取最近一批（30秒内触发的视为同批）
    if (all.length > 1) {
      const latest = all[0];
      const latestTime = new Date(latest.startTime).getTime();
      const filtered = all.filter(e =>
        Math.abs(new Date(e.startTime).getTime() - latestTime) < 2000
      );
      scheduleLogs.value = filtered;
      console.log('scheduleLogs filtered:', filtered.length, 'records, first:', filtered[0]?.executionId, filtered[0]?.startTime);
    } else {
      scheduleLogs.value = all;
      console.log('scheduleLogs single batch');
    }
    console.log('scheduleLogs after set:', scheduleLogs.value.length);
  } catch (e) {
    console.error('加载关联任务执行记录失败:', e);
    scheduleLogs.value = [];
  } finally {
    scheduleLoading.value = false;
  }
};

const switchScheduleExecById = (execId) => {
  const slog = scheduleLogs.value.find(e => e.executionId === execId);
  if (slog) {
    currentLog.value = slog;
    currentScheduleExecId.value = execId;
  }
};

// 关闭详情弹窗时清除 URL 参数，防止刷新后再次弹出
watch(detailVisible, (v) => {
  if (!v && (route.query.executionId || route.query.scheduleJobId)) {
    router.replace({ path: '/execute-log', query: {} });
  }
});

const showNodeLogs = async (record) => {
  try {
    const executionId = record.executionId || record.id;
    if (!executionId) {
      message.error('加载组件日志失败：缺少执行ID');
      return;
    }
    const res = await taskApi.getNodeLogs(executionId);
    nodeLogs.value = Array.isArray(res.data?.data) ? res.data.data : [];
    if (!nodeLogs.value.length) {
      message.warning('该次执行暂无组件日志（可能执行在入口校验阶段失败）');
    }
    selectedNode.value = flowNodes.value[0] || nodeLogs.value[0] || null;
    nodeLogVisible.value = true;
  } catch (e) {
    message.error('加载组件日志失败：' + (e?.message || '未知错误'));
  }
};

// 实时刷新：当全局执行状态变化时（有任务完成），自动重载日志列表
watch(() => executionStore.activeExecutions.map(e => e.status), (newStatuses, oldStatuses) => {
  if (!oldStatuses) return;
  const hasFinished = newStatuses.some((status, idx) =>
    status !== 'RUNNING' && oldStatuses[idx] === 'RUNNING'
  );
  if (hasFinished) {
    setTimeout(loadLogs, 1000); // 延迟 1s 确保后端落库完成
  }
}, { deep: true });

const statusLabel = (s) => ({ SUCCESS: '成功', FAILURE: '失败', TIMEOUT: '超时', RUNNING: '执行中' }[s] || s);

const formatJson = (str) => {
  if (!str) return '{}';
  try {
    const obj = typeof str === 'string' ? JSON.parse(str) : str;
    return JSON.stringify(obj, null, 2);
  } catch (e) {
    return str;
  }
};

const toKvRows = (raw) => {
  if (!raw) return [];
  let obj = raw;
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw);
    } catch (_) {
      return [{ key: 'value', valueText: String(raw) }];
    }
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
    return [{ key: 'value', valueText: JSON.stringify(obj) }];
  }
  return Object.keys(obj).map((k) => {
    const val = obj[k];
    return {
      key: k,
      valueText: typeof val === 'object' ? JSON.stringify(val) : String(val)
    };
  });
};

const hasValue = (v) => v !== undefined && v !== null && String(v).trim() !== '';

const resolveFieldSnapshot = (node) => {
  if (!node) return {};
  if (hasValue(node.fieldSnapshot)) return node.fieldSnapshot;
  if (hasValue(node.fieldValues)) return node.fieldValues;
  if (hasValue(node.componentFields)) return node.componentFields;
  return {};
};

const resolveIoSchema = (node) => {
  if (!node) return [];
  if (hasValue(node.ioSchema)) return node.ioSchema;
  if (hasValue(node.ioParams)) return node.ioParams;
  return [];
};

const resolveEdgeFrom = (node) => {
  if (!node) return [];
  if (hasValue(node.edgeFrom)) return node.edgeFrom;
  if (hasValue(node.fromNodeId)) return [node.fromNodeId];
  return [];
};

const resolveEdgeTo = (node) => {
  if (!node) return [];
  if (hasValue(node.edgeTo)) return node.edgeTo;
  if (hasValue(node.toNodeId)) return [node.toNodeId];
  return [];
};

onMounted(async () => {
  if (route.query.taskId) {
    filters.taskId = route.query.taskId;
  }
  await loadLogs();
  if (route.query.executionId) {
    const target = rows.value.find(r => r.executionId === route.query.executionId);
    if (target) {
      // 携带 scheduleJobId 进入，自动加载同批任务
      target.scheduleJobId = route.query.scheduleJobId || target.scheduleJobId;
      showDetail(target);
    }
  }
});
</script>

<style scoped>
.log-json {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  max-height: 200px;
  overflow: auto;
  margin: 0;
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.log-json-small {
  background: #f5f5f5;
  padding: 4px;
  border-radius: 4px;
  max-height: 120px;
  overflow: auto;
  margin: 0;
  font-family: monospace;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.error-json {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #cf1322;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.node-log-layout {
  display: flex;
  gap: 20px;
  height: 600px;
  overflow: hidden;
}

.node-flow-side {
  width: 260px;
  min-width: 260px;
  border-right: 1px solid #f0f0f0;
  padding-right: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node-detail-side {
  flex: 1;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

/* 防止 a-descriptions 表格撑出弹窗 */
:deep(.ant-descriptions-view table) {
  table-layout: fixed;
  width: 100%;
}

:deep(.ant-descriptions-item-label) {
  width: 120px;
  word-break: keep-all;
}

:deep(.ant-descriptions-item-content) {
  overflow: auto;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.flow-item-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.flow-node {
  width: 180px;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
}

.node-display-name {
  font-weight: 500;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-display-id {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-node:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.flow-node.success {
  border-color: #b7eb8f;
  background: #f6ffed;
}

.flow-node.failure {
  border-color: #ffa39e;
  background: #fff1f0;
}

.flow-arrow {
  font-size: 20px;
  color: #bfbfbf;
  margin: 4px 0;
}

.node-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>

<style>
/* 强制约束 a-descriptions 表格不超出弹窗 — 非 scoped 以穿透组件层级 */
.ant-modal-body {
  overflow: hidden;
}

.ant-modal-body .ant-descriptions-view table {
  table-layout: fixed !important;
  width: 100% !important;
}

.ant-modal-body .ant-descriptions-item-content {
  overflow: auto;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.ant-modal-body .ant-descriptions-item-label {
  width: 120px;
  white-space: nowrap;
}
</style>
