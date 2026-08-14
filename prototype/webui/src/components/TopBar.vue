<script setup>
import { computed } from 'vue'
import { state, applyTheme } from '../store'

const isDark = computed(() => state.theme === 'dark')
function toggle() { applyTheme(isDark.value ? 'light' : 'dark') }
</script>

<template>
  <nav class="navbar navbar-expand bg-body-tertiary border-bottom px-3 py-1 d-flex align-items-center gap-2">
    <span class="brand-mark"><i class="bi bi-box-seam-fill"></i></span>
    <span class="navbar-brand mb-0 h6 me-2">票据系统 WAR/JAR 差异比对与智能分析工具</span>
    <span class="badge text-bg-secondary">v2.0 Web</span>
    <div class="d-flex align-items-center gap-3 ms-auto text-secondary" style="font-size:.75rem">
      <span><i class="bi bi-pc-display"></i> 桌面端 · 离线优先</span>
      <button class="btn btn-outline-secondary btn-sm" @click="toggle"
              :title="isDark ? '切换浅色' : '切换深色'">
        <i class="bi" :class="isDark ? 'bi-sun' : 'bi-moon-stars'"></i>
      </button>
    </div>
  </nav>
</template>
