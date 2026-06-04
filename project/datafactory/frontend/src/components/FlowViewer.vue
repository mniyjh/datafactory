<template>
  <div
    ref="canvasRef"
    class="flow-viewer-canvas"
    tabindex="0"
    @mousedown="startPan"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @mouseleave="onMouseUp"
    @wheel.prevent="onWheel"
  >
    <div class="graph-stage" :style="stageStyle">
      <svg class="edge-layer">
        <defs>
          <marker id="arrow-viewer" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
            <path d="M0,0 L0,6 L9,3 z" fill="#5b8ff9" />
          </marker>
        </defs>
        <line
          v-for="e in edgeLines"
          :key="e.id"
          :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
          class="edge-line"
          marker-end="url(#arrow-viewer)"
        />
      </svg>
      <div
        v-for="node in nodes"
        :key="node.id"
        class="flow-node"
        :style="nodeStyle(node)"
        :class="{ selected: selectedId === node.id }"
        @click.stop="selectNode(node)"
      >
        <div class="node-name">{{ node.name }}</div>
        <div class="node-code">{{ node.componentCode || node.type || '' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';

const NODE_WIDTH = 160;
const NODE_HEIGHT = 62;

const props = defineProps({
  dslContent: [Object, String],
});

const emit = defineEmits(['node-click']);

const canvasRef = ref(null);
const selectedId = ref(null);
const scale = ref(1);
const pan = ref({ x: 0, y: 0 });
const panning = ref(false);
const panStart = ref({ x: 0, y: 0 });
const panBase = ref({ x: 0, y: 0 });

const nodes = ref([]);
const edges = ref([]);

const parseDsl = (raw) => {
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : (raw || {});
    return {
      nodes: Array.isArray(obj.nodes) ? obj.nodes : [],
      edges: Array.isArray(obj.edges) ? obj.edges : [],
    };
  } catch (e) {
    return { nodes: [], edges: [] };
  }
};

watch(() => props.dslContent, (val) => {
  const dsl = parseDsl(val);
  nodes.value = dsl.nodes;
  edges.value = dsl.edges;
  selectedId.value = null;
  // Auto-center the graph
  if (nodes.value.length > 0 && canvasRef.value) {
    const canvasRect = canvasRef.value.getBoundingClientRect();
    const minX = Math.min(...nodes.value.map(n => n.x || 0));
    const minY = Math.min(...nodes.value.map(n => n.y || 0));
    const maxX = Math.max(...nodes.value.map(n => (n.x || 0) + NODE_WIDTH));
    const maxY = Math.max(...nodes.value.map(n => (n.y || 0) + NODE_HEIGHT));
    const graphW = maxX - minX;
    const graphH = maxY - minY;
    scale.value = Math.min(1, Math.min(
      (canvasRect.width - 60) / (graphW || 1),
      (canvasRect.height - 60) / (graphH || 1)
    ));
    pan.value = {
      x: (canvasRect.width - graphW * scale.value) / 2 - minX * scale.value,
      y: (canvasRect.height - graphH * scale.value) / 2 - minY * scale.value,
    };
  } else {
    scale.value = 1;
    pan.value = { x: 0, y: 0 };
  }
}, { immediate: true });

const stageStyle = computed(() => ({
  transform: `translate(${pan.value.x}px, ${pan.value.y}px) scale(${scale.value})`,
  transformOrigin: '0 0',
}));

const nodeStyle = (node) => ({
  left: `${node.x || 0}px`,
  top: `${node.y || 0}px`,
  width: `${NODE_WIDTH}px`,
  height: `${NODE_HEIGHT}px`,
});

const toStagePoint = (clientX, clientY) => {
  const rect = canvasRef.value?.getBoundingClientRect();
  if (!rect) return { x: 0, y: 0 };
  return {
    x: (clientX - rect.left - pan.value.x) / scale.value,
    y: (clientY - rect.top - pan.value.y) / scale.value,
  };
};

const getEdgeNodeId = (edge, side) => {
  const endpoint = edge?.[side];
  if (endpoint && typeof endpoint === 'object') return endpoint.nodeId || endpoint.id || null;
  return side === 'source' ? edge?.sourceNodeId || null : edge?.targetNodeId || null;
};

const getPortPoint = (node, port) => {
  if (!node) return { x: 0, y: 0 };
  if (port === 'in') return { x: node.x + NODE_WIDTH / 2, y: node.y };
  return { x: node.x + NODE_WIDTH / 2, y: node.y + NODE_HEIGHT };
};

const edgeLines = computed(() =>
  edges.value
    .map((edge) => {
      const sourceId = getEdgeNodeId(edge, 'source');
      const targetId = getEdgeNodeId(edge, 'target');
      const sourceNode = nodes.value.find((n) => n.id === sourceId);
      const targetNode = nodes.value.find((n) => n.id === targetId);
      if (!sourceNode || !targetNode) return null;
      const start = getPortPoint(sourceNode, 'out');
      const end = getPortPoint(targetNode, 'in');
      return { id: edge.id, x1: start.x, y1: start.y, x2: end.x, y2: end.y };
    })
    .filter(Boolean)
);

const selectNode = (node) => {
  selectedId.value = node.id;
  emit('node-click', { ...node });
};

const startPan = (e) => {
  if (e.target?.closest('.flow-node')) return; // don't pan when clicking a node
  panning.value = true;
  panStart.value = { x: e.clientX, y: e.clientY };
  panBase.value = { x: pan.value.x, y: pan.value.y };
};

const onMouseMove = (e) => {
  if (!panning.value) return;
  pan.value.x = panBase.value.x + (e.clientX - panStart.value.x);
  pan.value.y = panBase.value.y + (e.clientY - panStart.value.y);
};

const onMouseUp = () => {
  panning.value = false;
};

const onWheel = (e) => {
  const delta = e.deltaY > 0 ? -0.1 : 0.1;
  scale.value = Math.min(2, Math.max(0.3, Number((scale.value + delta).toFixed(2))));
};
</script>

<style scoped>
.flow-viewer-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 300px;
  background: #fcfcfc;
  border: 1px solid #eee;
  border-radius: 8px;
  overflow: hidden;
  cursor: grab;
}
.flow-viewer-canvas:active {
  cursor: grabbing;
}
.graph-stage {
  position: absolute;
  left: 0;
  top: 0;
  width: 4000px;
  height: 4000px;
}
.edge-layer {
  position: absolute;
  left: 0;
  top: 0;
  width: 4000px;
  height: 4000px;
  pointer-events: none;
}
.edge-line {
  stroke: #5b8ff9;
  stroke-width: 2;
}
.flow-node {
  position: absolute;
  border: 2px solid #91caff;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  user-select: none;
}
.flow-node:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.15);
}
.flow-node.selected {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.2);
}
.node-name {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}
.node-code {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}
</style>
