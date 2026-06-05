<template>
  <div class="schedule-page">
    <div class="page-header">
      <h3>定时任务管理</h3>
      <a-button type="primary" @click="showCreateModal">新建定时任务</a-button>
    </div>

    <a-table :columns="columns" :data-source="jobs" :loading="loading" row-key="id" size="middle">
      <template #status="{ record }">
        <a-tag :color="record.status === 1 ? 'green' : 'red'">
          {{ record.status === 1 ? '启用' : '停用' }}
        </a-tag>
      </template>
      <template #cron="{ record }">
        <code>{{ record.cronExpression }}</code>
      </template>
      <template #blockStrategy="{ record }">
        <a-tag :color="blockColor(record.blockStrategy)">
          {{ blockLabel(record.blockStrategy) }}
        </a-tag>
      </template>
      <template #lastFire="{ record }">
        <template v-if="record.lastFireTime && record.lastExecutionId">
          <a @click="viewScheduleExecutions(record)">{{ record.lastFireTime }}</a>
          <span v-if="record.jobTasks && record.jobTasks.length > 1"
            style="color:#999; font-size:12px; margin-left:4px;">
            ({{ record.jobTasks.length }}个任务)
          </span>
        </template>
        <span v-else>未执行</span>
      </template>
      <template #retry="{ record }">
        <span v-if="record.currentRetry > 0">
          <a-tag color="orange">{{ record.currentRetry }}/{{ record.retryCount || 0 }}</a-tag>
        </span>
        <span v-else>-</span>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button size="small" @click="editJob(record)">编辑</a-button>
          <a-button size="small" @click="toggleJob(record)">
            {{ record.status === 1 ? '停用' : '启用' }}
          </a-button>
          <a-button size="small" @click="triggerJob(record)">手动触发</a-button>
          <a-button size="small" @click="showStats(record)">统计</a-button>
          <a-popconfirm title="确认删除?" @confirm="deleteJob(record)">
            <a-button size="small" danger>删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <!-- 创建/编辑弹窗 -->
    <a-modal v-model:open="modalVisible" :title="editingId ? '编辑定时任务' : '新建定时任务'" @ok="handleSave"
      @cancel="modalVisible = false" width="1200px">
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical">
        <div class="modal-content">
          <!-- 左侧：表单 -->
          <div class="layout-left">
            <div class="section-card">
              <div class="section-title">基本信息</div>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="定时任务编码" required name="jobCode">
                    <a-input v-model:value="form.jobCode" placeholder="唯一编码" :disabled="!!editingId" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="环境" required name="environment">
                    <a-select v-model:value="form.environment" @change="onEnvironmentChange">
                      <a-select-option value="TEST">TEST（测试环境）</a-select-option>
                      <a-select-option value="PROD">PROD（生产环境）</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <!-- 多任务关联列表 -->
              <a-form-item label="关联任务" required name="tasks" help="点击任务行切换右侧参数配置 | 环境为空则继承定时任务的环境">
                <div v-for="(taskItem, index) in form.tasks" :key="index"
                  :class="['task-item-row', { 'task-item-active': activeTaskIndex === index }]"
                  @click="switchActiveTask(index)">
                  <a-select v-model:value="taskItem.taskId" placeholder="选择任务" show-search
                    :filter-option="filterTaskOption" @change="(val) => onTaskItemChange(index, val)"
                    @click.stop :loading="taskLoading" style="flex:2.5">
                    <a-select-option v-for="t in taskList" :key="t.id" :value="t.id">
                      {{ t.taskName }} ({{ t.taskCode }})
                    </a-select-option>
                  </a-select>
                  <a-select v-model:value="taskItem.environment" placeholder="环境"
                    @click.stop @change="(val) => onTaskEnvChange(index, val)"
                    style="width:80px; flex-shrink:0" allowClear>
                    <a-select-option value="TEST">TEST</a-select-option>
                    <a-select-option value="PROD">PROD</a-select-option>
                  </a-select>
                  <a-select v-model:value="taskItem.taskVersionId" placeholder="选择版本"
                    @click.stop @change="(val) => onTaskVersionChange(index, val)"
                    style="flex:1.5" :disabled="!taskItem.taskId">
                    <a-select-option v-for="v in getVersionsForTaskItem(taskItem)" :key="v.id" :value="v.id">
                      {{ v.version }}
                    </a-select-option>
                  </a-select>
                  <a-tag v-if="activeTaskIndex === index" color="blue" style="margin-left:4px">当前</a-tag>
                  <a-button v-if="form.tasks.length > 1"
                    size="small" danger @click.stop="removeTaskItem(index)">✕</a-button>
                </div>
                <a-button type="dashed" size="small" @click="addTaskItem"
                  :disabled="form.tasks.length >= 10" style="margin-top:4px">
                  + 添加任务
                </a-button>
              </a-form-item>

              <a-form-item label="Cron 表达式" required name="cronExpression">
                <a-input v-model:value="form.cronExpression" placeholder="0 0 2 * * ?">
                  <template #suffix>
                    <a-tooltip title="秒 分 时 日 月 周(6段)">
                      <span style="cursor:help;color:#999">?</span>
                    </a-tooltip>
                  </template>
                </a-input>
                <div style="margin-top:8px;display:flex;flex-wrap:wrap;gap:6px;">
                  <a-tag v-for="p in cronPresets" :key="p.label" :color="form.cronExpression === p.value ? 'blue' : 'default'"
                    style="cursor:pointer" @click="form.cronExpression = p.value">
                    {{ p.label }}
                  </a-tag>
                </div>
              </a-form-item>
            </div>

            <div class="section-card">
              <div class="section-title">高级设置</div>

              <a-row :gutter="16">
                <a-col :span="8">
                  <a-form-item label="失败重试次数">
                    <a-input-number v-model:value="form.retryCount" :min="0" :max="10" style="width:100%" placeholder="0" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="重试间隔(秒)">
                    <a-input-number v-model:value="form.retryInterval" :min="1" :max="3600" style="width:100%" placeholder="60" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="执行超时(秒, 0=不限)">
                    <a-input-number v-model:value="form.executorTimeout" :min="0" :max="86400" style="width:100%" placeholder="0" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="8">
                  <a-form-item label="并发策略">
                    <a-select v-model:value="form.blockStrategy">
                      <a-select-option value="SKIP">SKIP（跳过）</a-select-option>
                      <a-select-option value="QUEUE">QUEUE（排队）</a-select-option>
                      <a-select-option value="COVER">COVER（覆盖）</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="排队上限">
                    <a-input-number v-model:value="form.maxQueueSize" :min="1" :max="100" style="width:100%"
                      :disabled="form.blockStrategy !== 'QUEUE'" placeholder="5" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="错过触发策略">
                    <a-select v-model:value="form.misfireStrategy">
                      <a-select-option value="IGNORE">IGNORE（忽略）</a-select-option>
                      <a-select-option value="FIRE_ONCE">FIRE_ONCE（触发一次）</a-select-option>
                      <a-select-option value="FIRE_ALL">FIRE_ALL（追回所有）</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="8">
                  <a-form-item label="时间窗口-开始">
                    <a-time-picker v-model:value="form.windowStart" format="HH:mm:ss" placeholder="不限" style="width:100%" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="时间窗口-结束">
                    <a-time-picker v-model:value="form.windowEnd" format="HH:mm:ss" placeholder="不限" style="width:100%" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="告警邮箱">
                    <a-input v-model:value="form.alarmEmail" placeholder="多个逗号分隔" />
                  </a-form-item>
                </a-col>
              </a-row>
            </div>
          </div>

          <!-- 右侧：流程画布 + 参数配置 -->
          <div class="layout-right">
            <div class="section-card" v-if="activeTask && activeTask.taskVersionId">
              <div class="section-title">
                流程可视化
                <a-tag color="blue" style="margin-left:8px">
                  {{ getActiveTaskName() }}
                </a-tag>
              </div>
              <div class="flow-viewer-mini" v-if="currentDslContent">
                <FlowViewer :dsl-content="currentDslContent" @node-click="onFlowNodeClick" />
              </div>
              <a-empty v-else-if="!paramsLoading" description="暂无流程数据" style="margin: 20px 0;" />

              <a-divider v-if="selectedFlowNode" style="margin: 12px 0;" />
              <div v-if="selectedFlowNode" class="section-title" style="margin-top: 0;">
                参数配置: {{ selectedFlowNode.name || selectedFlowNode.id }}
              </div>
              <a-table
                v-if="selectedFlowNode && filteredIoParams.length > 0"
                :columns="ioParamTableColumns"
                :data-source="filteredIoParams"
                :pagination="false"
                size="small"
                bordered
                row-key="id"
                :scroll="{ x: 'max-content', y: 300 }"
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
                    <a-tag>{{ sourceTypeLabel(record.sourceType) }}</a-tag>
                  </template>
                  <template v-else-if="column.dataIndex === 'sourceValue'">
                    <a-input
                      v-if="record.ioType === 'INPUT'"
                      v-model:value="paramsConfig[record.id]"
                      @change="(e) => onParamValueChange(record, e.target.value)"
                      :placeholder="formatSourceDisplay(record.sourceValue)"
                      size="small"
                    />
                    <span v-else class="source-value-text">{{ getOutputDisplayValue(record) }}</span>
                  </template>
                </template>
              </a-table>
              <a-empty v-else-if="selectedFlowNode" description="该节点暂无参数配置" />
              <a-empty v-else-if="currentDslContent && !paramsLoading" description="点击画布中的节点查看参数" style="margin: 16px 0;" />
            </div>
            <div class="empty-placeholder" v-else>
              <a-empty :description="form.tasks.some(t => t.taskId) ? '请为当前任务选择版本以配置参数' : '请先选择关联任务和版本'" />
            </div>
          </div>
        </div>
      </a-form>
    </a-modal>

    <!-- 统计弹窗 -->
    <a-modal v-model:open="statsVisible" title="调度统计" :footer="null" width="520px" @cancel="statsVisible = false">
      <a-spin :spinning="statsLoading">
        <template v-if="statsError">
          <a-alert type="error" :message="statsError" show-icon style="margin-bottom:12px;" />
        </template>
        <a-descriptions v-if="statsSummary" :column="2" size="small" bordered>
          <a-descriptions-item label="调度编码">{{ statsSummary.jobCode }}</a-descriptions-item>
          <a-descriptions-item label="成功率">{{ statsSummary.successRate }}%</a-descriptions-item>
          <a-descriptions-item label="总执行次数">{{ statsSummary.totalExecutions }}</a-descriptions-item>
          <a-descriptions-item label="成功次数">{{ statsSummary.totalSuccess }}</a-descriptions-item>
          <a-descriptions-item label="失败次数">{{ statsSummary.totalFailure }}</a-descriptions-item>
          <a-descriptions-item label="超时次数">{{ statsSummary.totalTimeout }}</a-descriptions-item>
          <a-descriptions-item label="正在运行">{{ statsSummary.totalRunning || 0 }}</a-descriptions-item>
          <a-descriptions-item label="平均耗时">{{ statsSummary.avgDurationMs || 0 }}ms</a-descriptions-item>
        </a-descriptions>
        <a-empty v-if="!statsSummary && !statsLoading && !statsError" description="暂无统计数据，请先触发执行" />
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { scheduleApi } from '../api/scheduleApi';
import { taskApi } from '../api/task';
import { componentApi } from '../api/componentApi';
import FlowViewer from '../components/FlowViewer.vue';

