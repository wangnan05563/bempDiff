// BempDiff Electron 桌面壳（开发态 / 生产态共用）
// 职责：
//   1) 自适应屏幕创建主窗口 —— 避免初始窗口过大被任务栏 / 屏幕截断
//   2) 以内嵌 sidecar 方式静默启动 Java 后端（javaw + windowsHide，无控制台窗口）
//      以及可选的 vite 前端开发服务器；应用退出时统一回收子进程与端口
// 仅作为原生窗口宿主 + 进程管理器，不打包任何业务代码。
// 生产打包由 electron-builder 复用同一 main.js，区别仅在于资源根路径（resourcesPath）。

const { app, BrowserWindow, ipcMain, dialog, screen } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const net = require('node:net')
const { spawn, spawnSync } = require('node:child_process')

// ---------- 配置 ----------
const PORT = 18765
const DEV_PORT = parseInt(process.env.BEMPDIFF_DEV_PORT || '5180', 10)
const DEFAULT_W = 1280
const DEFAULT_H = 900
const MIN_W = 960
const MIN_H = 640

const ROOT = resolveRoot()
const LOGDIR = path.join(ROOT, 'bempdiff', 'logs')
const BACKEND_LOG = path.join(LOGDIR, 'backend.log')
const VITE_LOG = path.join(LOGDIR, 'vite.log')

const PRELOAD = path.join(__dirname, 'preload.js')
const LOGO = path.join(__dirname, '..', 'bempdiff-logo.png')

let children = [] // sidecar 子进程，退出时回收

// ---------- 资源根路径 ----------
// 开发态：bempdiff/dev-shell -> 项目根（../../）
// 生产态：electron-builder extraResources 落地在 process.resourcesPath 下
function resolveRoot() {
  const devRoot = path.resolve(__dirname, '..', '..')
  if (fs.existsSync(path.join(devRoot, 'bempdiff'))) return devRoot
  const res = process.resourcesPath
  if (res) {
    if (fs.existsSync(path.join(res, 'bempdiff'))) return res
    if (fs.existsSync(path.join(res, 'dist_input'))) return res
  }
  return devRoot
}

function appendLog(file, ...args) {
  try {
    if (!fs.existsSync(LOGDIR)) fs.mkdirSync(LOGDIR, { recursive: true })
    fs.appendFileSync(file, args.join(' ') + '\n')
  } catch (_) {}
}

// ---------- 端口探测 ----------
function isPortOpen(port) {
  return new Promise((resolve) => {
    const s = net.connect(port, '127.0.0.1')
    let done = false
    const finish = (v) => { if (!done) { done = true; s.destroy(); resolve(v) } }
    s.setTimeout(800)
    s.on('connect', () => finish(true))
    s.on('timeout', () => finish(false))
    s.on('error', () => finish(false))
  })
}

async function waitPort(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await isPortOpen(port)) return true
    await new Promise((r) => setTimeout(r, 1000))
  }
  return false
}

function killPort(port) {
  try {
    spawnSync('powershell', ['-NoProfile', '-Command',
      `$p=${port};try{$id=(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1).OwningProcess;if($id -ne $null){Stop-Process -Id $id -Force -ErrorAction SilentlyContinue;Write-Host ('[STOP] port '+$p+' PID='+$id)}}catch{}`],
      { stdio: 'ignore' })
  } catch (_) {}
}

// ---------- 资源定位 ----------
function findJava() {
  const base = path.join(ROOT, 'bempdiff')
  const jre = path.join(base, 'dist_input', 'jre', 'bin', 'javaw.exe')
  if (fs.existsSync(jre)) return jre
  const tc = path.join(base, 'toolchain')
  if (fs.existsSync(tc)) {
    for (const d of fs.readdirSync(tc)) {
      if (d.startsWith('zulu21')) {
        const p = path.join(tc, d, 'bin', 'javaw.exe')
        if (fs.existsSync(p)) return p
      }
    }
  }
  return 'javaw' // 退回 PATH
}

function findJavac() {
  const base = path.join(ROOT, 'bempdiff')
  const tc = path.join(base, 'toolchain')
  if (fs.existsSync(tc)) {
    for (const d of fs.readdirSync(tc)) {
      if (d.startsWith('zulu21')) {
        const p = path.join(tc, d, 'bin', 'javac.exe')
        if (fs.existsSync(p)) return p
      }
    }
  }
  return 'javac'
}

