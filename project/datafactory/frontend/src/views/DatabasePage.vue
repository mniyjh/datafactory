<template>
  <div class="page-wrap">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入数据源名称或编码" />
      <a-button type="primary" @click="loadData">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建数据库</a-button>
    </div>

    <a-table :columns="columns" :data-source="rows" :pagination="{ pageSize: 10 }" row-key="id" size="middle"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'type'">
          <a-tag color="blue">{{ record.type }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag color="success" v-if="record.status === '启用'">启用</a-tag>
          <a-tag color="default" v-else>禁用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" @click="openDetail(record)">查看</a-button>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-button size="small" type="primary" ghost @click="openEnvManager(record, 'dev')">环境</a-button>
            <a-button size="small" @click="toggleStatus(record)">
              {{ record.status === '启用' ? '禁用' : '启用' }}
            </a-button>
            <a-popconfirm title="确定删除该数据库吗？" ok-text="确定" cancel-text="取消" @confirm="deleteDb(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑数据库' : '新建数据库'" :width="760" :footer="null" destroyOnClose>
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '130px' } }" class="db-form">
        <a-form-item label="数据库编码" required>
          <a-input v-model:value="formState.code" :disabled="isEdit" placeholder="例如：DB_MYSQL_001" />
        </a-form-item>

        <a-form-item label="数据库名称" required>
          <a-input v-model:value="formState.name" placeholder="例如：MySQL生产库" />
        </a-form-item>

        <a-form-item label="数据库类型" required>
          <a-select v-model:value="formState.type" :options="dbTypeOptions" placeholder="请选择数据库类型" />
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

    <a-modal v-model:open="detailVisible" title="数据库详情" :width="760" :footer="null" destroyOnClose>
      <a-descriptions bordered :column="1" size="middle">
        <a-descriptions-item label="ID">{{ detailRow.id }}</a-descriptions-item>
        <a-descriptions-item label="编码">{{ detailRow.code }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ detailRow.name }}</a-descriptions-item>
        <a-descriptions-item label="类型">
          <a-tag color="blue">{{ detailRow.type }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detailRow.status === '启用' ? 'success' : 'default'">{{ detailRow.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述">{{ detailRow.desc || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ detailRow.createdBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detailRow.createdAt }}</a-descriptions-item>
      </a-descriptions>

      <div class="modal-actions">
        <a-button @click="detailVisible = false">关闭</a-button>
      </div>
    </a-modal>

    <a-modal
      v-model:open="envVisible"
      :title="`${envDbName} - 数据库环境管理`"
      :width="1100"
      :footer="null"
      :centered="true"
      :maskClosable="false"
      :keyboard="false"
      destroyOnClose
      wrap-class-name="env-modal-fixed"
    >
      <div class="env-modal-body">
        <a-tabs v-model:activeKey="activeEnvTab" @change="handleEnvTabChange">
          <a-tab-pane key="dev" tab="开发环境" />
          <a-tab-pane key="test" tab="测试环境" />
          <a-tab-pane key="prod" tab="生产环境" />
        </a-tabs>

        <a-alert class="env-alert" type="info" show-icon :message="envTips.db[activeEnvTab]" />

        <div class="toolbar env-toolbar">
          <a-button v-if="activeEnvTab === 'dev'" type="primary" @click="openEnvVersionCreate('dev')">+ 新建版本</a-button>
          <a-button v-if="activeEnvTab === 'test'" type="primary" @click="openEnvVersionCreate('test')">+ 新建版本</a-button>
          <a-button v-if="activeEnvTab === 'prod'" type="primary" @click="openEnvVersionCreate('prod')">+ 新建版本</a-button>
        </div>

        <div class="env-table-wrap">
          <a-table :columns="dynamicEnvColumns" :data-source="currentEnvRows" row-key="id" :pagination="false"
            size="middle" :scroll="{ x: 980, y: 'calc(100vh - 360px)' }" table-layout="fixed">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'pick'">
                <a-radio :checked="selectedVersionId === record.id" @change="() => selectVersionOnly(record)" />
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === '已发布' ? 'success' : 'default'">{{ record.status }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'current'">
                <a-tag v-if="record.current === '是'" color="blue">当前版本</a-tag><span v-else>否</span>
              </template>
              <template v-else-if="column.dataIndex === 'op'">
                <a-button size="small" @click="viewVersion(record)">查看</a-button>
                <a-button size="small" @click="editVersion(record)">编辑</a-button>
                <a-button size="small" type="primary" ghost @click="testVersion(record)">连通测试</a-button>
                <a-button size="small" type="default" @click="selectCurrent(record)" :disabled="record.current === '是'">
                  {{ record.current === '是' ? '已选中' : '选中' }}
                </a-button>
              </template>
            </template>
          </a-table>
        </div>
      </div>
    </a-modal>
    <DatabaseVersionEditor :open="versionCreateVisible" :title="versionCreateTitle" :initialDsl="versionEditorDsl" :readonly="versionCreateReadOnly" :canTest="false"
      @close="versionCreateVisible = false" @save="saveEnvVersion" />
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import DatabaseVersionEditor from '../components/DatabaseVersionEditor.vue';
import { databaseApi } from '../api/databaseApi';

const keyword = ref('');
const loading = ref(false);
const formVisible = ref(false);
const formRef = ref();
const formRules = {
  code: [{ required: true, message: '请输入数据库编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入数据库名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择数据库类型', trigger: 'change' }]
};
const detailVisible = ref(false);
const envVisible = ref(false);
const activeEnvTab = ref('dev');
const envDbName = ref('');
const isEdit = ref(false);
const editingId = ref(null);
const selectedVersionId = ref(null);

const versionCreateVisible = ref(false);
const versionCreateEnv = ref('dev');
const versionEditorDsl = ref(null);
const versionCreateReadOnly = ref(false);
const versionCreateTitle = computed(() => {
  const map = { dev: '开发环境 - 新建版本', test: '测试环境 - 新建版本', prod: '生产环境 - 新建版本' };
  return map[versionCreateEnv.value] || '数据库版本新建';
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '描述', dataIndex: 'desc' },
  { title: '创建时间', dataIndex: 'createdAt', width: 190 },
  { title: '操作', dataIndex: 'op', width: 280 }
];

const rows = ref([]);

const dbTypeOptions = [
  { label: 'MySQL', value: 'MySQL' },
  { label: 'PostgreSQL', value: 'PostgreSQL' },
  { label: 'Oracle', value: 'Oracle' },
  { label: 'SQL Server', value: 'SQL Server' }
];

const envColumns = [
  { title: '', dataIndex: 'pick', width: 56 },
  { title: '版本', dataIndex: 'version', width: 90 },
  { title: '当前版本', dataIndex: 'current', width: 100 },
  { title: '变更说明', dataIndex: 'remark', width: 180 },
  { title: '创建人', dataIndex: 'creator', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'op', width: 180 }
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
  db: {
    dev: '开发环境用于数据库连接参数与权限验证。',
    test: '测试环境用于联调和验收，发布前请确认开发环境稳定。',
    prod: '生产环境配置将影响线上任务，请谨慎发布。'
  }
};

const currentEnvRows = ref([]);
const currentDbId = ref(null);

const formState = reactive({
  code: '',
  name: '',
  type: undefined,
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
  createdBy: '',
  createdAt: ''
});

const loadData = async () => {
  loading.value = true;
  try {
    const res = await databaseApi.pageDb({ current: 1, size: 100, keyword: keyword.value });
    const payload = res?.data?.data || res?.data || {};
    rows.value = payload.records || payload || [];
  } catch (error) {
    message.error(error.message || '加载数据库列表失败');
  } finally {
    loading.value = false;
  }
};

const resetSearch = () => {
  keyword.value = '';
  loadData();
};

const loadVersions = async () => {
  if (!currentDbId.value) return;
  try {
    const res = await databaseApi.listVersions(currentDbId.value, activeEnvTab.value.toUpperCase());
    const payload = res?.data?.data || res?.data || [];
    currentEnvRows.value = payload;
    selectedVersionId.value = null;
  } catch (error) {
    message.error(error.message || '加载版本列表失败');
  }
};

const bumpVersion = (v = '1.0.0') => {
  const arr = String(v || '1.0.0').split('.').map((x) => Number(x) || 0);
  while (arr.length < 3) arr.push(0);
  arr[2] += 1;
  return `${arr[0]}.${arr[1]}.${arr[2]}`;
};

onMounted(() => {
  loadData();
});

const resetForm = () => {
  formState.code = '';
  formState.name = '';
  formState.type = undefined;
  formState.status = '启用';
  formState.desc = '';
};

const openCreate = () => {
  isEdit.value = false;
  editingId.value = null;
  resetForm();
  formVisible.value = true;
};

const openEdit = (row) => {
  isEdit.value = true;
  editingId.value = row.id;
  formState.code = row.code;
  formState.name = row.name;
  formState.type = row.type;
  formState.status = row.status;
  formState.desc = row.desc;
  formVisible.value = true;
};

const openDetail = (row) => {
  detailRow.id = row.id;
  detailRow.code = row.code;
  detailRow.name = row.name;
  detailRow.type = row.type;
  detailRow.status = row.status;
  detailRow.desc = row.desc;
  detailRow.createdBy = row.createdBy;
  detailRow.createdAt = row.createdAt;
  detailVisible.value = true;
};

const toggleStatus = async (row) => {
  const newStatus = row.status === '启用' ? '禁用' : '启用';
  try {
    await databaseApi.updateDb(row.id, {
      ...row,
      status: newStatus
    });
    message.success(`${newStatus}成功`);
    await loadData();
  } catch (error) {
    message.error(`${newStatus}失败：${error.message}`);
  }
};

const openEnvManager = async (row, env = 'dev') => {
  envDbName.value = row.name || row.code;
  currentDbId.value = row.id;
  activeEnvTab.value = env;
  selectedVersionId.value = null;
  versionCreateVisible.value = false;
  versionCreateReadOnly.value = false;
  versionEditorDsl.value = null;
  envVisible.value = true;
  await loadVersions();
};

const handleEnvTabChange = async () => {
  selectedVersionId.value = null;
  await loadVersions();
};

const openEnvVersionCreate = (env) => {
  versionCreateEnv.value = env;
  versionEditorDsl.value = null;
  versionCreateReadOnly.value = false;
  versionCreateVisible.value = true;
};

const selectVersionOnly = (record) => {
  selectedVersionId.value = selectedVersionId.value === record.id ? null : record.id;
};

const saveEnvVersion = async (dslPayload) => {
  if (!currentDbId.value) {
    message.warning('未识别数据库ID');
    return;
  }

  const latest = currentEnvRows.value[0]?.version || '1.0.0';
  const nextVersion = currentEnvRows.value.length ? bumpVersion(latest) : '1.0.0';

  try {
    await databaseApi.createVersion({
      dbId: currentDbId.value,
      environment: versionCreateEnv.value.toUpperCase(),
      version: nextVersion,
      dslContent: JSON.stringify({ ...dslPayload, version: nextVersion }),
      dbType: dslPayload.dbType,
      dbName: dslPayload.dbName,
      jdbcUrl: dslPayload.jdbcUrl,
      username: dslPayload.username,
      password: dslPayload.password,
      changeLog: `版本保存 ${nextVersion}`
    });
    versionCreateVisible.value = false;
    message.success('版本保存成功');
    await loadVersions();
  } catch (e) {
    message.error(`版本保存失败：${e.message}`);
  }
};

const viewVersion = (record) => {
  versionCreateEnv.value = record.environment ? String(record.environment).toLowerCase() : activeEnvTab.value;
  versionEditorDsl.value = record.dslContent || null;
  versionCreateReadOnly.value = true;
  versionCreateVisible.value = true;
};

const editVersion = (record) => {
  versionCreateEnv.value = record.environment ? String(record.environment).toLowerCase() : activeEnvTab.value;
  versionEditorDsl.value = record.dslContent || null;
  versionCreateReadOnly.value = false;
  versionCreateVisible.value = true;
};

const testVersion = async (record) => {
  try {
    const payload = {
      dbType: record.dbType,
      jdbcUrl: record.jdbcUrl,
      username: record.username,
      password: record.password
    };
    if (!payload.jdbcUrl) {
      try {
        const dsl = typeof record.dslContent === 'string' ? JSON.parse(record.dslContent) : (record.dslContent || {});
        payload.dbType = payload.dbType || dsl.dbType || dsl.databaseType || dsl.type;
        payload.jdbcUrl = payload.jdbcUrl || dsl.jdbcUrl || dsl.url;
        payload.username = payload.username || dsl.username || dsl.userName || dsl.account;
        payload.password = payload.password || dsl.password || dsl.passwd;
      } catch (_) {
        // ignore parse errors and keep validation below
      }
    }
    if (!payload.jdbcUrl) {
      message.warning('当前版本缺少可用于连通测试的JDBC URL');
      return;
    }
    await databaseApi.testConnection(payload);
    message.success(`版本 ${record.version} 测试连接成功`);
  } catch (e) {
    message.error(`测试连接失败：${e.message}`);
  }
};

const selectCurrent = async (record) => {
  if (!record) {
    message.warning('未找到要设置的版本');
    return;
  }
  try {
    await databaseApi.selectVersion(record.id);
    message.success('已设为当前版本');
    await loadVersions();
  } catch (e) {
    message.error(`设置失败：${e.message}`);
  }
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    if (isEdit.value) {
      await databaseApi.updateDb(editingId.value, formState);
    } else {
      await databaseApi.createDb(formState);
    }
    formVisible.value = false;
    await loadData();
    message.success('保存成功');
  } catch (e) {
    message.error('保存失败');
  }
};

const deleteDb = async (id) => {
  try {
    await databaseApi.deleteDb(id);
    await loadData();
    message.success('删除成功');
  } catch (e) {
    message.error('删除失败');
  }
};
</script>

<style scoped>
.db-form {
  padding-top: 8px;
}

.modal-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.env-toolbar {
  margin-top: 14px;
  margin-bottom: 14px;
}

.env-modal-body {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 240px);
  min-height: 560px;
}

.env-table-wrap {
  flex: 1;
  min-height: 0;
}

.op-actions {
  display: flex;
  flex-wrap: nowrap;
}
</style>

<style>
.env-modal-fixed .ant-modal {
  width: 1100px !important;
  max-width: calc(100vw - 48px);
}

.env-modal-fixed .ant-modal-content {
  height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
}

.env-modal-fixed .ant-modal-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
