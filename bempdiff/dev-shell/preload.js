// BempDiff Electron preload —— 仅暴露文件选择桥给渲染进程。
// 设计原则：
//   - 渲染进程只能通过 contextBridge 拿到受控 API（sandbox + contextIsolation）。
//   - 不暴露 ipcRenderer 自身，避免渲染进程误用任意 channel。
//   - 与 webui/src/lib/tauri.js 的 pickPath(opts) 签名保持一致，
//     前端无需关心是 Electron 还是 Tauri 环境。

const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('bempdiff', {
  /**
   * 调主进程 dialog.showOpenDialog 选择文件或文件夹。
   * @param {{directory?: boolean, multiple?: boolean}} [opts]
   * @returns {Promise<string|string[]|null>} 绝对路径；取消返回 null；多选时返回数组。
   */
  pickPath(opts = {}) {
    return ipcRenderer.invoke('bempdiff:pick-path', opts)
  },
  /**
   * 注册「外部入口传入比对路径」监听（右键菜单 / 命令行参数）。
   * 主进程在收到文件参数时通过 webContents.send('bempdiff:shell-compare', paths) 推送；
   * 渲染进程（store.init）据此自动填路径并比对。
   * @param {(paths: string[]) => void} cb
   */
  onShellCompare(cb) {
    ipcRenderer.on('bempdiff:shell-compare', (_e, paths) => {
      if (typeof cb === 'function') cb(paths)
    })
  }
})