function findCfr() {
  const cfr = path.join(ROOT, 'bempdiff', 'dist_input', 'app', 'cfr.jar')
  return fs.existsSync(cfr) ? cfr : null
}

function findClasspath() {
  const base = path.join(ROOT, 'bempdiff', 'dist_input')
  const classes = path.join(base, 'classes')
  const jar = path.join(base, 'app', 'bempdiff.jar')
  const dev = path.join(base, 'dev_classes')
  if (fs.existsSync(classes)) return classes
  if (fs.existsSync(jar)) return jar
  if (fs.existsSync(dev)) return dev
  return null
}

// 无已编译产物时，按需从源码临时编译（开发态兜底）
function ensureClasspath() {
  const cp = findClasspath()
  if (cp) return cp
  const javac = findJavac()
  const srcDir = path.join(ROOT, 'bempdiff', 'java_core', 'src')
  if (!fs.existsSync(srcDir)) return null
  const out = path.join(ROOT, 'bempdiff', 'dist_input', 'dev_classes')
  fs.mkdirSync(out, { recursive: true })
  const files = []
  const walk = (d) => {
    for (const f of fs.readdirSync(d)) {
      const p = path.join(d, f)
      const st = fs.statSync(p)
      if (st.isDirectory()) walk(p)
      else if (f.endsWith('.java')) files.push(p)
    }
  }
  walk(srcDir)
  if (!files.length) return null
  const cfr = findCfr()
  const args = ['-encoding', 'UTF-8', '-d', out]
  if (cfr) args.push('-cp', cfr)
  args.push(...files)
  const r = spawnSync(javac, args, { cwd: ROOT, stdio: 'ignore' })
  if (r.status !== 0) return null
  return out
}

// ---------- sidecar 启动（静默、无控制台窗口） ----------
async function startBackend() {
  if (await isPortOpen(PORT)) {
    appendLog(BACKEND_LOG, '[sidecar] backend already listening on', PORT)
    return true
  }
  const java = findJava()
  const cp = ensureClasspath()
  if (!cp) {
    console.error('[BempDiff] No backend artifact and on-the-fly compile failed. Run the build script first.')
    return false
  }
  const cfr = findCfr()
  const classPath = cfr ? cp + path.delimiter + cfr : cp
  const args = ['-cp', classPath, 'com.bempdiff.Main', 'server', '--port', String(PORT)]
  if (fs.existsSync(path.join(ROOT, 'bempdiff', 'webui', 'dist'))) {
    args.push('--webroot', 'bempdiff/webui/dist')
  }
  const out = fs.openSync(BACKEND_LOG, 'a')
  const child = spawn(java, args, {
    cwd: ROOT,
    windowsHide: true, // 关键：不弹 Java 控制台窗口
    detached: true,
    stdio: ['ignore', out, out], // 日志落盘，不污染任何控制台
    env: process.env
  })
  children.push(child)
  appendLog(BACKEND_LOG, '[sidecar] backend started PID', child.pid, '->', urlSafe(classPath))
  return true
}

async function startFrontend() {
  if (await isPortOpen(DEV_PORT)) {
    appendLog(VITE_LOG, '[sidecar] vite already listening on', DEV_PORT)
    return true
  }
  const frontend = process.env.BEMPDIFF_FRONTEND || 'auto'
  let useVite = false
  if (frontend === 'dev') useVite = true
  else if (frontend === 'auto') {
    // 仅当存在 node 且 webui 依赖已安装时才走 vite dev
    try {
      spawnSync('where', ['node'], { stdio: 'ignore' })
      useVite = fs.existsSync(path.join(ROOT, 'bempdiff', 'webui', 'node_modules'))
    } catch (_) { useVite = false }
  }
  if (!useVite) return false
  const out = fs.openSync(VITE_LOG, 'a')
  const child = spawn('cmd.exe',
    ['/c', `cd /d bempdiff\\webui && npm run dev -- --port ${DEV_PORT} --host 127.0.0.1`],
    {
      cwd: ROOT,
      windowsHide: true,
      detached: true,
      stdio: ['ignore', out, out],
      env: process.env
    })
  children.push(child)
  appendLog(VITE_LOG, '[sidecar] vite started PID', child.pid)
  return true
}

