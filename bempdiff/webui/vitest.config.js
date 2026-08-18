import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// 组件级回归测试配置：用 Vue SFC 编译器 + jsdom 真实挂载组件，
// 不依赖 Java 后端（api.client 在测试中被 mock），用于复现/守护前端交互缺陷。
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/__tests__/**/*.spec.js']
  }
})
