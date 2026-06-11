<template>
  <div class="global-execution-center" :style="{ left: ballPos.x + 'px', top: ballPos.y + 'px' }">
    <!-- 悬浮球 -->
    <div ref="ballRef" class="floating-ball" :class="{ 'is-running': isAnyRunning, 'is-minimized': isMinimized }"
      @mousedown="handleMouseDown" @dblclick.stop="toggleMinimize" title="双击展开/收起，拖拽可移动">
      <div class="ball-content">
        <template v-if="isAnyRunning">
          <LoadingOutlined spin />
        </template>
        <template v-else>
          <RocketOutlined v-if="isMinimized" />
          <span v-else>{{ executionStore.activeExecutions.length || 'Go' }}</span>
        </template>
      </div>
    </div>

    <!-- 监控/执行面板 -->
    <div v-if="!isMinimized" ref="panelRef" class="center-panel" :style="panelStyle">
      <div class="panel-header">
        <div class="header-left header-tabs">
          <a-button type="link" size="small" :class="{ 'tab-active': viewMode === 'list' }" @click="viewMode = 'list'">
            可执行任务
          </a-button>
          <a-button type="link" size="small" :class="{ 'tab-active': viewMode === 'monitor' }" @click="viewMode = 'monitor'">
            正在执行（{{ runningExecutions.length }}）
          </a-button>
        </div>
        <div class="header-right">
        </div>
      </div>

      <div class="panel-body">
        <!-- 模式1：任务选择列表 (执行入口) -->
        <div v-if="viewMode === 'list'" class="task-list-view">
          <div class="search-bar">
            <a-input-search v-model:value="searchText" placeholder="搜索可执行任务" size="small" @search="fetchTasks" />
          </div>
          <div class="task-items">
            <a-spin :spinning="loading">
              <div v-for="task in availableTasks" :key="task.id" class="task-item">
                <div class="task-main">
                  <span class="task-name" :title="task.taskName">{{ task.taskName }}</span>
                  <a-tag size="small" color="green">v{{ task.prodVersion }}</a-tag>
                </div>
                <a-button type="primary" size="small" shape="circle" @click="handleRun(task)">
                  <PlayCircleOutlined />
                </a-button>
              </div>
              <div v-if="availableTasks.length === 0 && !loading" class="empty-tip">
                {{ searchText ? '未找到相关任务' : '暂无已发布的生产任务' }}
              </div>
            </a-spin>
          </div>
        </div>

        <!-- 模式2：实时执行监控 -->
        <div v-else class="monitor-view">
          <div v-if="runningExecutions.length === 0" class="empty-tip">当前没有正在执行的任务</div>
          <div v-for="exec in runningExecutions" :key="exec.executionId" class="exec-card">
            <div class="exec-card-header">
              <span class="exec-task-name">{{ exec.taskName }}</span>
              <a-tag :color="getStatusColor(exec.status)" size="small">{{ exec.status }}</a-tag>
              <a-button type="link" size="small" @click.stop="goToLog(exec.executionId)" title="查看执行日志详情" style="padding: 0 4px">
                <FileTextOutlined />
              </a-button>
              <CloseOutlined class="close-btn" @click="executionStore.removeExecution(exec.executionId)" />
            </div>

            <div class="realtime-flow">
              <div v-for="(node, idx) in sortNodes(exec.nodes, exec.nodeOrder)" :key="node.id" class="flow-step">
                <div class="step-line" v-if="idx > 0" :class="{ 'line-active': node.status !== 'PENDING' }"></div>
                <div class="step-node" :class="String(node.status || 'PENDING').toLowerCase()">
                  <div class="node-icon">
                    <LoadingOutlined v-if="node.status === 'RUNNING'" />
                    <CheckOutlined v-else-if="node.status === 'SUCCESS'" />
                    <CloseOutlined v-else-if="node.status === 'FAILURE'" />
                    <ClockCircleOutlined v-else />
                  </div>
                  <span class="node-name">{{ node.name }}</span>
                </div>
              </div>
              <div v-if="exec.nodes.length === 0" class="flow-init">
                <template v-if="String(exec.status || '').toUpperCase() === 'RUNNING'">
                  <LoadingOutlined spin /> 正在解析执行链路...
                </template>
                <template v-else>
                  执行已结束，暂无链路节点数据
                </template>
              </div>
            </div>

            <div v-if="exec.status === 'FAILURE'" class="error-msg">
              {{ exec.errorMessage || '执行发生错误' }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <TaskTestModal v-model:open="testModalVisible" :taskId="testTaskId" :version="testVersionName"
      :dslContent="testDslContent" :environment="testEnvironment" @close="testModalVisible = false"
      @success="(exec) => { executionStore.activeExecutions.push(exec); viewMode = 'monitor'; }" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { executionStore } from '../store/execution';
import {
  LoadingOutlined,
  RocketOutlined,
  PlayCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  ClockCircleOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue';
import { taskApi } from '../api/task';
import { message } from 'ant-design-vue';
import TaskTestModal from './TaskTestModal.vue';

const isMinimized = ref(true);
const viewMode = ref('list'); // 'list' or 'monitor'
const searchText = ref('');
const availableTasks = ref([]);
const loading = ref(false);

// 任务执行弹窗相关
const testModalVisible = ref(false);
const testTaskId = ref(null);
const testVersionName = ref('');
const testDslContent = ref('');
const testEnvironment = ref('PROD');

// 拖拽相关
const ballPos = ref({ x: 0, y: 0 });
const isDragging = ref(false);
const startPos = ref({ x: 0, y: 0 });
const ballRef = ref(null);
const panelRef = ref(null);

const panelStyle = computed(() => {
  const style = { position: 'absolute' };
  const windowWidth = window.innerWidth;
  const windowHeight = window.innerHeight;

  // 根据悬浮球位置决定面板弹出方向
  if (ballPos.value.x > windowWidth / 2) {
    style.right = '0';
  } else {
    style.left = '0';
  }

  if (ballPos.value.y > windowHeight / 2) {
    style.bottom = '72px';
  } else {
    style.top = '72px';
  }

  return style;
});

const runningExecutions = computed(() =>
  executionStore.activeExecutions.filter(e => String(e.status || '').toUpperCase() === 'RUNNING')
);

const isAnyRunning = computed(() => runningExecutions.value.length > 0);

const router = useRouter();
const goToLog = (executionId) => {
  router.push({ path: '/execute-log', query: { executionId } });
};

// 拖拽处理
const handleMouseDown = (e) => {
  isDragging.value = false;
  startPos.value = { x: e.clientX - ballPos.value.x, y: e.clientY - ballPos.value.y };

  const handleMouseMove = (moveEvent) => {
    isDragging.value = true;
    ballPos.value = {
      x: moveEvent.clientX - startPos.value.x,
      y: moveEvent.clientY - startPos.value.y
    };
  };

  const handleMouseUp = () => {
    document.removeEventListener('mousemove', handleMouseMove);
    document.removeEventListener('mouseup', handleMouseUp);
  };

  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
};

const toggleMinimize = () => {
  isMinimized.value = !isMinimized.value;
  if (!isMinimized.value) {
    if (executionStore.activeExecutions.length > 0) {
      viewMode.value = 'monitor';
    } else {
      viewMode.value = 'list';
      fetchTasks();
    }
  }
};

const fetchTasks = async () => {
  loading.value = true;
  try {
    const res = await taskApi.page({
      keyword: searchText.value,
      current: 1,
      size: 50
    });
    const records = res.data?.data?.records || [];

    // 筛选有生产版本的任务
    const tasksWithProd = await Promise.all(records.map(async (t) => {
      try {
        const vRes = await taskApi.getTaskVersions(t.id, { environment: 'PROD' });
        const currentProd = (vRes.data?.data || []).find(v => v.isCurrent === 1);
        return currentProd ? { ...t, prodVersion: currentProd.version } : null;
      } catch { return null; }
    }));

    availableTasks.value = tasksWithProd.filter(t => t !== null);
  } finally {
    loading.value = false;
  }
};

const handleRun = async (task) => {
  if (String(task.status) !== '1') {
    message.warning('任务已禁用，无法执行');
    return;
  }
  try {
    message.loading({ content: `正在启动任务: ${task.taskName}`, key: 'run_task' });
    
    // 1. 获取当前生产版本的 DSL，以提取默认参数
    const resDsl = await taskApi.current(task.id, 'PROD');
    const versionData = resDsl.data?.data;
    if (!versionData) {
      message.warning({ content: '该任务暂无已发布的生产版本', key: 'run_task' });
      return;
    }

    // 2. 提取 START 节点的默认参数（用于传递给执行引擎）
    const dsl = JSON.parse(versionData.dslContent || versionData.dslJson || '{}');
    const defaultParams = {};
    const initialNodes = [];
    const nodeOrder = buildNodeOrderByDsl(dsl);

    if (dsl.nodes && Array.isArray(dsl.nodes)) {
      dsl.nodes.forEach(node => {
        const nodeId = node.id || node.nodeId;
        const nodeName = node.name || node.label || node.title || nodeId || '未命名节点';
        if (nodeId) {
          initialNodes.push({ id: nodeId, name: nodeName, status: 'PENDING' });
        }

        // START 节点的 inputParams 作为任务输入参数传递
        if (node.type === 'START' && node.inputParams && Array.isArray(node.inputParams) && nodeId) {
          const nodeParams = {};
          node.inputParams.forEach(param => {
            if (param.paramCode) {
              nodeParams[param.paramCode] = param.sourceType === 'CONST' ? (param.sourceValue || '') : '';
            }
          });
          if (Object.keys(nodeParams).length > 0) {
            defaultParams[nodeId] = nodeParams;
          }
        }
      });
    }

    // 3. 执行任务（生产环境必须显式携带版本ID，避免后端误判未选版本）
    const execPayload = {
      ...defaultParams,
      versionId: versionData.id
    };
    const resExec = await taskApi.execute(task.id, execPayload);
    const executionId = resExec.data?.data;

    if (executionId) {
      message.success({ content: '启动成功', key: 'run_task' });
      executionStore.addExecution({
        executionId,
        taskId: task.id,
        taskName: task.taskName,
        status: 'RUNNING',
        nodes: initialNodes,
        nodeOrder
      });
      viewMode.value = 'monitor';
    }
  } catch (e) {
    message.error({ content: '启动失败: ' + e.message, key: 'run_task' });
  }
};

const getStatusColor = (status) => {
  const map = { 'RUNNING': 'blue', 'SUCCESS': 'success', 'FAILURE': 'error' };
  return map[String(status || '').toUpperCase()] || 'default';
};

const buildNodeOrderByDsl = (dsl) => {
  const nodes = Array.isArray(dsl?.nodes) ? dsl.nodes : [];
  const edges = Array.isArray(dsl?.edges) ? dsl.edges : [];

  const nodeMap = new Map();
  nodes.forEach((n) => {
    const id = n?.id || n?.nodeId;
    if (!id) return;
    const y = Number(n?.y ?? n?.position?.y ?? n?.pos?.y);
    const x = Number(n?.x ?? n?.position?.x ?? n?.pos?.x);
    nodeMap.set(id, {
      id,
      y: Number.isFinite(y) ? y : Number.MAX_SAFE_INTEGER,
      x: Number.isFinite(x) ? x : Number.MAX_SAFE_INTEGER
    });
  });

  const indegree = new Map();
  const graph = new Map();
  nodeMap.forEach((_, id) => {
    indegree.set(id, 0);
    graph.set(id, []);
  });

  edges.forEach((e) => {
    const from = e?.source || e?.from || e?.sourceNodeId;
    const to = e?.target || e?.to || e?.targetNodeId;
    if (!nodeMap.has(from) || !nodeMap.has(to)) return;
    graph.get(from).push(to);
    indegree.set(to, (indegree.get(to) || 0) + 1);
  });

  const queue = [];
  indegree.forEach((deg, id) => {
    if (deg === 0) queue.push(id);
  });

  const sortByTopDown = (a, b) => {
    const ya = nodeMap.get(a)?.y ?? Number.MAX_SAFE_INTEGER;
    const yb = nodeMap.get(b)?.y ?? Number.MAX_SAFE_INTEGER;
    if (ya !== yb) return ya - yb;

    const xa = nodeMap.get(a)?.x ?? Number.MAX_SAFE_INTEGER;
    const xb = nodeMap.get(b)?.x ?? Number.MAX_SAFE_INTEGER;
    if (xa !== xb) return xa - xb;

    return String(a).localeCompare(String(b));
  };

  queue.sort(sortByTopDown);

  const order = [];
  while (queue.length) {
    const id = queue.shift();
    order.push(id);

    const nextList = graph.get(id) || [];
    nextList.forEach((next) => {
      const nextDeg = (indegree.get(next) || 0) - 1;
      indegree.set(next, nextDeg);
      if (nextDeg === 0) {
        queue.push(next);
      }
    });

    queue.sort(sortByTopDown);
  }

  if (order.length !== nodeMap.size) {
    const rest = [...nodeMap.keys()].filter(id => !order.includes(id)).sort(sortByTopDown);
    order.push(...rest);
  }

  return order;
};

const sortNodes = (nodes, nodeOrder = []) => {
  const list = Array.isArray(nodes) ? [...nodes] : [];
  const orderMap = new Map((Array.isArray(nodeOrder) ? nodeOrder : []).map((id, idx) => [String(id), idx]));

  return list.sort((a, b) => {
    const oa = orderMap.has(String(a?.id)) ? orderMap.get(String(a.id)) : Number.MAX_SAFE_INTEGER;
    const ob = orderMap.has(String(b?.id)) ? orderMap.get(String(b.id)) : Number.MAX_SAFE_INTEGER;
    if (oa !== ob) return oa - ob;

    const sa = Number.isFinite(Number(a?.seq)) ? Number(a.seq) : Number.MAX_SAFE_INTEGER;
    const sb = Number.isFinite(Number(b?.seq)) ? Number(b.seq) : Number.MAX_SAFE_INTEGER;
    if (sa !== sb) return sa - sb;

    const ta = a?.startTime ? new Date(a.startTime).getTime() : Number.MAX_SAFE_INTEGER;
    const tb = b?.startTime ? new Date(b.startTime).getTime() : Number.MAX_SAFE_INTEGER;
    if (ta !== tb) return ta - tb;

    return String(a?.id || '').localeCompare(String(b?.id || ''));
  });
};

// 轮询逻辑
let timers = {};
const startPolling = (exec) => {
  if (timers[exec.executionId]) return;
  timers[exec.executionId] = setInterval(async () => {
    try {
      const logRes = await taskApi.getLogDetail(exec.executionId);
      const log = logRes.data?.data;
      if (!log) return;

      const currentExec = executionStore.activeExecutions.find(e => e.executionId === exec.executionId);
      if (!currentExec) return;

      const status = String(log.status || '').toUpperCase();
      currentExec.status = status || 'RUNNING';
      currentExec.errorMessage = log.errorMessage;

      // 节点日志加载失败不应影响任务整体状态更新
      try {
        const nodeRes = await taskApi.getNodeLogs(exec.executionId);
        const nodes = Array.isArray(nodeRes.data?.data) ? nodeRes.data.data : [];
        if (nodes.length > 0) {
          currentExec.nodes = nodes.map(n => ({
            id: n.nodeId,
            name: n.nodeName,
            status: String(n.status || 'PENDING').toUpperCase(),
            startTime: n.startTime,
            seq: n.seq
          }));
        }
      } catch (_) {
        // ignore
      }

      if (!['RUNNING', 'PENDING'].includes(status)) {
        clearInterval(timers[exec.executionId]);
        delete timers[exec.executionId];
      }
    } catch (e) {
      console.error(e);
      // 接口异常时不应永久显示“正在执行”，避免与执行日志页不一致
      clearInterval(timers[exec.executionId]);
      delete timers[exec.executionId];
      const currentExec = executionStore.activeExecutions.find(item => item.executionId === exec.executionId);
      if (currentExec && String(currentExec.status || '').toUpperCase() === 'RUNNING') {
        currentExec.status = 'FAILURE';
        currentExec.errorMessage = '执行状态同步失败，请刷新后重试';
      }
    }
  }, 2000);
};

watch(() => executionStore.activeExecutions.map(e => `${e.executionId}:${e.status}`), () => {
  executionStore.activeExecutions.forEach(e => {
    if (String(e.status || '').toUpperCase() === 'RUNNING') startPolling(e);
  });
}, { immediate: true, deep: true });

onMounted(() => {
  // 初始位置：右下角
  ballPos.value = {
    x: window.innerWidth - 80,
    y: window.innerHeight - 80
  };

  if (!isMinimized.value && viewMode.value === 'list') {
    fetchTasks();
  }
});

// 监听窗口大小变化，防止球跑出屏幕
window.addEventListener('resize', () => {
  if (ballPos.value.x > window.innerWidth - 60) {
    ballPos.value.x = window.innerWidth - 80;
  }
  if (ballPos.value.y > window.innerHeight - 60) {
    ballPos.value.y = window.innerHeight - 80;
  }
});
</script>

<style scoped>
.global-execution-center {
  position: fixed;
  z-index: 9999;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  pointer-events: none;
  /* 让容器不阻挡鼠标事件，但子元素需要开启 */
}

.floating-ball {
  pointer-events: auto;
  width: 56px;
  height: 56px;
  border-radius: 28px;
  background: linear-gradient(135deg, #1890ff 0%, #0050b3 100%);
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: grab;
  transition: transform 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275), box-shadow 0.2s;
  user-select: none;
  font-size: 20px;
}

.floating-ball:active {
  cursor: grabbing;
}

.floating-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(24, 144, 255, 0.6);
}

.floating-ball.is-minimized {
  /* 可以在这里添加收起时的特殊样式 */
}

.floating-ball.is-running {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.7);
  }

  70% {
    transform: scale(1.05);
    box-shadow: 0 0 0 15px rgba(24, 144, 255, 0);
  }

  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(24, 144, 255, 0);
  }
}

