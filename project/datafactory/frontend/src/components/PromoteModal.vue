<template>
  <a-modal
    :open="open"
    title="版本发布确认"
    @ok="handleOk"
    @cancel="handleCancel"
    :confirm-loading="confirmLoading"
    width="600px"
    :centered="true"
    :maskClosable="false"
  >
    <div class="promote-modal-body">
      <a-descriptions bordered :column="1" size="small">
        <a-descriptions-item label="版本号">{{ versionData?.version || '-' }}</a-descriptions-item>
        <a-descriptions-item label="变更说明">{{ versionData?.remark || versionData?.changeLog || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建人">{{ versionData?.creator || versionData?.createdBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ versionData?.createdAt || versionData?.createdTime || '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="publish-desc-section">
        <div class="label">发布说明:</div>
        <a-textarea
          v-model:value="publishDescription"
          placeholder="请输入本次发布的补充说明（可选）"
          :rows="4"
        />
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  open: Boolean,
  versionData: Object,
  confirmLoading: Boolean
});

const emit = defineEmits(['ok', 'cancel']);

const publishDescription = ref('');

watch(() => props.open, (newVal) => {
  if (newVal) {
    publishDescription.value = '';
  }
});

const handleOk = () => {
  emit('ok', publishDescription.value);
};

const handleCancel = () => {
  emit('cancel');
};
</script>

<style scoped>
.promote-modal-body {
  padding: 10px 0;
}

.publish-desc-section {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

.publish-desc-section .label {
  width: 80px;
  text-align: right;
  line-height: 32px;
  flex-shrink: 0;
}
</style>
