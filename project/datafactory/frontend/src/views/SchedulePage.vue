<template>
  <div class="schedule-page">
    <div class="page-header">
      <h3>定时任务管理</h3>
      <a-button type="primary" @click="showCreateModal">新建定时任务</a-button>
    </div>

    <a-table :columns="columns" :data-source="jobs" :loading="loading" row-key="id" size="middle"
      :expandable="{ expandedRowRender }">
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
        <span v-if="record.lastFireTime && record.lastExecutionId">
          <a @click="viewLog(record.lastExecutionId)">{{ record.lastFireTime }}</a>
        </span>
        <span v-else>{{ record.lastFireTime || '未执行' }}</span>
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
      <a-form :model="form" layout="vertical">
        <div class="modal-content">
          <!-- 左侧：表单 -->
          <div class="layout-left">
            <div class="section-card">
              <div class="section-title">基本信息</div>

              <a-row :gutter="16">
                <a-col :span="8">
                  <a-form-item label="定时任务编码" required>
                    <a-input v-model:value="form.jobCode" placeholder="唯一编码" :disabled="!!editingId" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="环境" required>
                    <a-select v-model:value="form.environment" @change="onEnvironmentChange">
                      <a-select-option value="TEST">TEST（测试环境）</a-select-option>
                      <a-select-option value="PROD">PROD（生产环境）</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="选择任务" required>
                    <a-select v-model:value="form.taskId" placeholder="请选择任务" show-search
                      :filter-option="filterTaskOption" @change="onTaskChange" :loading="taskLoading">
                      <a-select-option v-for="t in taskList" :key="t.id" :value="t.id">
                        {{ t.taskName }} ({{ t.taskCode }})
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="选择版本" required>
                <a-select v-model:value="form.taskVersionId" placeholder="请先选择任务和环境" :loading="versionLoading"
                  :disabled="!form.taskId || !form.environment" @change="loadIoParams">
                  <a-select-option v-for="v in versionList" :key="v.id" :value="v.id">
                    {{ v.version }} - {{ v.changeLog || '无变更说明' }}
                  </a-select-option>
                </a-select>
              </a-form-item>

              <a-form-item label="Cron 表达式" required>
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

          <!-- 右侧：参数配置 -->
          <div class="layout-right">
            <div class="section-card" v-if="form.taskVersionId">
              <div class="section-title">参数配置</div>
              <a-spin :spinning="paramsLoading">
                <template v-if="groupedIoParams.length === 0 && !paramsLoading">
                  <a-empty description="该版本暂无参数配置" />
                </template>
                <a-collapse v-else accordion>
                  <a-collapse-panel
                    v-for="group in groupedIoParams"
                    :key="group.nodeId"
                    :header="`节点: ${group.nodeName} (${group.nodeId})`"
                  >
                    <template v-if="group.inputParams.length > 0">
                      <div style="font-weight:600;margin:8px 0 4px;color:#1677ff;">输入参数</div>
                      <a-table
                        :columns="ioParamTableColumns"
                        :data-source="group.inputParams"
                        :pagination="false"
                        row-key="paramCode"
                        size="small"
                        :scroll="{ x: 'max-content' }"
                      >
                        <template #bodyCell="{ column, record }">
                          <template v-if="column.dataIndex === 'paramValue'">
                            <a-input
                              v-model:value="paramsConfig[record.nodeId + '|' + record.ioType + '|' + record.paramCode]"
                              :placeholder="'请输入' + (record.paramName || record.paramCode)"
                              size="small"
                            />
                          </template>
                        </template>
                      </a-table>
                    </template>
                    <template v-if="group.outputParams.length > 0">
                      <div style="font-weight:600;margin:12px 0 4px;color:#52c41a;">输出参数</div>
                      <a-table
                        :columns="ioParamTableColumns"
                        :data-source="group.outputParams"
                        :pagination="false"
                        row-key="paramCode"
                        size="small"
                        :scroll="{ x: 'max-content' }"
                      >
                        <template #bodyCell="{ column, record }">
                          <template v-if="column.dataIndex === 'paramValue'">
                            <a-input
                              v-model:value="paramsConfig[record.nodeId + '|' + record.ioType + '|' + record.paramCode]"
                              :placeholder="'请输入' + (record.paramName || record.paramCode)"
                              size="small"
                            />
                          </template>
                        </template>
                      </a-table>
                    </template>
                  </a-collapse-panel>
                </a-collapse>
              </a-spin>
            </div>
            <div class="empty-placeholder" v-else>
              <a-empty description="请先选择任务版本以配置参数" />
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
import { h, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { scheduleApi } from '../api/scheduleApi';
import { taskApi } from '../api/task';

const router = useRouter();

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
const paramsLoading = ref(false);
const paramsConfig = ref({});

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
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 70 },
  { title: 'Cron', key: 'cron', slots: { customRender: 'cron' }, width: 160 },
  { title: '环境', dataIndex: 'environment', key: 'environment', width: 60 },
  { title: '状态', key: 'status', slots: { customRender: 'status' }, width: 60 },
  { title: '并发', key: 'blockStrategy', slots: { customRender: 'blockStrategy' }, width: 70 },
  { title: '重试', key: 'retry', slots: { customRender: 'retry' }, width: 70 },
  { title: '上次执行', key: 'lastFire', slots: { customRender: 'lastFire' } },
  { title: '操作', key: 'actions', slots: { customRender: 'actions' }, width: 320 }
];

