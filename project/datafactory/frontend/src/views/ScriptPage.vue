<template>
  <div class="page-wrap">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入脚本名称和编码" />
      <a-button type="primary" @click="loadData">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建脚本</a-button>
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
            <a-button size="small" type="primary" ghost @click="openEnvModal(record, 'dev')">环境</a-button>
            <a-button size="small" @click="toggleStatus(record)">
              {{ record.status === '启用' ? '禁用' : '启用' }}
            </a-button>
            <a-popconfirm title="确定删除该脚本吗？" ok-text="确定" cancel-text="取消" @confirm="deleteScript(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑脚本' : '新建脚本'" :width="760" :footer="null" destroyOnClose>
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '130px' } }" class="script-form">
        <a-form-item label="脚本编码" required>
          <a-input v-model:value="formState.code" :disabled="isEdit" placeholder="例如：SCRIPT_PYTHON_001" />
        </a-form-item>

        <a-form-item label="脚本名称" required>
          <a-input v-model:value="formState.name" placeholder="例如：数据清洗脚本" />
        </a-form-item>

        <a-form-item label="脚本类型" required>
          <a-select v-model:value="formState.type" :options="scriptTypeOptions" placeholder="请选择脚本类型" />
        </a-form-item>

        <a-form-item label="状态">
          <a-radio-group v-model:value="formState.status">
            <a-radio value="启用">启用</a-radio>
            <a-radio value="禁用">禁用</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="描述">
          <a-textarea v-model:value="formState.desc" :rows="5" placeholder="请输入描述" />
        </a-form-item>
      </a-form>

      <div class="modal-actions">
        <a-button @click="formVisible = false">Cancel</a-button>
        <a-button type="primary" @click="submitForm">OK</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="detailVisible" title="脚本详情" :width="760" :footer="null" destroyOnClose>
      <a-descriptions bordered :column="1" size="middle">
        <a-descriptions-item label="ID">{{ detailRow.id }}</a-descriptions-item>
        <a-descriptions-item label="编码">{{ detailRow.code }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ detailRow.name }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ detailRow.type }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detailRow.status === '启用' ? 'success' : 'default'">{{ detailRow.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述">{{ detailRow.desc || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detailRow.createdTime || detailRow.createdAt }}</a-descriptions-item>
      </a-descriptions>

      <div class="modal-actions">
        <a-button @click="detailVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="envVisible" :title="`${envName} - 脚本环境管理`" :width="1100" :footer="null" :centered="true"
      :maskClosable="false" :keyboard="false" destroyOnClose wrap-class-name="env-modal-fixed">
      <a-tabs v-model:activeKey="activeEnvTab">
        <a-tab-pane key="dev" tab="开发环境" />
        <a-tab-pane key="test" tab="测试环境" />
        <a-tab-pane key="prod" tab="生产环境" />
      </a-tabs>
      <a-alert class="env-alert" type="info" show-icon :message="envTips.script[activeEnvTab]" />
      <div class="toolbar env-toolbar">
        <a-button v-if="activeEnvTab === 'dev'" type="primary" @click="createVersion">+ 新建版本</a-button>
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
              <a-tag :color="record.status === '已发布' ? 'success' : 'default'">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'op'">
              <a-space :size="6" wrap>
                <a-button size="small" @click="viewVersionEditor(record)">查看</a-button>
                <a-button v-if="activeEnvTab === 'dev'" size="small" @click="openVersionEditor(record)">编辑</a-button>
                <a-button v-if="activeEnvTab === 'test'" size="small" type="primary" ghost @click="openTestModal(record)">测试</a-button>
                <a-button v-if="activeEnvTab === 'prod'" size="small" type="primary" :ghost="record.current !== '是'"
                  :disabled="record.current === '是'" @click="selectCurrent(record)">
                  {{ record.current === '是' ? '已选中' : '选中' }}
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>

    <!-- 版本编辑器 -->
    <ScriptVersionEditor :open="editorVisible" :version-data="editingVersionData" :environment="activeEnvTab"
      :readonly="isReadonly" @save="handleSaveVersion" @cancel="handleCancelVersion" />

    <PromoteModal :open="promoteModalVisible" :versionData="promoteVersionData" :confirmLoading="promoteLoading"
      @ok="handlePromoteConfirm" @cancel="promoteModalVisible = false" />

    <ScriptTestModal v-model:open="testModalVisible" :versionData="testVersionData" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { message } from 'ant-design-vue';
