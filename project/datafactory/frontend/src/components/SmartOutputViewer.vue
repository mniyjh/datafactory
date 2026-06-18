<template>
  <div class="smart-output-viewer">
    <!-- 视图切换 -->
    <div class="viewer-toolbar" v-if="hasContent">
      <a-radio-group v-model:value="viewMode" size="small" button-style="solid">
        <a-radio-button value="json">
          <CodeOutlined /> JSON视图
        </a-radio-button>
        <a-radio-button value="text" v-if="hasTextOutput">
          <FileTextOutlined /> 文本视图
        </a-radio-button>
      </a-radio-group>
      <a-button size="small" type="link" @click="copyContent" v-if="hasContent">
        <CopyOutlined /> 复制
      </a-button>
    </div>

    <!-- JSON 视图 -->
    <div v-if="viewMode === 'json' && hasContent" class="viewer-body json-view">
      <pre>{{ formattedJson }}</pre>
    </div>

    <!-- 文本视图 -->
    <div v-if="viewMode === 'text' && hasTextOutput" class="viewer-body text-view">
      <div v-for="(block, idx) in textBlocks" :key="idx" class="text-block">
        <div class="text-block-header" v-if="block.label">
          <span class="block-label">{{ block.label }}</span>
        </div>
        <pre class="text-block-content">{{ block.content }}</pre>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!hasContent" class="viewer-empty">
      <span class="empty-text">{{ emptyText }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { CodeOutlined, FileTextOutlined, CopyOutlined } from '@ant-design/icons-vue';

const props = defineProps({
  /** JSON 字符串或对象 */
  value: { type: [String, Object], default: null },
  /** 后端传入的预提取文本（优先级最高） */
  textOutput: { type: String, default: null },
  /** 最大高度 */
  maxHeight: { type: [Number, String], default: 400 },
  /** 无内容时的提示文字 */
  emptyText: { type: String, default: '(无输出)' },
  /** 默认视图模式 */
  defaultView: { type: String, default: 'auto' } // 'auto' | 'json' | 'text'
});

const viewMode = ref('json');

// 解析输入
const parsed = computed(() => {
  if (!props.value) return null;
  if (typeof props.value === 'string') {
    try { return JSON.parse(props.value); } catch (_) { return null; }
  }
  return props.value;
});

const hasContent = computed(() => parsed.value !== null && parsed.value !== undefined);

// 格式化 JSON
const formattedJson = computed(() => {
  if (!parsed.value) return '{}';
  try {
    return JSON.stringify(parsed.value, null, 2);
  } catch (_) {
    return String(props.value);
  }
});

// 提取文本块
const textBlocks = computed(() => {
  const blocks = [];

  // 优先使用后端预提取的 textOutput
  if (props.textOutput) {
    blocks.push({ label: null, content: props.textOutput });
    return blocks;
  }

  // 客户端提取：深度遍历找出格式化文本
  if (!parsed.value) return blocks;
  const seen = new Set();
  collectTextBlocks(parsed.value, blocks, seen);
  return blocks;
});

const hasTextOutput = computed(() => textBlocks.value.length > 0);

// 自动选择视图模式
watch(() => [parsed.value, props.textOutput, props.defaultView], () => {
  if (props.defaultView === 'json') {
    viewMode.value = 'json';
  } else if (props.defaultView === 'text') {
    viewMode.value = hasTextOutput.value ? 'text' : 'json';
  } else {
    // 'auto': 有文本输出时默认显示文本视图
    viewMode.value = hasTextOutput.value ? 'text' : 'json';
  }
}, { immediate: true });

function collectTextBlocks(obj, blocks, seen) {
  if (!obj || seen.size > 50) return; // 安全上限

  if (typeof obj === 'string') {
    if (isFormattedText(obj)) {
      const h = simpleHash(obj);
      if (!seen.has(h)) {
        seen.add(h);
        blocks.push({ label: null, content: obj });
      }
    }
    return;
  }

  if (Array.isArray(obj)) {
    for (const item of obj) collectTextBlocks(item, blocks, seen);
    return;
  }

  if (typeof obj === 'object') {
    const skipKeys = new Set([
      'exitCode', 'exit_code', 'rowCount', 'durationMs', 'duration_ms',
      'scriptCode', 'statusCode', 'executionId', 'exceptionType',
      'edgeFrom', 'edgeTo', 'fieldSnapshot', 'ioSchema'
    ]);
    for (const [key, val] of Object.entries(obj)) {
      if (skipKeys.has(key)) continue;
      collectTextBlocks(val, blocks, seen);
    }
  }
}

function isFormattedText(s) {
  if (!s || s.length < 30) return false;
  // 统计换行符数量
  let newlines = 0;
  for (let i = 0; i < s.length; i++) {
    if (s.charAt(i) === '\n') newlines++;
  }
  if (newlines < 2) return false;
  // 排除纯 JSON
  const trimmed = s.trim();
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) return false;
  // 至少有一些中文字符或 emoji 信号
  return true;
}

function simpleHash(s) {
  let hash = 0;
  for (let i = 0; i < Math.min(s.length, 200); i++) {
    hash = ((hash << 5) - hash) + s.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

function copyContent() {
  const text = viewMode.value === 'text'
    ? textBlocks.value.map(b => b.content).join('\n\n')
    : formattedJson.value;
  navigator.clipboard.writeText(text).then(() => {
    message.success('已复制到剪贴板');
  }).catch(() => {
    message.warning('复制失败，请手动复制');
  });
}
</script>

<style scoped>
.smart-output-viewer {
  width: 100%;
  font-size: 13px;
}

.viewer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.viewer-body {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: auto;
  max-height: v-bind(maxHeight + 'px');
}

.viewer-body pre {
  margin: 0;
  padding: 12px;
  font-family: 'Courier New', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.json-view pre {
  background: #fafafa;
  color: #333;
}

.text-view {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.text-block {
  border-bottom: 1px solid #f0f0f0;
}

.text-block:last-child {
  border-bottom: none;
}

.text-block-header {
  padding: 6px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.block-label {
  font-size: 12px;
  color: #888;
  font-weight: 500;
}

.text-block-content {
  background: #1a1a2e;
  color: #e0e0e0 !important;
  padding: 14px 16px !important;
  font-family: 'Courier New', 'Microsoft YaHei', monospace !important;
  font-size: 14px !important;
  line-height: 1.8 !important;
  white-space: pre-wrap !important;
  word-break: break-word;
}

.viewer-empty {
  text-align: center;
  padding: 20px;
  color: #bbb;
  font-size: 13px;
}
</style>
