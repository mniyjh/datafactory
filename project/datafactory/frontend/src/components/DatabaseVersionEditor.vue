<template>
  <div ref="root"><a-modal :getContainer="() => root" :zIndex="1050" :open="open" @update:open="(val) => { if (!val) emit('close'); }"
    :title="titleText" :width="800" :footer="null" :centered="true"
    :maskClosable="false" :keyboard="false" destroyOnClose wrap-class-name="env-modal-fixed">
    <div class="editor-wrap">
      <div class="top-bar">
        <div class="top-tip">{{ readonly ? '当前为只读模式' : '版本号将由系统自动生成' }}</div>
      </div>

      <div class="editor-body">
        <a-form :model="formState" :label-col="{ style: { width: '120px' } }" class="db-version-form">
          <a-form-item label="数据库类型" required>
            <a-select v-model:value="formState.dbType" :options="dbTypeOptions" placeholder="请选择数据库类型"
              :disabled="readonly" />
          </a-form-item>
          <a-form-item label="数据源名称" required>
            <a-input v-model:value="formState.dbName" placeholder="请输入数据源名称" :disabled="readonly" />
          </a-form-item>
          <a-form-item label="数据源描述">
            <a-input v-model:value="formState.dbDesc" placeholder="请输入数据源描述" :disabled="readonly" />
          </a-form-item>
          <a-form-item label="JDBC URL" required>
            <a-input v-model:value="formState.jdbcUrl" placeholder="例如: jdbc:mysql://localhost:3306/db"
              :disabled="readonly" />
          </a-form-item>
          <a-form-item label="用户名">
            <a-input v-model:value="formState.username" placeholder="请输入用户名" :disabled="readonly" />
          </a-form-item>
          <a-form-item label="密码">
            <a-input-password v-model:value="formState.password" placeholder="请输入密码" :disabled="readonly" />
          </a-form-item>
        </a-form>
      </div>

      <div class="footer-actions">
        <a-button @click="emit('close')">{{ readonly ? '关闭' : '取消' }}</a-button>
        <a-button v-if="canTest && !readonly" type="primary" ghost @click="testConnect" :loading="testing">测试连接</a-button>
        <a-button v-if="!readonly" type="primary" @click="save">保存</a-button>
      </div>
    </div>
  </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, watch, ref } from 'vue';
import { message } from 'ant-design-vue';
import { databaseApi } from '../api/databaseApi';
const root = ref(null);

const props = defineProps({
  open: Boolean,
  title: {
    type: String,
    default: ''
  },
  initialDsl: {
    type: [String, Object],
    default: null
  },
  readonly: {
    type: Boolean,
    default: false
  },
  canTest: {
    type: Boolean,
    default: true
  }
});

const titleText = computed(() => props.title || (props.readonly ? '查看数据库版本配置' : '数据库版本编辑'));
const emit = defineEmits(['close', 'save']);

const testing = ref(false);

const dbTypeOptions = [
  { label: 'MySQL', value: 'MySQL' },
  { label: 'Oracle', value: 'Oracle' },
  { label: 'SQL Server', value: 'SQL Server' },
  { label: 'DB2', value: 'DB2' },
  { label: 'DM DBMS', value: 'DM DBMS' },
  { label: 'Essbase', value: 'Essbase' },
  { label: 'GBase', value: 'GBase' },
  { label: 'GreenPlum', value: 'GreenPlum' },
  { label: 'KingBaseES', value: 'KingBaseES' },
  { label: 'Netezza', value: 'Netezza' },
  { label: 'Sybase', value: 'Sybase' },
  { label: 'PetaBase', value: 'PetaBase' },
  { label: 'TeraData', value: 'TeraData' },
  { label: 'Hive', value: 'Hive' },
  { label: '其他', value: '其他' }
];

const formState = reactive({
  dbType: 'MySQL',
  dbName: '',
  dbDesc: '',
  jdbcUrl: '',
  username: '',
  password: ''
});

const applyInitialDsl = () => {
  if (!props.initialDsl) {
    formState.dbType = 'MySQL';
    formState.dbName = '';
    formState.dbDesc = '';
    formState.jdbcUrl = '';
    formState.username = '';
    formState.password = '';
    return;
  }
  try {
    const parsed = typeof props.initialDsl === 'string' ? JSON.parse(props.initialDsl) : props.initialDsl;
    formState.dbType = parsed.dbType || parsed.databaseType || parsed.type || 'MySQL';
    formState.dbName = parsed.dbName || parsed.name || '';
    formState.dbDesc = parsed.dbDesc || parsed.desc || parsed.description || '';
    formState.jdbcUrl = parsed.jdbcUrl || parsed.url || '';
    formState.username = parsed.username || parsed.userName || parsed.account || '';
    formState.password = parsed.password || parsed.passwd || '';
  } catch {
    formState.dbType = 'MySQL';
    formState.dbName = '';
    formState.dbDesc = '';
    formState.jdbcUrl = '';
    formState.username = '';
    formState.password = '';
  }
};

watch(() => props.open, (val) => {
  if (val) {
    applyInitialDsl();
  }
});

watch(() => props.initialDsl, () => {
  if (props.open) {
    applyInitialDsl();
  }
});

const testConnect = async () => {
  if (!formState.jdbcUrl) {
    message.warning('请输入JDBC URL');
    return;
  }
  testing.value = true;
  try {
    await databaseApi.testConnection({ ...formState });
    message.success('连接成功');
  } catch (e) {
    message.error(`连接失败：${e.message}`);
  } finally {
    testing.value = false;
  }
};

const save = () => {
  if (!formState.dbType) {
    message.warning('请选择数据库类型');
    return;
  }
  if (!formState.dbName) {
    message.warning('请输入数据源名称');
    return;
  }
  if (!formState.jdbcUrl) {
    message.warning('请输入JDBC URL');
    return;
  }
  emit('save', { ...formState });
};
</script>

<style scoped>
.editor-wrap {
  display: flex;
  flex-direction: column;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.top-tip {
  background: #e6f4ff;
  border: 1px solid #bae0ff;
  padding: 8px 12px;
  border-radius: 6px;
  width: 100%;
}

.editor-body {
  padding: 20px 40px;
}

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  border-top: 1px solid #f0f0f0;
  padding-top: 16px;
}
</style>
