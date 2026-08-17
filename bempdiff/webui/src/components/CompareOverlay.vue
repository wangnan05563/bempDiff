<script setup>
import { state } from '../store'
</script>

<template>
  <div class="busy-overlay" v-if="state.busy">
    <div class="busy-card shadow">
      <div class="spinner-border text-primary" role="status"></div>
      <div>{{ state.busyText || '处理中…' }}</div>
    </div>
  </div>
</template>