import ScriptVersionEditor from '../components/ScriptVersionEditor.vue';
import ScriptTestModal from '../components/ScriptTestModal.vue';
import PromoteModal from '../components/PromoteModal.vue';
import { scriptApi } from '../api/scriptApi';

const keyword = ref('');
const loading = ref(false);
const formVisible = ref(false);
const formRef = ref();
const formRules = {
  code: [{ required: true, message: '请输入脚本编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择脚本类型', trigger: 'change' }]
};
const detailVisible = ref(false);
const envVisible = ref(false);
const isEdit = ref(false);
const editingId = ref(null);
const envName = ref('');
const activeEnvTab = ref('dev');

// 编辑器状态
const editorVisible = ref(false);
const editingVersionData = ref(null);
const currentScriptId = ref(null);
const currentScriptBasicInfo = ref(null);
const isReadonly = ref(false);

// 发布弹窗状态
const promoteModalVisible = ref(false);
const promoteVersionData = ref(null);
const promoteLoading = ref(false);
const promoteTargetEnv = ref('');

// 测试弹窗状态
const testModalVisible = ref(false);
const testVersionData = ref(null);

const scriptTypeOptions = [
  { label: 'PYTHON', value: 'PYTHON' },
  { label: 'SHELL', value: 'SHELL' },
  { label: 'SQL', value: 'SQL' },
  { label: 'Other', value: 'Other' }
];

const formState = reactive({
  code: '',
  name: '',
  type: 'PYTHON',
  status: '启用',
  desc: ''
});

const detailRow = reactive({
  id: '',
  code: '',
  name: '',
  type: '',
  status: '启用',
  desc: '',
  createdAt: ''
});

const rows = ref([]);
const total = ref(0);
const currentPage = ref(1);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await scriptApi.pageScript({
      current: currentPage.value,
      size: 10,
      keyword: keyword.value
    });
    rows.value = res.data?.data?.records || [];
    total.value = res.data?.data?.total || 0;
  } catch (e) {
    message.error('加载脚本列表失败');
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
  Object.assign(formState, { code: '', name: '', type: 'PYTHON', status: '启用', desc: '' });
  formVisible.value = true;
};

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  Object.assign(formState, { code: row.code, name: row.name, type: row.type, status: row.status, desc: row.desc });
  formVisible.value = true;
};

const openDetail = (row) => {
  Object.assign(detailRow, row);
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
      await scriptApi.updateScript(editingId.value, formState);
      message.success('修改成功');
    } else {
      await scriptApi.createScript(formState);
      message.success('创建成功');
    }
    formVisible.value = false;
    loadData();
  } catch (e) {
    message.error(e.message || '保存失败');
  }
};

const deleteScript = async (id) => {
  try {
    await scriptApi.deleteScript(id);
    message.success('删除成功');
    loadData();
  } catch (e) {
    message.error('删除失败');
  }
};

const toggleStatus = async (row) => {
  try {
    const newStatus = row.status === '启用' ? '禁用' : '启用';
    await scriptApi.toggleStatus(row.id);
    message.success(`${newStatus}成功`);
    await loadData();
  } catch (e) {
    message.error(`状态更新失败：${e.message || '操作失败'}`);
  }
};

onMounted(() => {
  loadData();
});

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

const envTips = {
  script: {
    dev: '开发环境用于编写和初步调试脚本逻辑。',
    test: '测试环境用于执行全量测试用例验证。',
    prod: '生产环境执行线上任务，变更需严格审批。'
  }
};

const envRows = ref([]);
const selectedVersionId = ref(null);

const loadVersions = async () => {
  if (!currentScriptId.value) return;
  try {
    const res = await scriptApi.listVersions(currentScriptId.value, activeEnvTab.value);
    envRows.value = res.data?.data || [];
    selectedVersionId.value = null;
  } catch (e) {
    message.error('加载版本列表失败');
  }
};

watch(activeEnvTab, () => {
  loadVersions();
});

const openEnvModal = (record, env) => {
  currentScriptId.value = record.id;
  currentScriptBasicInfo.value = { ...record };
  envName.value = record.name || record.code;
  activeEnvTab.value = env;
  selectedVersionId.value = null;
  envVisible.value = true;
  loadVersions();
};

