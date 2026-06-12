<template>
  <div class="page-wrap" ref="pageRoot">
    <div class="toolbar">
      <span class="keyword-label">关键字：</span>
      <a-input v-model:value="keyword" placeholder="请输入用户名或姓名" />
      <a-button type="primary" @click="loadData">搜索</a-button>
      <a-button class="btn-reset" @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="openCreate">+ 新建用户</a-button>
    </div>

    <a-table :columns="columns" :data-source="rows" :pagination="{ pageSize: 10 }" row-key="id" size="middle"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'roles'">
          <template v-if="record.roles && record.roles.length">
            <a-tag v-for="role in record.roles" :key="role.id || role" color="blue">
              {{ role.label || role.name || role }}
            </a-tag>
          </template>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-switch :checked="record.status === 'enabled' || record.status === 1"
            @change="(checked) => toggleStatus(record, checked)" />
        </template>
        <template v-else-if="column.dataIndex === 'op'">
          <a-space :size="6" class="op-actions">
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除该用户吗？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formVisible" :title="isEdit ? '编辑用户' : '新建用户'" :width="600" :footer="null" destroyOnClose
      :getContainer="() => pageRoot">
      <a-form ref="formRef" :model="formState" :rules="formRules" :label-col="{ style: { width: '100px' } }">
        <a-form-item label="用户名" required name="username">
          <a-input v-model:value="formState.username" :disabled="isEdit" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item v-if="!isEdit" label="密码" required name="password">
          <a-input-password v-model:value="formState.password" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item label="姓名" name="realName">
          <a-input v-model:value="formState.realName" placeholder="请输入姓名" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="formState.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="手机号" name="phone">
          <a-input v-model:value="formState.phone" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item label="角色" name="roleIds">
          <a-select v-model:value="formState.roleIds" mode="multiple" placeholder="请选择角色" :options="roleOptions" />
        </a-form-item>
      </a-form>

      <div class="modal-actions">
        <a-button @click="formVisible = false">取消</a-button>
        <a-button type="primary" @click="submitForm">确定</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
defineOptions({ name: 'UserPage' });
import { reactive, ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { pageUsers, createUser, updateUser, deleteUser, getRoles } from '../api/userApi';

const pageRoot = ref(null);

const keyword = ref('');
const loading = ref(false);
const formVisible = ref(false);
const formRef = ref();
const isEdit = ref(false);
const editingId = ref(null);

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'realName' },
  { title: '邮箱', dataIndex: 'email' },
  { title: '角色', dataIndex: 'roles' },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'op', width: 150 }
];

const rows = ref([]);
const roleOptions = ref([]);

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

const formState = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  roleIds: []
});

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageUsers({ current: 1, size: 100, keyword: keyword.value });
    const payload = res?.data || {};
    rows.value = payload.records || payload || [];
  } catch (error) {
    message.error(error.message || '加载用户列表失败');
  } finally {
    loading.value = false;
  }
};

const loadRoles = async () => {
  try {
    const res = await getRoles();
    const list = res?.data || [];
    roleOptions.value = list.map(r => ({ label: r.label || r.name, value: r.id || r.code }));
  } catch (error) {
    message.error(error.message || '加载角色列表失败');
  }
};

const resetSearch = () => {
  keyword.value = '';
  loadData();
};

const resetForm = () => {
  formState.username = '';
  formState.password = '';
  formState.realName = '';
  formState.email = '';
  formState.phone = '';
  formState.roleIds = [];
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
  formState.username = row.username;
  formState.password = '';
  formState.realName = row.realName || '';
  formState.email = row.email || '';
  formState.phone = row.phone || '';
  formState.roleIds = (row.roles || []).map(r => r.id || r.code || r);
  formVisible.value = true;
};

const toggleStatus = async (record, checked) => {
  const newStatus = checked ? 'enabled' : 'disabled';
  try {
    await updateUser(record.id, { status: newStatus });
    message.success(checked ? '启用成功' : '禁用成功');
    await loadData();
  } catch (error) {
    message.error('状态更新失败：' + (error.message || ''));
  }
};

const submitForm = async () => {
  try {
    await formRef.value.validate();
  } catch (e) {
    return;
  }
  try {
    const data = { ...formState };
    if (isEdit.value) {
      delete data.password;
      await updateUser(editingId.value, data);
    } else {
      await createUser(data);
    }
    formVisible.value = false;
    await loadData();
    message.success('保存成功');
  } catch (e) {
    message.error('保存失败：' + (e.message || ''));
  }
};

const handleDelete = async (id) => {
  try {
    await deleteUser(id);
    await loadData();
    message.success('删除成功');
  } catch (e) {
    message.error('删除失败：' + (e.message || ''));
  }
};

onMounted(() => {
  loadData();
  loadRoles();
});
</script>
