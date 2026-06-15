<template>
  <div class="profile-root">
    <!-- ========== 顶部信息卡 ========== -->
    <div class="profile-card">
      <div class="profile-avatar">
        <a-avatar :size="72" :style="{ background: '#111', fontSize: '32px' }">
          {{ avatarLetter }}
        </a-avatar>
      </div>
      <div class="profile-info">
        <div class="profile-name">{{ user.realName || user.username }}</div>
        <div class="profile-meta">
          <span>@{{ user.username }}</span>
          <span class="meta-sep">|</span>
          <span>{{ user.email || '未绑定邮箱' }}</span>
          <span class="meta-sep">|</span>
          <span v-for="r in user.roles" :key="r" class="role-tag">{{ r }}</span>
        </div>
      </div>
      <div class="profile-actions">
        <a-button type="default" @click="openEditProfile">编辑资料</a-button>
      </div>
    </div>

    <!-- ========== Tab 切换 ========== -->
    <div class="profile-tabs">
      <div class="tab-item" :class="{ active: activeTab === 'info' }" @click="activeTab = 'info'">个人信息</div>
      <div v-if="isAdmin" class="tab-item" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">
        用户管理
        <span class="tab-badge">{{ totalUsers }}</span>
      </div>
    </div>

    <!-- ========== 个人信息 Tab ========== -->
    <div v-if="activeTab === 'info'" class="tab-content">
      <!-- 账户信息 -->
      <div class="section">
        <div class="section-title">账户信息</div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ user.username }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">姓名</span>
            <span class="info-value">{{ user.realName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">邮箱</span>
            <span class="info-value">{{ user.email || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">手机号</span>
            <span class="info-value">{{ user.phone || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">角色</span>
            <span class="info-value">
              <a-tag v-for="r in user.roles" :key="r" color="#111">{{ r }}</a-tag>
              <span v-if="!user.roles?.length">-</span>
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">权限</span>
            <span class="info-value">
              <a-tag v-for="p in user.permissions?.slice(0, 8)" :key="p" color="#555">{{ p }}</a-tag>
              <span v-if="user.permissions?.length > 8" class="more-hint">+{{ user.permissions.length - 8 }} more</span>
              <span v-if="!user.permissions?.length">-</span>
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">所属租户</span>
            <span class="info-value">
              <a-tag v-for="t in user.tenants" :key="t.id" color="#888">{{ t.name }}</a-tag>
              <span v-if="!user.tenants?.length">-</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 修改密码 -->
      <div class="section">
        <div class="section-title">修改密码</div>
        <a-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" layout="horizontal"
          :label-col="{ style: { width: '100px' } }" class="pwd-form">
          <a-form-item label="当前密码" name="oldPassword">
            <a-input-password v-model:value="pwdForm.oldPassword" placeholder="请输入当前密码" style="width: 280px" />
          </a-form-item>
          <a-form-item label="新密码" name="newPassword">
            <a-input-password v-model:value="pwdForm.newPassword" placeholder="请输入新密码" style="width: 280px" />
          </a-form-item>
          <a-form-item label="确认密码" name="confirmPassword">
            <a-input-password v-model:value="pwdForm.confirmPassword" placeholder="请再次输入新密码" style="width: 280px" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleChangePwd" :loading="pwdLoading" class="btn-dark">修改密码</a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>

    <!-- ========== 用户管理 Tab（管理员） ========== -->
    <div v-if="activeTab === 'users' && isAdmin" class="tab-content">
      <div class="section">
        <div class="toolbar">
          <a-input v-model:value="userKeyword" placeholder="搜索用户名或姓名" style="width: 200px" />
          <a-button type="primary" @click="loadUsers" class="btn-dark">搜索</a-button>
          <a-button @click="resetUserSearch">重置</a-button>
          <a-button type="primary" @click="openCreate" class="btn-dark">+ 新建用户</a-button>
        </div>

        <a-table
          :columns="userColumns"
          :data-source="userRows"
          :pagination="{ pageSize: 10 }"
          row-key="id"
          size="middle"
          :loading="userLoading"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'roles'">
              <a-tag v-for="r in (record.roles || [])" :key="r.id || r" color="#111">{{ r.label || r.name || r }}</a-tag>
              <span v-if="!record.roles?.length">-</span>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-switch
                v-if="record.id !== authStore.user?.id"
                :checked="record.status === 'enabled' || record.status === 1"
                @change="(checked) => toggleStatus(record, checked)"
              />
              <span v-else style="color:#999;font-size:12px">当前用户</span>
            </template>
            <template v-else-if="column.dataIndex === 'op'">
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                v-if="record.id !== authStore.user?.id"
                title="确定删除该用户吗？" @confirm="handleDelete(record.id)">
                <a-button size="small" danger class="ml-8">删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- ========== 编辑资料弹窗 ========== -->
    <a-modal v-model:open="profileModalVisible" title="编辑个人资料" :width="480" :footer="null" destroyOnClose>
      <a-form ref="profileFormRef" :model="profileForm" :rules="profileRules"
        :label-col="{ style: { width: '80px' } }">
        <a-form-item label="姓名" name="realName">
          <a-input v-model:value="profileForm.realName" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="profileForm.email" />
        </a-form-item>
        <a-form-item label="手机号" name="phone">
          <a-input v-model:value="profileForm.phone" />
        </a-form-item>
      </a-form>
      <div class="modal-actions">
        <a-button @click="profileModalVisible = false">取消</a-button>
        <a-button type="primary" @click="submitProfile" class="btn-dark">保存</a-button>
      </div>
    </a-modal>

    <!-- ========== 用户编辑弹窗 ========== -->
    <a-modal v-model:open="userModalVisible" :title="isEdit ? '编辑用户' : '新建用户'" :width="560" :footer="null" destroyOnClose>
      <a-form ref="userFormRef" :model="userForm" :rules="userFormRules"
        :label-col="{ style: { width: '80px' } }">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="userForm.username" :disabled="isEdit" />
        </a-form-item>
        <a-form-item v-if="!isEdit" label="密码" name="password">
          <a-input-password v-model:value="userForm.password" />
        </a-form-item>
        <a-form-item label="姓名" name="realName">
          <a-input v-model:value="userForm.realName" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="userForm.email" />
        </a-form-item>
        <a-form-item label="手机号" name="phone">
          <a-input v-model:value="userForm.phone" />
        </a-form-item>
        <a-form-item label="角色" name="roleIds">
          <a-select v-model:value="userForm.roleIds" mode="multiple" :options="roleOptions" />
        </a-form-item>
      </a-form>
      <div class="modal-actions">
        <a-button @click="userModalVisible = false">取消</a-button>
        <a-button type="primary" @click="submitUserForm" class="btn-dark">确定</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
defineOptions({ name: 'ProfilePage' });
import { ref, reactive, computed, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { authStore } from '../store/auth';
import { pageUsers, createUser, updateUser, deleteUser, changePassword } from '../api/userApi';
import { getRoles } from '../api/userApi';

// ─── 当前用户 ───
const user = computed(() => authStore.user || {});
const isAdmin = computed(() => authStore.isAdmin);
const avatarLetter = computed(() => (user.value.realName || user.value.username || '?')[0].toUpperCase());

// ─── Tab ───
const activeTab = ref('info');
const totalUsers = ref(0);

// ─── 修改密码 ───
const pwdFormRef = ref();
const pwdLoading = ref(false);
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码' }],
  newPassword: [{ required: true, message: '请输入新密码' }, { min: 6, message: '至少6位' }],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    { validator: (_, v) => v === pwdForm.newPassword ? Promise.resolve() : Promise.reject('两次密码不一致') }
  ],
};

const handleChangePwd = async () => {
  try { await pwdFormRef.value.validate(); } catch { return; }
  pwdLoading.value = true;
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword });
    message.success('密码修改成功，请重新登录');
    pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdForm.confirmPassword = '';
  } catch (e) {
    message.error(e.message || '修改失败');
  } finally { pwdLoading.value = false; }
};

// ─── 编辑资料 ───
const profileModalVisible = ref(false);
const profileFormRef = ref();
const profileForm = reactive({ realName: '', email: '', phone: '' });
const profileRules = {};
const openEditProfile = () => {
  profileForm.realName = user.value.realName || '';
  profileForm.email = user.value.email || '';
  profileForm.phone = user.value.phone || '';
  profileModalVisible.value = true;
};
const submitProfile = async () => {
  try { await profileFormRef.value.validate(); } catch { return; }
  try {
    await updateUser(user.value.id, { ...profileForm });
    // Refresh user info in store
    authStore.user = { ...authStore.user, ...profileForm };
    localStorage.setItem('userInfo', JSON.stringify(authStore.user));
    message.success('资料更新成功');
    profileModalVisible.value = false;
  } catch (e) {
    message.error(e.message || '更新失败');
  }
};

// ─── 用户管理（从 UserPage 移入） ───
const userKeyword = ref('');
const userLoading = ref(false);
const userModalVisible = ref(false);
const userFormRef = ref();
const isEdit = ref(false);
const editingId = ref(null);
const userRows = ref([]);
const roleOptions = ref([]);

const userColumns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '姓名', dataIndex: 'realName' },
  { title: '邮箱', dataIndex: 'email' },
  { title: '角色', dataIndex: 'roles' },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '操作', dataIndex: 'op', width: 150 },
];

