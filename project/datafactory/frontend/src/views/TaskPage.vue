<template>
  <div class="page-wrap">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入任务名称和编码" style="width: 240px; margin-right: 12px" />
      <a-button type="primary" @click="loadTasks">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch" style="margin-left: 8px">重置</a-button>
      <a-button type="primary" @click="openCreate" style="margin-left: 8px">+ 新建任务</a-button>
    </div>

    <a-table :columns="columns" :data-source="rows" :pagination="{ pageSize: 10 }" row-key="id" size="middle"
      :loading="loading" :scroll="{ x: 'max-content' }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag color="success" v-if="String(record.status) === '1'">启用</a-tag>
          <a-tag color="default" v-else>禁用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" @click="openDetail(record)">查看</a-button>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" type="primary" ghost @click="openEnvModal(record, 'dev')">环境</a-button>
            <a-button size="small" @click="toggleStatus(record)">{{ String(record.status) === '1' ? '禁用' : '启用'
              }}</a-button>
            <a-popconfirm title="确定删除该任务吗？" ok-text="确定" cancel-text="取消" @confirm="deleteTask(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="detailVisible" title="任务详情" :width="760" :footer="null" destroyOnClose>
      <a-descriptions bordered :column="1" size="middle">
        <a-descriptions-item label="任务ID">{{ detailRow.id }}</a-descriptions-item>
        <a-descriptions-item label="任务编码">{{ detailRow.taskCode }}</a-descriptions-item>
        <a-descriptions-item label="任务名称">{{ detailRow.taskName }}</a-descriptions-item>
        <a-descriptions-item label="版本">{{ detailRow.version }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ String(detailRow.status) === '1' ? '启用' : '禁用' }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ detailRow.description || '-' }}</a-descriptions-item>
      </a-descriptions>
      <div class="modal-actions"><a-button @click="detailVisible = false">关闭</a-button></div>
    </a-modal>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑任务' : '新建任务'" :width="760" :footer="null" destroyOnClose>
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '130px' } }" class="task-form">
        <a-form-item label="任务编码" required>
          <a-input v-model:value="formState.taskCode" :disabled="isEdit" placeholder="请输入任务编码" />
        </a-form-item>
        <a-form-item label="任务名称" required>
          <a-input v-model:value="formState.taskName" placeholder="请输入任务名称" />
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
        <a-form-item label="描述">
          <a-textarea v-model:value="formState.description" :rows="5" placeholder="请输入任务描述" />
        </a-form-item>
      </a-form>
      <div class="modal-actions">
        <a-button @click="formVisible = false">Cancel</a-button>
        <a-button type="primary" @click="submitForm">OK</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="envVisible" :title="`${envName} - 任务环境管理`" :width="1100" :footer="null" :centered="true"
      :maskClosable="false" :keyboard="false" destroyOnClose wrap-class-name="env-modal-fixed">
      <a-tabs v-model:activeKey="activeEnvTab" @change="loadVersions">
        <a-tab-pane key="dev" tab="开发环境" />
        <a-tab-pane key="test" tab="测试环境" />
        <a-tab-pane key="prod" tab="生产环境" />
      </a-tabs>
      <a-alert class="env-alert" type="info" show-icon :message="envTips.task[activeEnvTab]" />
      <div class="toolbar env-toolbar">
        <a-button v-if="activeEnvTab === 'dev'" type="primary" @click="openDevEditorForNew">+ 新建版本</a-button>
        <a-button v-if="activeEnvTab === 'dev'" type="primary" @click="promoteSelected('TEST')">发布选中版本到TEST</a-button>
        <a-button v-if="activeEnvTab === 'test'" type="primary" @click="promoteSelected('PROD')">发布选中版本到PROD</a-button>
      </div>
      <div class="env-table-wrap">
        <a-table :columns="dynamicEnvColumns" :data-source="envRows" :pagination="false" row-key="id" size="middle"
          :scroll="{ x: 980, y: 360 }" table-layout="fixed">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'pick'">
              <a-radio v-if="activeEnvTab !== 'prod'" :checked="selectedVersionId === record.id"
                @change="() => selectedVersionId = record.id" />
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="record.publishStatus === 1 ? 'success' : 'default'">{{ record.publishStatus === 1 ? '已发布' :
                '未发布' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'op'">
              <a-space :size="6" wrap>
                <a-button size="small" @click="viewVersion(record)">查看</a-button>
                <a-button v-if="activeEnvTab === 'dev'" size="small" @click="editVersion(record)">编辑</a-button>
                <a-button v-if="activeEnvTab === 'test'" size="small" type="primary" ghost
                  @click="testVersion(record)">测试</a-button>
                <a-button v-if="activeEnvTab === 'prod'" size="small" type="primary" :ghost="record.isCurrent !== 1"
                  :disabled="record.isCurrent === 1" @click="selectCurrent(record)">
                  {{ record.isCurrent === 1 ? '已选中' : '选中' }}
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 将发布确认弹窗移动到环境管理弹窗内部，确保其作为子弹窗显示，不遮挡或关闭父弹窗 -->
      <PromoteModal :open="promoteModalVisible" :versionData="promoteVersionData" :confirmLoading="promoteLoading"
        @ok="handlePromoteConfirm" @cancel="promoteModalVisible = false" />
    </a-modal>

    <TaskDevVersionEditor :open="devEditorVisible" :initialDsl="editingVersionDsl" :readonly="isReadonly"
      @close="closeDevEditor" @save="saveDevVersion" />

    <TaskTestModal :open="testModalVisible" :taskId="testTaskId" :version="testVersionName" :versionId="testVersionId" :dslContent="testDslContent"
      @close="closeTestModal" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from 'vue-router';
import TaskDevVersionEditor from '../components/TaskDevVersionEditor.vue';
import TaskTestModal from '../components/TaskTestModal.vue';
import PromoteModal from '../components/PromoteModal.vue';
import { taskApi, taskDslApi } from '../api/task';

const keyword = ref('');
const loading = ref(false);
const rows = ref([]);
const currentTaskId = ref(null);
const envRows = ref([]);
const selectedVersionId = ref(null);
const formVisible = ref(false);
const formRef = ref();
const formRules = {
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }]
};
const detailVisible = ref(false);
const envVisible = ref(false);
const isEdit = ref(false);
const editingId = ref(null);
const envName = ref('');
const activeEnvTab = ref('dev');
const devEditorVisible = ref(false);
const editingVersionDsl = ref(null);
const isReadonly = ref(false);
const testModalVisible = ref(false);
const testTaskId = ref(null);
const testVersionName = ref('');
const testVersionId = ref(null);
const testDslContent = ref('');
const promoteModalVisible = ref(false);
const promoteVersionData = ref(null);
const promoteLoading = ref(false);
const promoteTargetEnv = ref('');
const router = useRouter();

