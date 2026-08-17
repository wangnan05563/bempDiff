// 桌面壳环境探测与原生对话框封装。
// 文件名沿用 tauri.js 是历史包袱（早期只支持 Tauri v2），实际同时支持两套桌面壳：
//   - Electron：preload 暴露 window.bempdiff.pickPath(opts)（IPC → 主进程 dialog.showOpenDialog）。
//   - Tauri v2：withGlobalTauri=true 时 window.__TAURI__.dialog.open 可用。
//   - 浏览器（vite dev / 静态托管）：返回 null，由调用方提示手动输入服务器本机绝对路径。
// 路由顺序：Electron → Tauri → null（Electron 是当前默认桌面壳，优先）。

export function isTauri() {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

export function isElectron() {
  return typeof window !== 'undefined'
    && !!window.bempdiff
    && typeof window.bempdiff.pickPath === 'function'
}

/**
 * 调桌面壳原生对话框选择文件或文件夹。
 * opts 默认值会被规整成 { directory: boolean, multiple: boolean } 后传给桥，避免桥实现里
 * 碰到 undefined 分支不一致。
 * @param {{directory?: boolean, multiple?: boolean}} opts
 * @returns {Promise<string|string[]|null>} 绝对路径；取消/非桌面环境返回 null。
 */
export async function pickPath(opts = {}) {
  const merged = { directory: !!opts.directory, multiple: !!opts.multiple }
  if (isElectron()) {
    try {
      return await window.bempdiff.pickPath(merged)
    } catch (e) {
      console.warn('[electron] 原生文件对话框调用失败：', e)
      return null
    }
  }
  if (isTauri()) {
    try {
      const dialog = window.__TAURI__ && window.__TAURI__.dialog
      if (!dialog || typeof dialog.open !== 'function') return null
      return await dialog.open(merged)
    } catch (e) {
      console.warn('[tauri] 原生对话框调用失败，回退手动输入：', e)
      return null
    }
  }
  return null
}