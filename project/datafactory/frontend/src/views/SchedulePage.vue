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
      <template #lastFire="{ record }">
        <span v-if="record.lastFireTime && record.lastExecutionId">
          <a @click="viewLog(record.lastExecutionId)">{{ record.lastFireTime }}</a>
        </span>
        <span v-else>{{ record.lastFireTime || '未执行' }}</span>
      </template>
      <template #actions="{ record }">
        <a-space>
          <a-button size="small" @click="editJob(record)">编辑</a-button>
          <a-button size="small" @click="toggleJob(record)">
            {{ record.status === 1 ? '停用' : '启用' }}
          </a-button>
          <a-button size="small" @click="triggerJob(record)">手动触发</a-button>
          <a-popconfirm title="确认删除?" @confirm="deleteJob(record)">
            <a-button size="small" danger>删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-table>

    <a-modal v-model:open="modalVisible" :title="editingId ? '编辑定时任务' : '新建定时任务'" @ok="handleSave"
      @cancel="modalVisible = false" width="560px">
      <a-form :model="form" layout="vertical">
        <a-form-item label="定时任务编码" required>
          <a-input v-model:value="form.jobCode" placeholder="唯一编码" />
        </a-form-item>
        <a-form-item label="选择任务" required>
          <a-select v-model:value="form.taskId" placeholder="请选择任务" show-search
            :filter-option="filterTaskOption" @change="onTaskChange" :loading="taskLoading">
            <a-select-option v-for="t in taskList" :key="t.id" :value="t.id">
              {{ t.taskName }} ({{ t.taskCode }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关联任务编码">
          <a-input v-model:value="form.taskCode" disabled />
        </a-form-item>
        <a-form-item label="版本环境" required>
          <a-select v-model:value="form.versionEnv" @change="onVersionEnvChange">
            <a-select-option value="DEV">DEV（开发环境）</a-select-option>
            <a-select-option value="TEST">TEST（测试环境）</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="选择版本" required>
          <a-select v-model:value="form.taskVersionId" placeholder="请先选择任务和环境" :loading="versionLoading"
            :disabled="!form.taskId || !form.versionEnv">
            <a-select-option v-for="v in versionList" :key="v.id" :value="v.id">
              {{ v.version }} - {{ v.changeLog || '无变更说明' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Cron 表达式" required>
          <a-input v-model:value="form.cronExpression" placeholder="0 0 2 * * ?">
            <template #suffix>
              <a-tooltip title="秒 分 时 日 月 周&#10;示例: 0 0 2 * * ? 每天凌晨2点&#10;0 */30 * * * ? 每30分钟">
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
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
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

const cronPresets = [
  { label: '每5分钟', value: '0 */5 * * * ?' },
  { label: '每30分钟', value: '0 */30 * * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每天凌晨2点', value: '0 0 2 * * ?' },
  { label: '每天早上9点', value: '0 0 9 * * ?' },
  { label: '工作日早9点', value: '0 0 9 * * MON-FRI' },
  { label: '每周一凌晨3点', value: '0 0 3 * * MON' },
  { label: '每月1号凌晨1点', value: '0 0 1 1 * ?' },
];

const form = ref({
  jobCode: '',
  taskId: null,
  taskCode: '',
  taskVersionId: null,
  versionEnv: 'TEST',
  cronExpression: '',
  environment: 'TEST'
});

const columns = [
  { title: '编码', dataIndex: 'jobCode', key: 'jobCode', width: 140 },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 80 },
  { title: 'Cron', key: 'cron', slots: { customRender: 'cron' }, width: 180 },
  { title: '环境', dataIndex: 'environment', key: 'environment', width: 70 },
  { title: '状态', key: 'status', slots: { customRender: 'status' }, width: 70 },
  { title: '上次执行', key: 'lastFire', slots: { customRender: 'lastFire' } },
  { title: '操作', key: 'actions', slots: { customRender: 'actions' }, width: 280 }
];

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
  if (form.value.versionEnv) loadVersionList();
};

const onVersionEnvChange = () => {
  form.value.taskVersionId = null;
  versionList.value = [];
  if (form.value.taskId) loadVersionList();
};

const loadVersionList = async () => {
  if (!form.value.taskId || !form.value.versionEnv) return;
  versionLoading.value = true;
  try {
    const res = await taskApi.getTaskVersions(form.value.taskId, { environment: form.value.versionEnv });
    versionList.value = res.data?.data || [];
  } catch (e) {
    versionList.value = [];
  } finally {
    versionLoading.value = false;
  }
};

const showCreateModal = () => {
  editingId.value = null;
  form.value = { jobCode: '', taskId: null, taskCode: '', taskVersionId: null, versionEnv: 'TEST', cronExpression: '', environment: 'TEST' };
  versionList.value = [];
  loadTaskList();
  modalVisible.value = true;
};

const editJob = async (record) => {
  editingId.value = record.id;
  form.value = { ...record, versionEnv: record.environment || 'TEST', taskCode: record.taskCode || '' };
  await loadTaskList();
  // 如果 record 没有存 taskCode，从任务列表中自动匹配
  if (!form.value.taskCode && form.value.taskId) {
    const t = taskList.value.find(item => item.id === form.value.taskId);
    form.value.taskCode = t?.taskCode || '';
  }
  if (form.value.taskId) {
    await loadVersionList();
  }
  modalVisible.value = true;
};

const handleSave = async () => {
  try {
    form.value.environment = form.value.versionEnv;
    if (editingId.value) {
      await scheduleApi.update(editingId.value, form.value);
    } else {
      await scheduleApi.create(form.value);
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
    const executionId = res.data?.data;
    message.success(`已触发执行 (${executionId})`);
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
</style>