const detailRow = reactive({ id: '', taskCode: '', taskName: '', version: '', status: 1, description: '' });
const formState = reactive({ taskCode: '', taskName: '', version: '1.0.0', status: 1, description: '' });

const columns = [
  { title: '任务ID', dataIndex: 'id', width: 80 },
  { title: '任务编码', dataIndex: 'taskCode' },
  { title: '任务名称', dataIndex: 'taskName' },
  { title: '版本', dataIndex: 'version', width: 90 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '描述', dataIndex: 'description' },
  { title: '创建时间', dataIndex: 'createdTime', width: 190 },
  { title: '操作', dataIndex: 'op', width: 340 }
];

const envColumns = [
  { title: '', dataIndex: 'pick', width: 56 },
  { title: '版本', dataIndex: 'version', width: 90 },
  { title: '发布状态', dataIndex: 'status', width: 110 },
  { title: '变更说明', dataIndex: 'changeLog', width: 180 },
  { title: '创建人', dataIndex: 'createdBy', width: 100 },
  { title: '创建时间', dataIndex: 'createdTime', width: 180 },
  { title: '操作', dataIndex: 'op', width: 120 }
];

const dynamicEnvColumns = computed(() => {
  return envColumns.filter(col => {
    if (activeEnvTab.value === 'prod' && col.dataIndex === 'status') {
      return false;
    }
    return true;
  });
});
const envTips = { task: { dev: '开发环境用于编排任务节点与依赖。', test: '测试环境用于任务联调、补数验证。', prod: '生产环境变更会影响调度，请谨慎发布。' } };
const toEnvCode = (tab) => (tab || 'dev').toUpperCase();
const toStatusNumber = (status) => (String(status) === '1' || status === 1 || status === '启用' ? 1 : 0);
const normalizeTaskRow = (item = {}) => ({
  ...item,
  taskCode: item.taskCode ?? item.code ?? '',
  taskName: item.taskName ?? item.name ?? '',
  version: item.version ?? '1.0.0',
  status: toStatusNumber(item.status),
  description: item.description ?? item.desc ?? '',
  createdTime: item.createdTime ?? item.createdAt ?? ''
});