const router = useRouter();

const formRef = ref();
const formRules = {
  jobCode: [{ required: true, message: '请输入定时任务编码', trigger: 'blur' }],
  environment: [{ required: true, message: '请选择环境', trigger: 'change' }],
  tasks: [{
    type: 'array', required: true, min: 1,
    message: '请至少添加一个关联任务', trigger: 'change',
    validator: (_rule, value) => {
      if (!value || value.length === 0) return Promise.reject('请至少添加一个关联任务');
      for (const item of value) {
        if (!item.taskId) return Promise.reject('请为每个关联任务选择任务');
        if (!item.taskVersionId) return Promise.reject('请为每个关联任务选择版本');
      }
      return Promise.resolve();
    }
  }],
  cronExpression: [
    { required: true, message: '请输入Cron表达式', trigger: 'blur' },
    { pattern: /^[0-9*,/\-?A-Za-z]+\s[0-9*,/\-?A-Za-z]+\s[0-9*,/\-?A-Za-z]+\s[0-9*,/\-?A-Za-z]+\s[0-9*,/\-?A-Za-z]+\s[0-9*,/\-?A-Za-z#]+$/, message: 'Cron表达式格式不正确(6段)', trigger: 'blur' }
  ]
};

const loading = ref(false);
const jobs = ref([]);
const modalVisible = ref(false);
const editingId = ref(null);
const taskList = ref([]);
const taskLoading = ref(false);
const versionList = ref([]);
const versionLoading = ref(false);

// 统计弹窗
const statsVisible = ref(false);
const statsLoading = ref(false);
const statsSummary = ref(null);
const statsError = ref('');

// 参数配置
const ioParamsList = ref([]);
const currentDslContent = ref(null);
const paramsLoading = ref(false);
const paramsConfig = ref({});
const activeTaskIndex = ref(0);

const activeTask = computed(() => {
  const tasks = form.value.tasks;
  if (!tasks || tasks.length === 0) return null;
  const idx = Math.min(activeTaskIndex.value, tasks.length - 1);
  return tasks[idx] || tasks[0] || null;
});

const selectedFlowNode = ref(null);
const filteredIoParams = computed(() => {
  if (!selectedFlowNode.value) return [];
  return ioParamsList.value.filter(p => p.nodeId === selectedFlowNode.value.id);
});

const cronPresets = [
  { label: '每1分钟', value: '0 * * * * ?' },
  { label: '每5分钟', value: '0 */5 * * * ?' },
  { label: '每30分钟', value: '0 */30 * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天凌晨2点', value: '0 0 2 * * ?' },
  { label: '每天早上9点', value: '0 0 9 * * ?' },
  { label: '工作日早9点', value: '0 0 9 * * MON-FRI' },
  { label: '每周一凌晨3点', value: '0 0 3 * * MON' },
  { label: '每月1号凌晨1点', value: '0 0 1 1 * ?' },
];

const defaultForm = () => ({
  jobCode: '',
  taskId: null,
  taskCode: '',
  taskVersionId: null,
  tasks: [{ taskId: null, taskVersionId: null, environment: null }],
  cronExpression: '',
  environment: 'TEST',
  retryCount: 0,
  retryInterval: 60,
  executorTimeout: 0,
  blockStrategy: 'SKIP',
  maxQueueSize: 5,
  misfireStrategy: 'IGNORE',
  windowStart: null,
  windowEnd: null,
  alarmEmail: ''
});

const form = ref(defaultForm());

const columns = [
  { title: '编码', dataIndex: 'jobCode', key: 'jobCode', width: 130 },
  { title: '关联任务数', key: 'taskCount', width: 90,
    customCell: () => ({ style: { textAlign: 'center' } }),
    customRender: ({ record }) => (record.jobTasks && record.jobTasks.length) ? record.jobTasks.length : (record.taskId ? 1 : 0)
  },
  { title: 'Cron', key: 'cron', slots: { customRender: 'cron' }, width: 160 },
  { title: '环境', dataIndex: 'environment', key: 'environment', width: 60 },
  { title: '状态', key: 'status', slots: { customRender: 'status' }, width: 60 },
  { title: '并发', key: 'blockStrategy', slots: { customRender: 'blockStrategy' }, width: 70 },
  { title: '重试', key: 'retry', slots: { customRender: 'retry' }, width: 70 },
  { title: '上次执行', key: 'lastFire', slots: { customRender: 'lastFire' } },
  { title: '操作', key: 'actions', slots: { customRender: 'actions' }, width: 320 }
];

const ioParamTableColumns = [
  { title: '分类', dataIndex: 'ioType', width: 80 },
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 140 },
  { title: '数据类型', dataIndex: 'dataType', width: 90 },
  { title: '参数来源', dataIndex: 'sourceType', width: 100 },
  { title: '来源值', dataIndex: 'sourceValue', width: 240 },
  { title: '必填', dataIndex: 'requiredFlag', width: 70 }
];


const blockLabel = (s) => ({ SKIP: '跳过', QUEUE: '排队', COVER: '覆盖' }[s] || s || 'SKIP');
const blockColor = (s) => ({ SKIP: 'blue', QUEUE: 'orange', COVER: 'red' }[s] || 'blue');


const fetchJobs = async () => {
  loading.value = true;
  try {
    const res = await scheduleApi.list();
    jobs.value = res.data.data || [];
  } catch (e) {
    message.error('加载失败: ' + e.message);
  } finally {
    loading.value = false;
  }
};

const loadTaskList = async () => {
  taskLoading.value = true;
  try {
    const res = await taskApi.page({ current: 1, size: 200 });
    taskList.value = (res.data?.data?.records) || [];
  } catch (e) {
    taskList.value = [];
  } finally {
    taskLoading.value = false;
  }
};

const filterTaskOption = (input, option) => {
  const label = option.children?.default?.() || option.children || '';
  return String(label).toLowerCase().includes(String(input).toLowerCase());
};

// 每个 taskItem 独立加载版本列表
const taskItemVersionMap = ref({}); // { [taskIndex]: versions[] }

const addTaskItem = () => {
  form.value.tasks.push({ taskId: null, taskVersionId: null, environment: null });
};

const removeTaskItem = (index) => {
  if (form.value.tasks.length <= 1) return;
  form.value.tasks.splice(index, 1);
  // 清理被删除项的版本数据
  const newMap = {};
  Object.keys(taskItemVersionMap.value).forEach(k => {
    const ki = Number(k);
    if (ki < index) newMap[ki] = taskItemVersionMap.value[ki];
    else if (ki > index) newMap[ki - 1] = taskItemVersionMap.value[ki];
  });
  taskItemVersionMap.value = newMap;
  // 调整 active 索引
  if (activeTaskIndex.value >= form.value.tasks.length) {
    activeTaskIndex.value = form.value.tasks.length - 1;
  }
  loadActiveTaskIoParams();
};

const onTaskItemChange = (index, taskId) => {
  const item = form.value.tasks[index];
  item.taskVersionId = null;
  // 加载该任务的版本列表
  loadTaskItemVersions(index, taskId);
};

const resolveTaskEnv = (taskItem) => {
  if (taskItem.environment) return taskItem.environment;
  return form.value.environment || 'TEST';
};

const loadTaskItemVersions = async (index, taskId) => {
  const taskItem = form.value.tasks[index];
  if (!taskItem || !taskId) return;
  const env = resolveTaskEnv(taskItem);
  if (!env) return;
  try {
    const res = await taskApi.getTaskVersions(taskId, { environment: env });
    taskItemVersionMap.value[index] = res.data?.data || [];
  } catch (e) {
    taskItemVersionMap.value[index] = [];
  }
};

const onTaskEnvChange = (index, env) => {
  const taskItem = form.value.tasks[index];
  taskItem.environment = env || null;
  taskItem.taskVersionId = null;
  if (taskItem.taskId) {
    loadTaskItemVersions(index, taskItem.taskId);
  }
  // 如果是当前激活的任务，刷新右侧面板
  if (index === activeTaskIndex.value) {
    ioParamsList.value = [];
    currentDslContent.value = null;
    paramsConfig.value = {};
  }
};

const getVersionsForTaskItem = (taskItem) => {
  const idx = form.value.tasks.indexOf(taskItem);
  if (idx < 0) return [];
  return taskItemVersionMap.value[idx] || [];
};

const switchActiveTask = (index) => {
  if (activeTaskIndex.value === index) return;
  // 保存当前参数到旧的 active taskItem
  syncParamsToActiveTask();
  activeTaskIndex.value = index;
  // 加载新 task 的 DSL 和参数
  loadActiveTaskIoParams();
};

const getActiveTaskName = () => {
  const t = activeTask.value;
  if (!t || !t.taskId) return '未选择';
  const found = taskList.value.find(item => item.id === t.taskId);
  return found ? `${found.taskName} (${found.taskCode})` : `任务#${t.taskId}`;
};

const onTaskVersionChange = (index, versionId) => {
  if (index === activeTaskIndex.value) {
    loadActiveTaskIoParams();
  }
};

const onParamValueChange = (record, value) => {
  paramsConfig.value[record.id] = value;
  // 实时同步到 active taskItem
  syncParamsToActiveTask();
};

// 将当前 paramsConfig 序列化回 active taskItem.paramsConfig
const syncParamsToActiveTask = () => {
  const task = activeTask.value;
  if (!task) return;
  const filledParams = ioParamsList.value
    .filter(p => {
      const val = paramsConfig.value[p.id];
      return val !== undefined && val !== null && val !== '';
    })
    .map(p => ({
      nodeId: p.nodeId,
      nodeName: p.nodeName,
      ioType: p.ioType,
      paramCode: p.paramCode,
      paramName: p.paramName,
      dataType: p.dataType,
      sourceType: p.sourceType,
      originalSourceValue: typeof p.sourceValue === 'object' ? JSON.stringify(p.sourceValue) : String(p.sourceValue || ''),
      sourceValue: paramsConfig.value[p.id]
    }));
  task.paramsConfig = filledParams.length > 0 ? JSON.stringify({ params: filledParams }) : null;
};

// 加载 active task 的 DSL -> IO 参数
const loadActiveTaskIoParams = async () => {
  const task = activeTask.value;
  if (!task || !task.taskVersionId) {
    ioParamsList.value = [];
    currentDslContent.value = null;
    paramsConfig.value = {};
    selectedFlowNode.value = null;
    return;
  }
  paramsLoading.value = true;
  ioParamsList.value = [];
  const idx = activeTaskIndex.value;
  const versions = taskItemVersionMap.value[idx] || [];
  const selectedVersion = versions.find(v => v.id === task.taskVersionId);
  if (!selectedVersion || !selectedVersion.dslContent) {
    paramsLoading.value = false;
    return;
  }
  try {
    const dsl = typeof selectedVersion.dslContent === 'string'
      ? JSON.parse(selectedVersion.dslContent)
      : selectedVersion.dslContent;
    currentDslContent.value = dsl;
    const nodes = dsl.nodes || [];
    const componentIds = [...new Set(nodes.map(n => n.componentId).filter(Boolean))];
    const metaMap = new Map();
    await Promise.all(componentIds.map(async (cid) => {
      try {
        const res = await componentApi.getMeta(cid);
        metaMap.set(cid, res.data?.data || null);
      } catch (e) { metaMap.set(cid, null); }
    }));
    const rows = [];
    nodes.forEach(node => {
      if (isStartOrEndByNode(node)) {
        const inputs = Array.isArray(node.inputParams) ? node.inputParams : [];
        node.outputParams = inputs.map(item => ({
          paramCode: item.paramCode, paramName: item.paramName,
          dataType: item.dataType, requiredFlag: item.requiredFlag || 0,
          sourceType: item.sourceType || 'CONST', sourceValue: item.sourceValue ?? '',
          defaultValue: typeof item.sourceValue === 'string' ? item.sourceValue : ''
        }));
      }
      const nodeParams = extractNodeIoParams(node);
      const meta = node.componentId ? metaMap.get(node.componentId) : null;
      const metaParams = [
        ...(meta?.inputParams || []).map(p => ({ ...p, ioType: 'INPUT' })),
        ...(meta?.outputParams || []).map(p => ({ ...p, ioType: 'OUTPUT' }))
      ];
      const byKey = new Map();
      [...metaParams, ...nodeParams].forEach((p, idx2) => {
        const t2 = String(p.ioType || p.paramType || 'INPUT').toUpperCase();
        const k = `${t2}::${p.paramCode || p.paramName || p.name || idx2}`;
        byKey.set(k, { ...(byKey.get(k) || {}), ...p, ioType: t2 });
      });
      Array.from(byKey.values()).forEach((param, idx2) => {
        const upperType = String(param.ioType || 'INPUT').toUpperCase();
        const key = param.paramCode || param.name || `${upperType === 'OUTPUT' ? 'output' : 'param'}_${idx2 + 1}`;
        rows.push({
          id: `${node.id}_${upperType}_${key}_${idx2}`,
          ioType: upperType, paramCode: key,
          paramName: param.paramName || param.name || key,
          description: param.description || '',
          dataType: param.dataType || param.type || 'STRING',
          sourceType: param.sourceType || 'CONST',
          sourceValue: ensureString(param.sourceValue) ?? ensureString(param.defaultValue) ?? '',
          requiredFlag: Number(param.requiredFlag || 0),
          nodeId: node.id, nodeName: node.name || node.id,
          nodeType: node.type || '', componentCode: node.componentCode || ''
        });
      });
    });
    ioParamsList.value = rows;

    // 恢复该 task 已保存的参数
    if (task.paramsConfig) {
      try {
        const saved = typeof task.paramsConfig === 'string'
          ? JSON.parse(task.paramsConfig) : task.paramsConfig;
        const conf = saved?.params || saved || {};
        if (Array.isArray(conf)) {
          paramsConfig.value = {};
          conf.forEach(p => {
            const match = rows.find(r =>
              r.nodeId === p.nodeId && r.ioType === p.ioType && r.paramCode === p.paramCode
            );
            if (match) paramsConfig.value[match.id] = p.sourceValue;
          });
        }
      } catch (e) { paramsConfig.value = {}; }
    } else {
      paramsConfig.value = {};
    }
  } catch (e) {
    ioParamsList.value = [];
    console.warn('加载IO参数失败:', e);
  } finally {
    paramsLoading.value = false;
  }
};

const onTaskChange = (taskId) => {
  const t = taskList.value.find(item => item.id === taskId);
  form.value.taskCode = t?.taskCode || '';
  form.value.taskVersionId = null;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
  currentDslContent.value = null;
  selectedFlowNode.value = null;
  if (form.value.environment) loadVersionList();
};

const onEnvironmentChange = () => {
  form.value.taskVersionId = null;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
  currentDslContent.value = null;
  selectedFlowNode.value = null;
  taskItemVersionMap.value = {};
  // 重新加载所有 taskItem 的版本
  form.value.tasks.forEach((item, i) => {
    if (item.taskId) loadTaskItemVersions(i, item.taskId);
  });
  if (form.value.taskId) loadVersionList();
};

const loadVersionList = async () => {
  if (!form.value.taskId || !form.value.environment) return;
  versionLoading.value = true;
  try {
    const res = await taskApi.getTaskVersions(form.value.taskId, { environment: form.value.environment });
    versionList.value = res.data?.data || [];
  } catch (e) {
    versionList.value = [];
  } finally {
    versionLoading.value = false;
  }
};

const extractNodeIoParams = (node) => {
  const inputParams = Array.isArray(node?.inputParams)
    ? node.inputParams.map(p => ({ ...p, ioType: 'INPUT' }))
    : [];
  const outputParams = Array.isArray(node?.outputParams)
    ? node.outputParams.map(p => ({ ...p, ioType: 'OUTPUT' }))
    : [];
  if (inputParams.length > 0 || outputParams.length > 0) {
    return [...inputParams, ...outputParams];
  }
  return [];
};

const isStartOrEndByNode = (node) => {
  if (!node) return false;
  return node.type === 'START' || node.type === 'END' ||
    String(node.componentCode || '').toUpperCase().includes('START') ||
    String(node.componentCode || '').toUpperCase().includes('END');
};

const loadIoParams = async () => {
  if (!form.value.taskVersionId) return;
  paramsLoading.value = true;
  ioParamsList.value = [];
  try {
    // Find the selected version from versionList
    const selectedVersion = versionList.value.find(v => v.id === form.value.taskVersionId);
    if (!selectedVersion || !selectedVersion.dslContent) {
      paramsLoading.value = false;
      return;
    }

    // Parse DSL
    const dsl = typeof selectedVersion.dslContent === 'string'
      ? JSON.parse(selectedVersion.dslContent)
      : selectedVersion.dslContent;
    currentDslContent.value = dsl;
    const nodes = dsl.nodes || [];

    // Fetch component metadata for richer param info
    const componentIds = [...new Set(nodes.map(n => n.componentId).filter(Boolean))];
    const metaMap = new Map();
    await Promise.all(componentIds.map(async (cid) => {
      try {
        const res = await componentApi.getMeta(cid);
        metaMap.set(cid, res.data?.data || null);
      } catch (e) {
        metaMap.set(cid, null);
      }
    }));

    // Process nodes - extract all IO params
    const rows = [];
    nodes.forEach(node => {
      // START/END: sync output params from input params
      if (isStartOrEndByNode(node)) {
        const inputs = Array.isArray(node.inputParams) ? node.inputParams : [];
        node.outputParams = inputs.map(item => ({
          paramCode: item.paramCode,
          paramName: item.paramName,
          dataType: item.dataType,
          requiredFlag: item.requiredFlag || 0,
          sourceType: item.sourceType || 'CONST',
          sourceValue: item.sourceValue ?? '',
          defaultValue: typeof item.sourceValue === 'string' ? item.sourceValue : ''
        }));
      }

      const nodeParams = extractNodeIoParams(node);
      const meta = node.componentId ? metaMap.get(node.componentId) : null;
      const metaParams = [
        ...(meta?.inputParams || []).map(p => ({ ...p, ioType: 'INPUT' })),
        ...(meta?.outputParams || []).map(p => ({ ...p, ioType: 'OUTPUT' }))
      ];

      // Merge: meta params first, node params override
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
          id: `${node.id}_${upperType}_${key}_${idx}`,
          ioType: upperType,
          paramCode: key,
          paramName: param.paramName || param.name || key,
          description: param.description || '',
          dataType: param.dataType || param.type || 'STRING',
          sourceType: param.sourceType || 'CONST',
          sourceValue: ensureString(param.sourceValue) ?? ensureString(param.defaultValue) ?? '',
          requiredFlag: Number(param.requiredFlag || 0),
          nodeId: node.id,
          nodeName: node.name || node.id,
          nodeType: node.type || '',
          componentCode: node.componentCode || ''
        });
      });
    });

    ioParamsList.value = rows;

    // Restore saved param values when editing
    if (editingId.value && form.value.paramsConfig) {
      try {
        const saved = typeof form.value.paramsConfig === 'string'
          ? JSON.parse(form.value.paramsConfig) : form.value.paramsConfig;
        const conf = saved?.params || saved || {};
        if (Array.isArray(conf)) {
          paramsConfig.value = {};
          conf.forEach(p => {
            const match = rows.find(r =>
              r.nodeId === p.nodeId && r.ioType === p.ioType && r.paramCode === p.paramCode
            );
            if (match) {
              paramsConfig.value[match.id] = p.sourceValue;
            }
          });
        }
      } catch (e) {
        paramsConfig.value = {};
      }
    } else {
      paramsConfig.value = {};
    }
  } catch (e) {
    ioParamsList.value = [];
    console.warn('加载IO参数失败:', e);
  } finally {
    paramsLoading.value = false;
  }
};