.center-panel {
  pointer-events: auto;
  position: absolute;
  width: 340px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #f0f0f0;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    transform: translateY(20px);
    opacity: 0;
  }

  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.panel-header {
  padding: 12px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
}

.header-tabs :deep(.ant-btn-link) {
  padding: 0 6px;
  color: #595959;
}

.header-tabs :deep(.ant-btn-link.tab-active) {
  color: #1677ff;
  font-weight: 600;
}

.panel-body {
  max-height: 460px;
  overflow-y: auto;
  background: #fff;
}

/* 任务列表样式 */
.task-list-view {
  padding: 12px;
}

.search-bar {
  margin-bottom: 12px;
}

.task-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  padding: 10px 12px;
  background: #fdfdfd;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s;
}

.task-item:hover {
  border-color: #1890ff;
  background: #f0f7ff;
}

.task-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.task-name {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-tip {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

/* 监控视图样式 */
.monitor-view {
  padding: 12px;
}

.exec-card {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid #f0f0f0;
}

.exec-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.exec-task-name {
  font-weight: 600;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.close-btn {
  color: #bfbfbf;
  cursor: pointer;
}

.close-btn:hover {
  color: #f5222d;
}

.realtime-flow {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
  padding-left: 10px;
}

.flow-step {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
}

.step-line {
  position: absolute;
  left: 11px;
  top: -12px;
  width: 2px;
  height: 12px;
  background: #f0f0f0;
}

.line-active {
  background: #1890ff;
}

.step-node {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid #eee;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  z-index: 1;
}

.step-node.running .node-icon {
  border-color: #1890ff;
  color: #1890ff;
  box-shadow: 0 0 8px rgba(24, 144, 255, 0.3);
}

.step-node.success .node-icon {
  border-color: #52c41a;
  color: #52c41a;
  background: #f6ffed;
}

.step-node.failure .node-icon {
  border-color: #f5222d;
  color: #f5222d;
  background: #fff1f0;
}

.node-name {
  font-size: 13px;
  color: #555;
}

.error-msg {
  margin-top: 12px;
  padding: 8px;
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 4px;
  color: #f5222d;
  font-size: 12px;
}

.flow-init {
  padding: 20px;
  text-align: center;
  color: #999;
}
</style>
