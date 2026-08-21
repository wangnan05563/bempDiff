import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

// 复现台架构建配置：用自定义插件把 AiConsole 依赖的 store 指向 mock。
export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'mock-store-resolver',
      enforce: 'pre',
      resolveId(source, importer) {
        // 任何导入路径以 /store 或 /store.js 结尾 → 替换为 mock-store.js
        const norm = source.replace(/\\/g, '/')
        if (norm === '../store' || norm === './store' || norm.endsWith('/store') || norm.endsWith('/store.js')) {
          return fileURLToPath(new URL('./mock-store.js', import.meta.url))
        }
        return null
      }
    }
  ],
  root: fileURLToPath(new URL('.', import.meta.url)),
  base: './',
  publicDir: fileURLToPath(new URL('../public', import.meta.url)),
  build: {
    outDir: 'dist',
    emptyOutDir: false,
    rollupOptions: {
      input: fileURLToPath(new URL('./repro.html', import.meta.url))
    }
  },
  server: { port: 5199, host: '127.0.0.1' }
})