const loadTasks = async () => {
  if (loading.value) return;
  loading.value = true;
  console.log('TaskPage: Requesting tasks...');
  try {
    const res = await taskApi.page({ current: 1, size: 100, keyword: keyword.value || undefined });
    console.log('TaskPage: Full Axios Response:', res);

    // 深度寻找数组
    let dataArray = [];
    const body = res.data;

    if (body) {
      if (Array.isArray(body)) {
        dataArray = body;
      } else if (body.code === 0 && body.data) {
        // Result<T> 结构
        if (Array.isArray(body.data)) {
          dataArray = body.data;
        } else if (body.data.records && Array.isArray(body.data.records)) {
          // Result<PageResult<T>> 结构
          dataArray = body.data.records;
        }
      } else if (body.records && Array.isArray(body.records)) {
        // 直接返回 PageResult 结构
        dataArray = body.records;
      }
    }

    rows.value = dataArray.map(normalizeTaskRow);
    console.log('TaskPage: Final rows set to:', rows.value);

    if (rows.value.length === 0) {
      console.warn('TaskPage: No data found in response. Please check if database has records');
    }
  } catch (e) {
    console.error('TaskPage: Load tasks failed', e);
    rows.value = [];
    message.error(`列表加载失败：${e.message}`);
  } finally {
    loading.value = false;
  }
};

const resetSearch = async () => { keyword.value = ''; await loadTasks(); };

const openCreate = () => {
  isEdit.value = false;
  editingId.value = null;
  formState.taskCode = '';
  formState.taskName = '';
  formState.version = '1.0.0';
  formState.status = 1;
  formState.description = '';
  formVisible.value = true;
};

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(formState, {
    taskCode: row.taskCode,
    taskName: row.taskName,
    version: row.version || '1.0.0',
    status: toStatusNumber(row.status),
    description: row.description || ''
  });
  formVisible.value = true;
};

const openDetail = async (row) => {
  const res = await taskApi.detail(row.id);
  Object.assign(detailRow, normalizeTaskRow(res.data?.data || row));
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
      await taskApi.update({ id: editingId.value, ...formState });
      message.success('编辑成功');
    } else {
      await taskApi.create(formState);
      message.success('新建成功');
      keyword.value = '';
    }
    formVisible.value = false;
    await loadTasks();
  } catch (e) {
    message.error(`保存失败：${e.message}`);
  }
};

const deleteTask = async (id) => {
  try { await taskApi.remove(id); message.success('删除成功'); } catch (e) { message.error(`删除失败：${e.message}`); }
  await loadTasks();
};

const toggleStatus = async (record) => {
  try {
    const nextStatus = toStatusNumber(record.status) === 1 ? 0 : 1;
    await taskApi.changeStatus(record.id, nextStatus);
    message.success(nextStatus === 1 ? '启用成功' : '禁用成功');
    await loadTasks();
  } catch (e) {
    message.error(`状态更新失败：${e.message}`);
  }
};

const openEnvModal = async (record, env = 'dev') => {
  currentTaskId.value = record.id;
  envName.value = record.taskName || record.taskCode;
  activeEnvTab.value = env;
  selectedVersionId.value = null;
  envVisible.value = true;
  await loadVersions();
};

const loadVersions = async () => {
  if (!currentTaskId.value) return;
  const res = await taskDslApi.list(currentTaskId.value, toEnvCode(activeEnvTab.value));
  envRows.value = res.data?.data || res.data?.records || [];
  selectedVersionId.value = null;
};

const bumpVersion = (v = '1.0.0') => {
  const arr = String(v || '1.0.0').split('.').map((x) => Number(x) || 0);
  while (arr.length < 3) arr.push(0);
  arr[2] += 1;
  return `${arr[0]}.${arr[1]}.${arr[2]}`;
};

const openDevEditorForNew = () => {
  editingVersionDsl.value = null;
  isReadonly.value = false;
  devEditorVisible.value = true;
};

const closeDevEditor = () => {
  devEditorVisible.value = false;
  editingVersionDsl.value = null;
  isReadonly.value = false;
};

