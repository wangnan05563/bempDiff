import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发时前端跑在 5173，后端 `server` 子命令默认 18765。
// dev proxy 把 /api 转发到后端，避免跨端口 CORS（后端也自带 Access-Control-Allow-Origin:* 兜底）。
// 生产（Tauri sidecar 或 `server --webroot`）由后端同源托管 SPA，base 用 './' 适配任意挂载路径。
const SERVER_PORT = process.env.BEMPDIFF_SERVER_PORT || '18765'

export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: `http://127.0.0.1:${SERVER_PORT}`,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true
  }
})
