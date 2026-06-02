<template>
  <div>
    <div class="page-title">☰ {{ title }}</div>
    <div class="page-card"><slot /></div>
  </div>
</template>

<script setup>
defineProps({ title: String });
</script>
