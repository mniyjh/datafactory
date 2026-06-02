import { reactive } from 'vue';

export const executionStore = reactive({
  activeExecutions: [],
  
  addExecution(execution) {
    this.activeExecutions.push({
      ...execution,
      status: execution?.status || 'RUNNING',
      nodes: Array.isArray(execution?.nodes) ? execution.nodes : [],
      startTime: execution?.startTime || new Date().toISOString(),
      isCollapsed: false,
    });
  },

  updateNodeStatus(executionId, nodeId, status, data = null) {
    const exec = this.activeExecutions.find(e => e.executionId === executionId);
    if (exec) {
      const node = exec.nodes.find(n => n.id === nodeId);
      if (node) {
        node.status = status;
        if (data) node.data = data;
      } else {
        exec.nodes.push({ id: nodeId, status, data });
      }
    }
  },

  finishExecution(executionId, status, result = null) {
    const exec = this.activeExecutions.find(e => e.executionId === executionId);
    if (exec) {
      exec.status = status;
      exec.result = result;
      // 保持一段时间后自动移除，或者让用户手动关闭
    }
  },

  removeExecution(executionId) {
    this.activeExecutions = this.activeExecutions.filter(e => e.executionId !== executionId);
  }
});
