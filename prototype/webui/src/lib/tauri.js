// Tauri v2 环境探测与原生对话框封装（无需额外 npm 包：依赖 tauri.conf.json 的 withGlobalTauri）。
// 设计原则：前端在「浏览器开发态」与「Tauri 生产态」共用同一套代码。
//  - 浏览器态：`isTauri()` 为 false，文件选择退化为手动输入服务器本机绝对路径。
//  - Tauri 态：withGlobalTauri=true 时 `window.__TAURI__.dialog.open` 可用，
//    直接用原生对话框拿绝对路径（免上传，无需引入 @tauri-apps/plugin-dialog 包）。

export function isTauri() {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

/**
 * 调 Tauri 原生对话框选择文件或文件夹。
 * @param {{directory?: boolean, multiple?: boolean}} opts
 * @returns {Promise<string|string[]|null>} 绝对路径；取消/非 Tauri 环境返回 null。
 */
export async function pickPath({ directory = false, multiple = false } = {}) {
  if (!isTauri()) return null
  try {
    const dialog = window.__TAURI__ && window.__TAURI__.dialog
    if (!dialog || typeof dialog.open !== 'function') return null
    const sel = await dialog.open({ directory, multiple })
    return sel
  } catch (e) {
    console.warn('[tauri] 原生对话框调用失败，回退手动输入：', e)
    return null
  }
}