// 版本编辑器逻辑
const openVersionEditor = (record) => {
  const latestVersion = envRows.value[0]?.version || '';
  const nextVersion = envRows.value.length ? bumpVersion(latestVersion) : '1.0.0';
  editingVersionData.value = {
    ...record,
    id: null,
    code: currentScriptBasicInfo.value.code,
    name: currentScriptBasicInfo.value.name,
    type: currentScriptBasicInfo.value.type,
    version: nextVersion,
    changeLog: `基于版本 ${record.version} 修改`
  };
  isReadonly.value = false;
  editorVisible.value = true;
};

const viewVersionEditor = (record) => {
  editingVersionData.value = {
    ...record,
    code: currentScriptBasicInfo.value.code,
    name: currentScriptBasicInfo.value.name,
    type: currentScriptBasicInfo.value.type
  };
  isReadonly.value = true;
  editorVisible.value = true;
};

const createVersion = () => {
  const latestVersion = envRows.value[0]?.version || '';
  editingVersionData.value = {
    scriptId: currentScriptId.value,
    code: currentScriptBasicInfo.value.code,
    name: currentScriptBasicInfo.value.name,
    type: currentScriptBasicInfo.value.type,
    version: envRows.value.length ? bumpVersion(latestVersion) : '1.0.0'
  };
  isReadonly.value = false;
  editorVisible.value = true;
};

const bumpVersion = (v) => {
  const parts = v.split('.').map(Number);
  parts[2]++;
  if (parts[2] > 9) {
    parts[2] = 0;
    parts[1]++;
  }
  if (parts[1] > 9) {
    parts[1] = 0;
    parts[0]++;
  }
  return parts.join('.');
};

const handleSaveVersion = async (data) => {
  try {
    if (data.id) {
      await scriptApi.updateVersion(data.id, {
        ...data,
        scriptId: currentScriptId.value,
        environment: activeEnvTab.value.toUpperCase()
      });
    } else {
      await scriptApi.createVersion({
        ...data,
        scriptId: currentScriptId.value,
        environment: activeEnvTab.value.toUpperCase()
      });
    }

    editorVisible.value = false;
    message.success('版本保存成功');
    loadVersions();
  } catch (e) {
    message.error('版本保存失败：' + (e.message || '未知错误'));
  }
};

const handleCancelVersion = () => {
  editorVisible.value = false;
};

const promoteSelected = (targetEnv) => {
  if (!selectedVersionId.value) {
    message.warning('请先勾选要发布的版本');
    return;
  }
  if (!currentScriptId.value) {
    message.warning('未识别脚本ID');
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
    createdBy: selected.createdBy || '-',
    createdTime: selected.createdTime || '-'
  };
  promoteModalVisible.value = true;
};

const handlePromoteConfirm = async (publishDescription) => {
  const toEnv = String(promoteTargetEnv.value || '').toUpperCase();
  const selected = promoteVersionData.value;

  promoteLoading.value = true;
  try {
    if (!selected) {
      throw new Error('未找到选中的版本');
    }

    await scriptApi.promoteVersion(selected.id, toEnv);

    message.success(`版本 ${selected.version} 已发布到 ${toEnv}`);
    promoteModalVisible.value = false;
    activeEnvTab.value = toEnv.toLowerCase();
    selectedVersionId.value = null;
    await loadVersions();
  } catch (e) {
    message.error(`发布失败：${e.message}`);
  } finally {
    promoteLoading.value = false;
  }
};

const openTestModal = (record) => {
  testVersionData.value = {
    id: record.id,
    version: record.version,
    code: currentScriptBasicInfo.value?.code || '',
    name: currentScriptBasicInfo.value?.name || '',
    type: currentScriptBasicInfo.value?.type || ''
  };
  testModalVisible.value = true;
};

const selectCurrent = async (record) => {
  if (!record) {
    message.warning('未找到要设置的版本');
    return;
  }
  try {
    await scriptApi.setCurrentVersion(record.id);
    message.success('已设为当前执行版本');
    await loadVersions();
  } catch (e) {
    message.error(`设置失败：${e.message}`);
  }
};

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '描述', dataIndex: 'desc' },
  { title: '创建时间', dataIndex: 'createdAt', width: 190 },
  { title: '操作', dataIndex: 'op', width: 300 }
];
</script>

<style scoped>
.env-alert {
  margin-bottom: 10px;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>