const onFlowNodeClick = (node) => {
  selectedFlowNode.value = node;
};

const sourceTypeLabels = { CONST: '常量', UPSTREAM_OUTPUT: '上游输出', EXPRESSION: '表达式' };
const sourceTypeLabel = (t) => sourceTypeLabels[t] || t || '-';

// 确保值为字符串（对象JSON化或null返回）
const ensureString = (val) => {
  if (val === undefined || val === null) return null;
  if (typeof val === 'string') return val;
  if (typeof val === 'object') {
    if (val.nodeId && val.paramCode) return val; // 保持上游引用对象
    return JSON.stringify(val);
  }
  return String(val);
};

// START/END 节点输出参数值联动对应输入参数的编辑值
const getOutputDisplayValue = (record) => {
  if (record.ioType !== 'OUTPUT') return formatSourceDisplay(record.sourceValue);
  if (isStartOrEndByNode({ type: record.nodeType, componentCode: record.componentCode })) {
    const inputMatch = ioParamsList.value.find(
      p => p.ioType === 'INPUT' && p.nodeId === record.nodeId && p.paramCode === record.paramCode
    );
    if (inputMatch && paramsConfig.value[inputMatch.id]) {
      return paramsConfig.value[inputMatch.id];
    }
  }
  return formatSourceDisplay(record.sourceValue);
};