function urlSafe(s) { return String(s).replace(/\\/g, '/') }

// ---------- 窗口自适应 ----------
function computeSize() {
  const area = screen.getPrimaryDisplay().workAreaSize // 已排除任务栏
  const w = Math.min(DEFAULT_W, Math.floor(area.width * 0.92))
  const h = Math.min(DEFAULT_H, Math.floor(area.height * 0.92))
  const cw = Math.max(MIN_W, w)
  const ch = Math.max(MIN_H, h)
  // 即便最小尺寸都放不下时，直接最大化
  if (cw >= area.width || ch >= area.height) return { maximize: true }
  return { maximize: false, width: cw, height: ch }
}

function createWindow(url) {
  const size = computeSize()
  const win = new BrowserWindow({
    width: size.maximize ? MIN_W : size.width,
    height: size.maximize ? MIN_H : size.height,
    minWidth: MIN_W,
    minHeight: MIN_H,
    icon: LOGO,
    show: false, // 先隐藏，尺寸/居中确定后再显示，避免闪烁与错位
    webPreferences: {
      preload: PRELOAD,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  win.loadURL(url)

  win.once('ready-to-show', () => {
    if (!size.maximize) win.center() // 在主屏工作区内居中
    win.show()
  })

  // 开发便利：F12 打开 DevTools
  win.webContents.on('before-input-event', (event, input) => {
    if (input.key === 'F12') {
      if (win.webContents.isDevToolsOpened()) win.webContents.closeDevTools()
      else win.webContents.openDevTools()
      event.preventDefault()
    }
  })

  // 让外部链接走系统默认浏览器，不在壳内打开
  win.webContents.setWindowOpenHandler(({ url: u }) => {
    const allowed = (() => { try { return new URL(win.webContents.getURL()).host } catch (_) { return '' } })()
    try {
      const tu = new URL(u)
      if (tu.host === allowed) return { action: 'allow' }
    } catch (_) { /* ignore */ }
    require('electron').shell.openExternal(u)
    return { action: 'deny' }
  })
}

// ---------- 进程回收 ----------
function stopSidecar() {
  for (const c of children) {
    try { c.kill('SIGTERM') } catch (_) { /* ignore */ }
  }
  children = []
  killPort(PORT)
  killPort(DEV_PORT)
}

// 渲染进程文件/文件夹选择对话框桥
ipcMain.handle('bempdiff:pick-path', async (event, opts = {}) => {
  const properties = []
  if (opts.directory) properties.push('openDirectory')
  else properties.push('openFile')
  if (opts.multiple) properties.push('multiSelections')
  const win = BrowserWindow.fromWebContents(event.sender) || BrowserWindow.getFocusedWindow() || BrowserWindow.getAllWindows()[0]
  const result = await dialog.showOpenDialog(win, { properties })
  if (result.canceled || !result.filePaths.length) return null
  return opts.multiple ? result.filePaths : result.filePaths[0]
})

app.on('before-quit', stopSidecar)

app.whenReady().then(async () => {
  let initialUrl
  if (process.env.BEMPDIFF_DEV_URL) {
    // 指向外部已运行的服务（调试用），不启动本地 sidecar
    initialUrl = process.env.BEMPDIFF_DEV_URL
  } else {
    // 清理旧实例占用的端口，允许干净重启
    killPort(PORT)
    killPort(DEV_PORT)
    await startBackend()
    const viteStarted = await startFrontend()
    // 后端启动快，先用它开窗口；若 vite 会启动，则等其就绪后切换到 vite 地址（避免白屏等待）
    initialUrl = `http://127.0.0.1:${PORT}/`
    if (viteStarted) {
      waitPort(DEV_PORT, 60000).then((ok) => {
        if (ok) {
          const w = BrowserWindow.getAllWindows()[0]
          if (w && !w.isDestroyed()) w.loadURL(`http://127.0.0.1:${DEV_PORT}/`)
        }
      })
    }
  }

  createWindow(initialUrl)

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow(initialUrl)
  })
})

// 开发态：关闭所有窗口即退出（并回收 sidecar）
app.on('window-all-closed', () => {
  app.quit()
})
