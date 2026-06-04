<template>
  <div class="dashboard-container">
    <div class="stat-row">
      <div class="stat-card" v-for="item in displayStats" :key="item.title">
        <div class="stat-title">{{ item.title }}</div>
        <div class="stat-value" style="font-size:36px">{{ item.value }}</div>
      </div>
    </div>

    <div class="panel-row">
      <div class="panel">
        <div class="title">最近执行</div>
        <div class="body">
          <a-table :columns="recentColumns" :data-source="recentLogs" :pagination="false" size="small" row-key="id"
            style="width: 100%">
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'status'">
                <a-tag :color="record.status === 'SUCCESS' ? 'success' : 'error'">
                  {{ record.status === 'SUCCESS' ? '成功' : '失败' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'environment'">
                <a-tag :color="record.environment === 'PROD' ? 'red' : 'orange'">{{ record.environment }}</a-tag>
              </template>
            </template>
          </a-table>
        </div>
      </div>
      <div class="panel">
        <div class="title">执行统计</div>
        <div class="body" style="flex-direction:column;gap:16px;justify-content:center;align-items:center">
          <a-progress type="circle" :percent="successRate" :format="rateFormat" :stroke-color="{ '0%': '#ff4d4f', '100%': '#52c41a' }" />
          <div style="color:#555;font-size:16px">总体执行成功率</div>
          <div class="stat-detail" style="display:flex;gap:20px;margin-top:8px">
            <span>成功: <span style="color:#52c41a">{{ executionStats.success }}</span></span>
            <span>失败: <span style="color:#ff4d4f">{{ executionStats.failure }}</span></span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { dashboardApi } from '../api/dashboardApi';
import { taskApi } from '../api/task';
import { message } from 'ant-design-vue';
import { executionStore } from '../store/execution';

const systemStats = ref({
  dbCount: 0,
  apiCount: 0,
  scriptCount: 0,
  taskCount: 0
});

const recentLogs = ref([]);
const executionStats = ref({
  total: 0,
  success: 0,
  failure: 0,
  rate: 0
});

const displayStats = computed(() => [
  { title: '数据源总数', value: systemStats.value.dbCount },
  { title: 'API总数', value: systemStats.value.apiCount },
  { title: '脚本总数', value: systemStats.value.scriptCount },
  { title: '任务总数', value: systemStats.value.taskCount }
]);

const successRate = computed(() => {
  return executionStats.value.rate || 0;
});

const rateFormat = (percent) => {
  return Number(percent).toFixed(2) + '%';
};

const recentColumns = [
  { title: '任务名称', dataIndex: 'taskName', ellipsis: true },
  { title: '环境', dataIndex: 'environment', width: 80 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '执行时间', dataIndex: 'startTime', width: 160 }
];

const loadDashboardData = async () => {
  try {
    const [statsRes, logsRes, overviewRes] = await Promise.all([
      dashboardApi.getStats(),
      taskApi.pageLogs({ current: 1, size: 5 }),
      taskApi.getExecutionStats().catch(() => ({ data: { data: null } }))
    ]);

    if (statsRes.data?.data) {
      systemStats.value = { ...statsRes.data.data };
    }

    if (logsRes.data?.data) {
      recentLogs.value = logsRes.data.data.records || [];
    }

    if (overviewRes.data?.data) {
      const ov = overviewRes.data.data;
      executionStats.value = {
        total: ov.total || 0,
        success: ov.success || 0,
        failure: ov.failure || 0,
        rate: ov.rate || 0
      };
    }
  } catch (e) {
    console.error(e);
    message.error('加载仪表盘数据失败');
  }
};

// 监听全局执行状态，如果有任务完成，则刷新最近执行和统计
watch(() => executionStore.activeExecutions.map(e => e.status), (newStatuses, oldStatuses) => {
  if (!oldStatuses) return;
  // 如果有状态从 RUNNING 变为 SUCCESS 或 FAILURE
  const hasFinished = newStatuses.some((status, idx) =>
    status !== 'RUNNING' && oldStatuses[idx] === 'RUNNING'
  );
  if (hasFinished) {
    setTimeout(loadDashboardData, 1000); // 延迟 1s 确保后端数据已落库
  }
}, { deep: true });

onMounted(loadDashboardData);
</script>

<style scoped>
.dashboard-container {
  padding: 4px;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  text-align: center;
}

.stat-title {
  color: #8c8c8c;
  font-size: 14px;
  margin-bottom: 8px;
}

.stat-value {
  font-weight: bold;
  color: #262626;
}

.panel-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
}

.panel {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
}

.panel .title {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: bold;
  font-size: 16px;
}

.panel .body {
  padding: 16px;
  flex: 1;
  display: flex;
}
</style>