const formatSourceDisplay = (val) => {
  if (val === undefined || val === null || val === '') return '-';
  if (typeof val === 'string') {
    try {
      const parsed = JSON.parse(val);
      if (parsed && typeof parsed === 'object') return JSON.stringify(parsed);
    } catch (_) {}
    return val;
  }
  if (typeof val === 'object') return JSON.stringify(val);
  return String(val);
};

const showCreateModal = () => {
  editingId.value = null;
  form.value = defaultForm();
  activeTaskIndex.value = 0;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
  currentDslContent.value = null;
  selectedFlowNode.value = null;
  taskItemVersionMap.value = {};
  loadTaskList();
  modalVisible.value = true;
};

const editJob = async (record) => {
  editingId.value = record.id;
  form.value = {
    ...defaultForm(),
    ...record,
    taskCode: record.taskCode || ''
  };
  activeTaskIndex.value = 0;
  currentDslContent.value = null;
  selectedFlowNode.value = null;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
  taskItemVersionMap.value = {};

  await loadTaskList();
  if (!form.value.taskCode && form.value.taskId) {
    const t = taskList.value.find(item => item.id === form.value.taskId);
    form.value.taskCode = t?.taskCode || '';
  }

  // 加载关联的任务列表
  try {
    const res = await scheduleApi.getJobTasks(record.id);
    const jobTasks = res.data?.data || [];
    if (jobTasks.length > 0) {
      form.value.tasks = jobTasks.map(jt => ({
        taskId: jt.taskId,
        taskVersionId: jt.taskVersionId,
        environment: jt.environment || null,
        paramsConfig: jt.paramsConfig || null
      }));
      // 并行加载每个任务的版本列表
      await Promise.all(jobTasks.map((jt, i) => loadTaskItemVersions(i, jt.taskId)));
      // 加载第一个 task 的 IO 参数
      await loadActiveTaskIoParams();
    }
  } catch (e) {
    // Fallback: 使用旧 taskId
    if (form.value.taskId) {
      form.value.tasks = [{ taskId: form.value.taskId, taskVersionId: form.value.taskVersionId, environment: null }];
      await loadTaskItemVersions(0, form.value.taskId);
      await loadActiveTaskIoParams();
    }
  }
  modalVisible.value = true;
};

