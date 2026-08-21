// 复现台架入口：用真实 AiConsole.vue + mock store 渲染「报告生成后」的控制台。
// 用 render 函数避开运行时模板编译依赖（vite 默认 vue 为 runtime-only）。
import { createApp, h } from 'vue'
import AiConsole from '../src/components/AiConsole.vue'
import './repro.css'

createApp({
  render: () => h('div', { class: 'host' }, [h(AiConsole)])
}).mount('#app')