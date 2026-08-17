// BempDiff 桌面壳桥接（Electron / Tauri）路由单测。
// 直接 import 真实模块 webui/src/lib/tauri.js，mock window 全局。
// 运行：node bempdiff/scripts/test_desktop_picker.mjs
import { isElectron, isTauri, pickPath } from '../webui/src/lib/tauri.js'

let pass = 0, fail = 0
function check(name, cond) {
  if (cond) pass++
  else { fail++; console.log('  FAIL:', name) }
}
function deepEq(a, b) {
  if (a === b) return true
  if (typeof a !== typeof b) return false
  if (Array.isArray(a)) return a.length === b.length && a.every((x, i) => deepEq(x, b[i]))
  if (a && typeof a === 'object') {
    return Object.keys(a).every(k => deepEq(a[k], b[k])) && Object.keys(b).every(k => k in a)
  }
  return false
}

// 提供 window 全局（Node 里没有），便于测试探测函数读 typeof window !== 'undefined'。
globalThis.window = {}

// 工具：清空 window.bempdiff / window.__TAURI* 桥
function resetBridges() {
  try { delete globalThis.window.bempdiff } catch { globalThis.window.bempdiff = undefined }
  try { delete globalThis.window.__TAURI__ } catch { globalThis.window.__TAURI__ = undefined }
  try { delete globalThis.window.__TAURI_INTERNALS__ } catch { globalThis.window.__TAURI_INTERNALS__ = undefined }
}

// 集中执行，避免异步检查漂移
;(async () => {
  // ── A2/A3 isElectron 探测 ───────────────────────────────────────
  resetBridges()
  check('A2 无 window.bempdiff → isElectron=false', isElectron() === false)

  globalThis.window.bempdiff = {}
  check('A3 window.bempdiff 无 pickPath 方法 → isElectron=false', isElectron() === false)

  globalThis.window.bempdiff = { pickPath: () => {} }
  check('A1 window.bempdiff.pickPath 是函数 → isElectron=true', isElectron() === true)

  // ── A4/A5 isTauri 探测 ───────────────────────────────────────────
  resetBridges()
  check('A5 浏览器场景无 __TAURI_INTERNALS__ → isTauri=false', isTauri() === false)
  globalThis.window.__TAURI_INTERNALS__ = {}
  check('A4 有 __TAURI_INTERNALS__ → isTauri=true', isTauri() === true)

  // ── A6 浏览器（无桌面壳）→ pickPath 返回 null ───────────────────
  resetBridges()
  const r0 = await pickPath({})
  check('A6 浏览器场景 pickPath 返回 null', r0 === null)

  // ── A7 Electron 路由 + 单选参数透传 ─────────────────────────────
  resetBridges()
  let receivedA7 = null
  globalThis.window.bempdiff = { pickPath: async (opts) => { receivedA7 = opts; return '/abs/file.war' } }
  const r1 = await pickPath({ directory: true, multiple: false })
  check('A7 Electron 路由返回桥接值', r1 === '/abs/file.war')
  check('A7 Electron 路由透传 opts(directory:true, multiple:false)', deepEq(receivedA7, { directory: true, multiple: false }))

  // ── A8 Electron 多选 ────────────────────────────────────────────
  resetBridges()
  let receivedA8 = null
  globalThis.window.bempdiff = { pickPath: async (opts) => { receivedA8 = opts; return ['/a.war', '/b.war'] } }
  const r2 = await pickPath({ multiple: true })
  check('A8 Electron 多选透传 opts', deepEq(receivedA8, { directory: false, multiple: true }))
  check('A8 Electron 多选返回数组', Array.isArray(r2) && r2.length === 2)

  // ── A10 Electron 优先于 Tauri（同存时走 Electron） ─────────────
  resetBridges()
  globalThis.window.bempdiff = { pickPath: async () => 'ELECTRON' }
  globalThis.window.__TAURI__ = { dialog: { open: async () => 'TAURI' } }
  globalThis.window.__TAURI_INTERNALS__ = {}
  const r3 = await pickPath({})
  check('A10 Electron + Tauri 共存时优先走 Electron', r3 === 'ELECTRON')

  // ── A9 Tauri-only 路由 ──────────────────────────────────────────
  resetBridges()
  let receivedA9 = null
  globalThis.window.__TAURI__ = { dialog: { open: async (opts) => { receivedA9 = opts; return '/tauri/sel.war' } } }
  globalThis.window.__TAURI_INTERNALS__ = {}
  const r4 = await pickPath({ directory: false, multiple: true })
  check('A9 Tauri-only 走 __TAURI__.dialog.open', r4 === '/tauri/sel.war')
  check('A9 Tauri-only 透传 opts', deepEq(receivedA9, { directory: false, multiple: true }))

  // ── A11 Electron 桥接抛错 → pickPath 返回 null，不向上抛 ───────────
  resetBridges()
  globalThis.window.bempdiff = { pickPath: async () => { throw new Error('user cancelled modal') } }
  let caught = null
  const r5 = await pickPath({}).catch(e => { caught = e; return 'thrown' })
  check('A11 Electron 桥接抛错被捕获、pickPath 返回 null', r5 === null && caught === null)

  // ── A12 Electron 桥接返回 null（取消）→ 不再 fall-through 到 Tauri ─
  resetBridges()
  globalThis.window.bempdiff = { pickPath: async () => null }
  globalThis.window.__TAURI__ = { dialog: { open: async () => 'TAURI' } }
  globalThis.window.__TAURI_INTERNALS__ = {}
  const r6 = await pickPath({})
  check('A12 Electron 返回 null 时不再 fall-through Tauri', r6 === null)

  console.log(`desktop picker: PASS=${pass} FAIL=${fail} (total ${pass + fail})`)
  process.exit(fail ? 1 : 0)
})().catch(e => {
  console.log('UNCAUGHT in test runner:', e)
  process.exit(2)
})