const handleSave = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    // 处理时间选择器返回的 dayjs 对象
    const payload = { ...form.value };
    if (payload.windowStart && typeof payload.windowStart === 'object') {
      payload.windowStart = payload.windowStart.format('HH:mm:ss');
    }
    if (payload.windowEnd && typeof payload.windowEnd === 'object') {
      payload.windowEnd = payload.windowEnd.format('HH:mm:ss');
    }
    // 保存前同步当前 active task 的参数
    syncParamsToActiveTask();

    // 构建多任务关联列表 (jobTasks)，每个 task 已自带 paramsConfig
    if (payload.tasks && payload.tasks.length > 0) {
      payload.jobTasks = payload.tasks.map((t, i) => ({
        taskId: t.taskId,
        taskVersionId: t.taskVersionId,
        environment: t.environment || null,
        sortOrder: i,
        paramsConfig: t.paramsConfig || null
      }));
      // 向后兼容: 第一个 task 回填到 taskId/taskVersionId + job 级 paramsConfig
      payload.taskId = payload.tasks[0].taskId;
      payload.taskVersionId = payload.tasks[0].taskVersionId;
      payload.paramsConfig = payload.tasks[0].paramsConfig || null;
    }
    if (editingId.value) {
      await scheduleApi.update(editingId.value, payload);
    } else {
      await scheduleApi.create(payload);
    }
    modalVisible.value = false;
    message.success('保存成功');
    fetchJobs();
  } catch (e) {
    message.error('保存失败: ' + e.message);
  }
};