const ioParamTableColumns = [
  { title: '参数编码', dataIndex: 'paramCode', width: 160 },
  { title: '参数名称', dataIndex: 'paramName', width: 140 },
  { title: '数据类型', dataIndex: 'dataType', width: 100 },
  { title: '参数值', dataIndex: 'paramValue', width: 240 }
];

// 展开行显示详细信息
const expandedRowRender = (record) => {
  if (!record) return h('span', '-');
  try {
    return h('div', { style: 'padding: 8px 16px; display:flex; gap:24px; flex-wrap:wrap; font-size:13px; color:#666;' }, [
      h('span', `超时阈值: ${record.executorTimeout || 0}s`),
      h('span', `错过策略: ${record.misfireStrategy || 'IGNORE'}`),
      h('span', `告警邮箱: ${record.alarmEmail || '未配置'}`),
      h('span', `父调度ID: ${record.parentJobId || '无'}`),
      h('span', `下次触发: ${record.nextFireTime || '-'}`)
    ]);
  } catch (e) {
    return h('span', '-');
  }
};

const blockLabel = (s) => ({ SKIP: '跳过', QUEUE: '排队', COVER: '覆盖' }[s] || s || 'SKIP');
const blockColor = (s) => ({ SKIP: 'blue', QUEUE: 'orange', COVER: 'red' }[s] || 'blue');

const groupedIoParams = computed(() => {
  const groups = {};
  ioParamsList.value.forEach(param => {
    const key = param.nodeId;
    if (!groups[key]) {
      groups[key] = { nodeId: key, nodeName: param.nodeName, inputParams: [], outputParams: [] };
    }
    if (param.ioType === 'INPUT') {
      groups[key].inputParams.push(param);
    } else {
      groups[key].outputParams.push(param);
    }
  });
  return Object.values(groups);
});

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

const onTaskChange = (taskId) => {
  const t = taskList.value.find(item => item.id === taskId);
  form.value.taskCode = t?.taskCode || '';
  form.value.taskVersionId = null;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
  if (form.value.environment) loadVersionList();
};

const onEnvironmentChange = () => {
  form.value.taskVersionId = null;
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
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

const loadIoParams = async () => {
  if (!form.value.taskVersionId) return;
  paramsLoading.value = true;
  ioParamsList.value = [];
  try {
    const res = await scheduleApi.fetchVersionIoParams(form.value.taskVersionId);
    const allParams = res.data?.data || [];
    ioParamsList.value = allParams;
    if (editingId.value && form.value.paramsConfig) {
      try {
        const saved = typeof form.value.paramsConfig === 'string'
          ? JSON.parse(form.value.paramsConfig) : form.value.paramsConfig;
        const conf = saved?.params || saved?.paramsConfig || saved;
        if (Array.isArray(conf)) {
          conf.forEach(p => {
            if (p.paramValue) {
              paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode] = p.paramValue;
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

const showCreateModal = () => {
  editingId.value = null;
  form.value = defaultForm();
  versionList.value = [];
  ioParamsList.value = [];
  paramsConfig.value = {};
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
  await loadTaskList();
  if (!form.value.taskCode && form.value.taskId) {
    const t = taskList.value.find(item => item.id === form.value.taskId);
    form.value.taskCode = t?.taskCode || '';
  }
  if (form.value.taskId) {
    await loadVersionList();
    await loadIoParams();
  }
  modalVisible.value = true;
};

const handleSave = async () => {
  try {
    // 处理时间选择器返回的 dayjs 对象
    const payload = { ...form.value };
    if (payload.windowStart && typeof payload.windowStart === 'object') {
      payload.windowStart = payload.windowStart.format('HH:mm:ss');
    }
    if (payload.windowEnd && typeof payload.windowEnd === 'object') {
      payload.windowEnd = payload.windowEnd.format('HH:mm:ss');
    }
    // 序列化参数配置
    const filledParams = ioParamsList.value
      .filter(p => {
        const key = p.nodeId + '|' + p.ioType + '|' + p.paramCode;
        const val = paramsConfig.value[key];
        return val !== undefined && val !== null && val !== '';
      })
      .map(p => ({
        nodeId: p.nodeId,
        nodeName: p.nodeName,
        ioType: p.ioType,
        paramCode: p.paramCode,
        paramName: p.paramName,
        dataType: p.dataType,
        paramValue: paramsConfig.value[p.nodeId + '|' + p.ioType + '|' + p.paramCode]
      }));
    payload.paramsConfig = filledParams.length > 0 ? JSON.stringify({ params: filledParams }) : null;
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
</style>