const userFormRules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }],
};
const userForm = reactive({ username: '', password: '', realName: '', email: '', phone: '', roleIds: [] });

const loadUsers = async () => {
  userLoading.value = true;
  try {
    const res = await pageUsers({ current: 1, size: 100, keyword: userKeyword.value });
    const payload = res?.data || {};
    userRows.value = payload.records || payload || [];
    totalUsers.value = userRows.value.length;
  } catch (e) {
    message.error('加载失败');
  } finally { userLoading.value = false; }
};

const loadRoles = async () => {
  try {
    const res = await getRoles();
    roleOptions.value = (res?.data || []).map(r => ({ label: r.label || r.name, value: r.id || r.code }));
  } catch { /* ignore */ }
};

const resetUserSearch = () => { userKeyword.value = ''; loadUsers(); };
const resetUserForm = () => {
  userForm.username = ''; userForm.password = ''; userForm.realName = '';
  userForm.email = ''; userForm.phone = ''; userForm.roleIds = [];
};

const openCreate = () => { isEdit.value = false; editingId.value = null; resetUserForm(); userModalVisible.value = true; };
const openEdit = (row) => {
  isEdit.value = true; editingId.value = row.id;
  userForm.username = row.username; userForm.password = '';
  userForm.realName = row.realName || ''; userForm.email = row.email || '';
  userForm.phone = row.phone || ''; userForm.roleIds = (row.roles || []).map(r => r.id || r.code || r);
  userModalVisible.value = true;
};