const toggleJob = async (record) => {
  try {
    await scheduleApi.toggle(record.id);
    message.success('操作成功');
    fetchJobs();
  } catch (e) {
    message.error('操作失败: ' + e.message);
  }
};

const triggerJob = async (record) => {
  try {
    const res = await scheduleApi.trigger(record.id);
    const result = res.data?.data;
    message.success(`已触发执行 (${result?.executionId || 'OK'})`);
    fetchJobs();
  } catch (e) {
    message.error('触发失败: ' + e.message);
  }
};

const deleteJob = async (record) => {
  try {
    await scheduleApi.remove(record.id);
    message.success('删除成功');
    fetchJobs();
  } catch (e) {
    message.error('删除失败: ' + e.message);
  }
};

const viewLog = (executionId) => {
  router.push({ path: '/execute-log', query: { executionId } });
};

const viewScheduleExecutions = (record) => {
  router.push({
    path: '/execute-log',
    query: {
      scheduleJobId: record.id,
      executionId: record.lastExecutionId
    }
  });
};

const showStats = async (record) => {
  statsVisible.value = true;
  statsLoading.value = true;
  statsSummary.value = null;
  statsError.value = '';
  try {
    const res = await scheduleApi.statsSummary(record.id);
    const data = res.data?.data;
    if (data && typeof data === 'object') {
      statsSummary.value = data;
    } else {
      statsError.value = '返回数据格式异常';
    }
  } catch (e) {
    statsError.value = e.message || '加载失败';
  } finally {
    statsLoading.value = false;
  }
};

onMounted(fetchJobs);
</script>

<style scoped>
.schedule-page { padding: 0; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h3 { margin: 0; }

.modal-content {
  display: flex;
  gap: 20px;
  height: 70vh;
  overflow: hidden;
}
.layout-left {
  flex: 1;
  min-width: 380px;
  overflow-y: auto;
  padding-right: 4px;
}
.layout-right {
  flex: 1;
  min-width: 420px;
  overflow-y: auto;
  padding-right: 4px;
}
.section-card {
  background: #fbfbfb;
  padding: 16px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
  margin-bottom: 12px;
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
  min-height: 200px;
}
.source-value-text {
  color: #999;
}
.flow-viewer-mini {
  height: 280px;
  margin-bottom: 4px;
}
.task-item-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
  padding: 6px 8px;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.task-item-row:hover {
  background: #f0f5ff;
  border-color: #d6e4ff;
}
.task-item-active {
  background: #e6f4ff;
  border-color: #1890ff;
}
</style>
