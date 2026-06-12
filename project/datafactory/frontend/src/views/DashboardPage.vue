<template>
  <div class="dashboard-container">
    <!-- 系统概况 -->
    <div class="stat-row">
      <div class="stat-card" v-for="item in displayStats" :key="item.title">
        <div class="stat-title">{{ item.title }}</div>
        <div class="stat-value" style="font-size:36px">{{ item.value }}</div>
      </div>
    </div>

    <!-- 实时监控 -->
    <div class="section-title-row"><span class="section-label">实时监控</span><span class="auto-refresh">每 30s 自动刷新</span></div>
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-title">堆内存使用</div>
        <div class="stat-value">{{ metrics.jvm.heapUsedMB }}<span style="font-size:16px;color:#888"> / {{ metrics.jvm.heapMaxMB }} MB</span></div>
        <a-progress :percent="metrics.jvm.heapUsagePercent" :stroke-color="heapColor" size="small" style="margin-top:8px" />
      </div>
      <div class="stat-card">
        <div class="stat-title">活跃线程数</div>
        <div class="stat-value">{{ metrics.threadCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">今日执行</div>
        <div class="stat-value">{{ metrics.today.total || 0 }}<span style="font-size:14px;color:#888"> 次</span></div>
        <div style="margin-top:4px;font-size:12px">
          <span style="color:#52c41a">成功 {{ metrics.today.success || 0 }}</span>
          <span style="color:#ff4d4f;margin-left:12px">失败 {{ metrics.today.failure || 0 }}</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-title">平均耗时</div>
        <div class="stat-value">{{ avgDuration }}</div>
      </div>
    </div>

    <!-- 历史面板 -->
    <div class="panel-row">
      <div class="panel">
        <div class="title">最近执行</div>
        <div class="body">
          <a-table :columns="recentColumns" :data-source="recentLogs" :pagination="false" size="small" row-key="id" style="width:100%">
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
        <div class="title">执行成功率</div>
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
defineOptions({ name: 'DashboardPage' })
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { dashboardApi } from '../api/dashboardApi';
import { taskApi } from '../api/task';
import { message } from 'ant-design-vue';
import { executionStore } from '../store/execution';

const systemStats = ref({ dbCount: 0, apiCount: 0, scriptCount: 0, taskCount: 0 });
const recentLogs = ref([]);
const executionStats = ref({ total: 0, success: 0, failure: 0, rate: 0 });
const metrics = ref({
  jvm: { heapUsedMB: 0, heapMaxMB: 0, heapUsagePercent: 0, processMemoryMB: 0 },
  threadCount: 0,
  database: {},
  today: { total: 0, success: 0, failure: 0, avgDurationMs: 0 },
  timestamp: 0
});

let metricsTimer = null;

const displayStats = computed(() => [
  { title: '数据源总数', value: systemStats.value.dbCount },
  { title: 'API总数', value: systemStats.value.apiCount },
  { title: '脚本总数', value: systemStats.value.scriptCount },
  { title: '任务总数', value: systemStats.value.taskCount }
]);

const successRate = computed(() => executionStats.value.rate || 0);
const rateFormat = (p) => Number(p).toFixed(2) + '%';

const avgDuration = computed(() => {
  const ms = metrics.value.today.avgDurationMs;
  if (!ms || ms === 0) return '--';
  return ms < 1000 ? Math.round(ms) + 'ms' : (ms / 1000).toFixed(1) + 's';
});

const heapColor = computed(() => {
  const p = metrics.value.jvm.heapUsagePercent;
  if (p > 80) return { '0%': '#ff4d4f', '100%': '#ff4d4f' };
  if (p > 60) return { '0%': '#faad14', '100%': '#faad14' };
  return { '0%': '#52c41a', '100%': '#52c41a' };
});

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
    if (statsRes.data?.data) systemStats.value = { ...statsRes.data.data };
    if (logsRes.data?.data) recentLogs.value = logsRes.data.data.records || [];
    if (overviewRes.data?.data) {
      const ov = overviewRes.data.data;
      executionStats.value = { total: ov.total || 0, success: ov.success || 0, failure: ov.failure || 0, rate: ov.rate || 0 };
    }
  } catch (e) {
    console.error(e);
  }
};

const loadMetrics = async () => {
  try {
    const res = await dashboardApi.getMetrics();
    if (res.data) metrics.value = res.data;
  } catch (e) {
    // metrics endpoint not available, ignore silently
  }
};

onMounted(() => {
  loadDashboardData();
  loadMetrics();
  metricsTimer = setInterval(loadMetrics, 30000);
});

onUnmounted(() => {
  if (metricsTimer) clearInterval(metricsTimer);
});
</script>

<style scoped>
.dashboard-container { padding: 4px; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); text-align: center; }
.stat-title { color: #8c8c8c; font-size: 14px; margin-bottom: 8px; }
.stat-value { font-weight: bold; color: #262626; }
.section-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.section-label { font-weight: bold; font-size: 15px; color: #333; }
.auto-refresh { font-size: 12px; color: #bbb; }
.panel-row { display: grid; grid-template-columns: 1.5fr 1fr; gap: 16px; }
.panel { background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); display: flex; flex-direction: column; }
.panel .title { padding: 16px; border-bottom: 1px solid #f0f0f0; font-weight: bold; font-size: 16px; }
.panel .body { padding: 16px; flex: 1; display: flex; }
</style>