const toggleStatus = async (record, checked) => {
  try {
    await updateUser(record.id, { status: checked ? 'enabled' : 'disabled' });
    message.success(checked ? '已启用' : '已禁用');
    loadUsers();
  } catch (e) { message.error('操作失败'); }
};

const submitUserForm = async () => {
  try { await userFormRef.value.validate(); } catch { return; }
  try {
    const data = { ...userForm };
    if (isEdit.value) { delete data.password; await updateUser(editingId.value, data); }
    else { await createUser(data); }
    userModalVisible.value = false;
    await loadUsers();
    message.success('保存成功');
  } catch (e) { message.error('保存失败: ' + (e.message || '')); }
};

const handleDelete = async (id) => {
  try { await deleteUser(id); await loadUsers(); message.success('已删除'); }
  catch (e) { message.error('删除失败'); }
};

onMounted(() => {
  if (isAdmin.value) loadUsers();
  loadRoles();
});
</script>

<style scoped>
/* ═══ 黑白风格基色 ═══ */
.profile-root {
  max-width: 900px;
  margin: 0 auto;
  padding: 4px;
}

/* ═══ 顶部信息卡 ═══ */
.profile-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 28px 32px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  margin-bottom: 16px;
}
.profile-avatar { flex-shrink: 0; }
.profile-info { flex: 1; min-width: 0; }
.profile-name { font-size: 22px; font-weight: 700; color: #111; margin-bottom: 6px; }
.profile-meta { font-size: 13px; color: #666; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.meta-sep { color: #ccc; }
.role-tag { background: #111; color: #fff; padding: 1px 8px; border-radius: 3px; font-size: 11px; }
.profile-actions { flex-shrink: 0; }

/* ═══ Tab 切换 ═══ */
.profile-tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid #e5e5e5;
  margin-bottom: 20px;
}
.tab-item {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;
  color: #888;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tab-item:hover { color: #333; }
.tab-item.active { color: #111; border-bottom-color: #111; }
.tab-badge {
  background: #111;
  color: #fff;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 10px;
}

/* ═══ 内容区 ═══ */
.tab-content { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 24px; }

.section { margin-bottom: 28px; }
.section:last-child { margin-bottom: 0; }
.section-title { font-size: 15px; font-weight: 600; color: #111; margin-bottom: 16px; padding-bottom: 10px; border-bottom: 1px solid #eee; }

/* 信息网格 */
.info-grid { display: grid; gap: 16px; }
.info-item { display: flex; align-items: baseline; gap: 12px; }
.info-label { color: #888; font-size: 13px; min-width: 60px; flex-shrink: 0; }
.info-value { color: #111; font-size: 14px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.more-hint { color: #999; font-size: 12px; }

/* 密码表单 */
.pwd-form { margin-top: 4px; }

/* 工具栏 */
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }

/* 弹窗操作区 */
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 20px; }

/* 暗色按钮 */
.btn-dark {
  background: #111 !important;
  border-color: #111 !important;
  color: #fff !important;
}
.btn-dark:hover { background: #333 !important; border-color: #333 !important; }

.ml-8 { margin-left: 8px; }
</style>