const saveDevVersion = async (dslPayload) => {
  const latest = envRows.value[0]?.version || '0.9.9';
  const nextVersion = envRows.value.length ? bumpVersion(latest) : '1.0.0';
  const normalizedDsl = {
    schemaVersion: dslPayload?.schemaVersion || 3,
    graph: {
      nodes: Array.isArray(dslPayload?.nodes) ? dslPayload.nodes : [],
      edges: Array.isArray(dslPayload?.edges) ? dslPayload.edges : [],
      viewport: dslPayload?.viewport || { scale: 1, pan: { x: 0, y: 0 } }
    },
    nodes: Array.isArray(dslPayload?.nodes) ? dslPayload.nodes : [],
    edges: Array.isArray(dslPayload?.edges) ? dslPayload.edges : [],
    viewport: dslPayload?.viewport || { scale: 1, pan: { x: 0, y: 0 } },
    version: nextVersion
  };
  try {
    const res = await taskDslApi.createVersion({
      taskId: currentTaskId.value,
      environment: 'DEV',
      version: nextVersion,
      dslContent: JSON.stringify(normalizedDsl),
      changeLog: `画布保存版本 ${nextVersion}`
    });
    const versionId = res.data?.data;
    if (versionId) {
      await taskApi.rebuildComponentSnapshots(versionId).catch(() => {});
    }
    message.success(`版本 ${nextVersion} 保存成功`);
    devEditorVisible.value = false;
    await loadVersions();
  } catch (e) {
    message.error(`保存失败：${e.message || '未知错误'}`);
  }
};

const promoteSelected = (targetEnv) => {
  if (!selectedVersionId.value) {
    message.warning('请先勾选要发布的版本');
    return;
  }
  if (!currentTaskId.value) {
    message.warning('未识别任务ID');
    return;
  }

  const selected = envRows.value.find((x) => x.id === selectedVersionId.value);
  if (!selected) {
    message.warning('未找到选中的版本');
    return;
  }

  promoteTargetEnv.value = targetEnv;
  promoteVersionData.value = {
    ...selected,
    creator: selected.createdBy || selected.creator || '-',
    createdAt: selected.createdTime || selected.createdAt || '-'
  };
  promoteModalVisible.value = true;
};

const handlePromoteConfirm = async (publishDescription) => {
  const fromEnv = toEnvCode(activeEnvTab.value);
  const toEnv = String(promoteTargetEnv.value || '').toUpperCase();
  const selected = promoteVersionData.value;

  promoteLoading.value = true;
  try {
    // 先确保来源版本是已发布版本（后端晋升要求）
    if (selected.publishStatus !== 1) {
      await taskDslApi.publish(selected.id);
    }

    await taskDslApi.promote(currentTaskId.value, {
      sourceVersionId: selected.id,
      fromEnvironment: fromEnv,
      toEnvironment: toEnv,
      changeLog: publishDescription || `由${fromEnv}环境版本${selected.version}发布到${toEnv}`
    });

    message.success(`版本 ${selected.version} 已发布到 ${toEnv}`);
    promoteModalVisible.value = false;

    // 自动切换到目标环境查看结果
    activeEnvTab.value = toEnv.toLowerCase();
    await loadVersions();
  } catch (e) {
    message.error(`发布失败：${e.message}`);
  } finally {
    promoteLoading.value = false;
  }
};

const selectCurrent = async (record) => {
  try {
    await taskDslApi.setCurrentVersion(record.id);
    message.success('已设为当前执行版本');
    await loadVersions();
  } catch (e) {
    message.error(`设置失败：${e.message}`);
  }
};

const editVersion = (record) => {
  if (activeEnvTab.value !== 'dev') {
    message.info('仅开发环境支持可视化编辑');
    return;
  }
  editingVersionDsl.value = record?.dslContent || record?.dslJson || null;
  isReadonly.value = false;
  devEditorVisible.value = true;
};

const viewVersion = (record) => {
  editingVersionDsl.value = record?.dslContent || record?.dslJson || null;
  isReadonly.value = true;
  devEditorVisible.value = true;
};

const testVersion = (record) => {
  const parentTask = rows.value.find(t => t.id === currentTaskId.value);
  if (parentTask && String(parentTask.status) !== '1') {
    message.warning('该任务已被禁用，无法进行版本测试');
    return;
  }
  testTaskId.value = record.taskId;
  testVersionName.value = record.version;
  testVersionId.value = record.id;
  testDslContent.value = record.dslContent || record.dslJson || '';
  testModalVisible.value = true;
};

const closeTestModal = () => {
  testModalVisible.value = false;
  testTaskId.value = null;
  testVersionName.value = '';
  testVersionId.value = null;
  testDslContent.value = '';
};

onMounted(loadTasks);
</script>

<style scoped>
.env-alert {
  margin-bottom: 10px;
}

.task-form {
  padding-top: 8px;
}

.modal-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>