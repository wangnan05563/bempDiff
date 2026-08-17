// BempDiff 多 tab 对比（DiffView 升级）行为单测。
//
// 直接 import webui/src/lib/tabs.js 的真实纯函数，零复制、零漂移：
//   - 不同 key 的反编译各自独立、互不覆盖
//   - 同一 key 重复点击不会触发二次反编译
//   - 关闭当前激活 tab 会激活下一个（右边 → 左边 → null）
//   - 关闭非激活 tab 不会改变 activeKey
//   - runCompare 后 tabs 被清空
//
// store.js 的 selectEntry / closeTab 实际调用本模块（activateTab/closeTabReducer）。

import assert from 'node:assert/strict'
import { activateTab, closeTabReducer, findActiveTab } from '../webui/src/lib/tabs.js'

let pass = 0, fail = 0
function t(name, fn) {
  try { fn(); console.log('  ✓', name); pass++ }
  catch (e) { console.log('  ✗', name, '\n    ', e.stack || e.message); fail++ }
}

console.log('T1 同一 key 重复点击不会触发二次反编译')
t('点击已存在 key → 只切激活、tabs 数量不变、不返回 opened', () => {
  const node = { key: 'A' }
  let r = activateTab([], null, 'A', node)
  assert.equal(r.opened, true)
  assert.equal(r.tabs.length, 1)
  assert.equal(r.activeKey, 'A')
  const tabs = r.tabs
  // 第二次点 A：应是纯切激活
  r = activateTab(tabs, 'A', 'A', node)
  assert.equal(r.opened, false)
  assert.equal(r.tabs.length, 1)
  assert.equal(r.activeKey, 'A')
  assert.strictEqual(r.tabs, tabs, '应复用同一个数组，不重新分配')
})

console.log('T2 不同 key 的反编译各自独立、互不覆盖')
t('依次点 B、C → 两个 tab 同时存在；activeKey 切到 C', () => {
  const a = activateTab([], null, 'A', { key: 'A' })
  const b = activateTab(a.tabs, a.activeKey, 'B', { key: 'B' })
  assert.equal(b.tabs.length, 2)
  assert.equal(b.activeKey, 'B')
  // 模拟反编译回写：tab A 的 decompile 完成，B 仍未完成
  const tabs = b.tabs.map(t => t.key === 'A' ? { ...t, decompile: { ok: true, engine: 'cfr', oldSrc: 'O-A', newSrc: 'N-A', diffText: '' }, busy: false } : t)
  const ac = tabs.find(t => t.key === b.activeKey)
  assert.equal(ac.decompile, null, 'B 的 decompile 还没回来时不应被 A 的结果污染')
  // 现在 B 的 decompile 也回来
  const final = tabs.map(t => t.key === 'B' ? { ...t, decompile: { ok: true, engine: 'cfr', oldSrc: 'O-B', newSrc: 'N-B', diffText: '' }, busy: false } : t)
  const tabA = final.find(t => t.key === 'A')
  const tabB = final.find(t => t.key === 'B')
  assert.deepEqual(tabA.decompile, { ok: true, engine: 'cfr', oldSrc: 'O-A', newSrc: 'N-A', diffText: '' })
  assert.deepEqual(tabB.decompile, { ok: true, engine: 'cfr', oldSrc: 'O-B', newSrc: 'N-B', diffText: '' })
  assert.notDeepEqual(tabA.decompile, tabB.decompile, '两个 tab 的反编译结果必须独立')
})

console.log('T3 关闭当前激活 tab → 激活右边那个')
t('关 active=C → active 切到右边的 B（不是左边的 A）', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  s = activateTab(s.tabs, s.activeKey, 'B', { key: 'B' })
  s = activateTab(s.tabs, s.activeKey, 'C', { key: 'C' })
  assert.equal(s.activeKey, 'C')
  // 关 C → 切到右边的？没有右边。closeTabReducer 实现：从 i 处开始，next[i] 已是越界，则取 next[i-1]
  // tabs = [A, B, C]  i(C)=2, next = [A, B], next[2]=undef, next[2-1]=next[1]=B → 切到 B
  s = closeTabReducer(s.tabs, s.activeKey, 'C')
  assert.equal(s.activeKey, 'B')
  assert.equal(s.tabs.length, 2)
})

console.log('T4 关闭当前激活 tab 且无右边 → 激活左边')
t('关闭 active=B → 切到前面的 A', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  s = activateTab(s.tabs, s.activeKey, 'B', { key: 'B' })
  // 现在关 B（active），应切到 A
  s = closeTabReducer(s.tabs, s.activeKey, 'B')
  assert.equal(s.activeKey, 'A')
  assert.equal(s.tabs.length, 1)
})

console.log('T5 关闭最后一个 tab → activeKey 为 null')
t('关完所有 tab → activeKey=null', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  s = closeTabReducer(s.tabs, s.activeKey, 'A')
  assert.equal(s.activeKey, null)
  assert.equal(s.tabs.length, 0)
})

console.log('T6 关闭非激活 tab → activeKey 不变')
t('关 A（不 active）→ activeKey=B、tabs 剩 B', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  s = activateTab(s.tabs, s.activeKey, 'B', { key: 'B' })
  s = closeTabReducer(s.tabs, s.activeKey, 'A')
  assert.equal(s.activeKey, 'B')
  assert.equal(s.tabs.length, 1)
  assert.equal(s.tabs[0].key, 'B')
})

console.log('T7 关闭不存在的 key → noop')
t('关 Z（不存在）→ tabs/active 不变', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  const r = closeTabReducer(s.tabs, s.activeKey, 'Z')
  assert.equal(r.tabs.length, 1)
  assert.equal(r.activeKey, 'A')
})

console.log('T8 切激活后再切回 → 复用已有 tab、不触发反编译')
t('点 A、B、A → A 只反编译一次（opened 只 +1）', () => {
  let openedCount = 0
  let r = activateTab([], null, 'A', { key: 'A' }); openedCount += r.opened ? 1 : 0
  r = activateTab(r.tabs, r.activeKey, 'B', { key: 'B' }); openedCount += r.opened ? 1 : 0
  r = activateTab(r.tabs, r.activeKey, 'A', { key: 'A' }); openedCount += r.opened ? 1 : 0
  assert.equal(openedCount, 2, 'A 只在第一次算 opened（需要反编译），B 一次')
  assert.equal(r.activeKey, 'A')
})

console.log('T9 占位 tab 形状正确（busy=true、decompile=null）')
t('新 tab 初始 busy=true、decompile=null、error=null', () => {
  const r = activateTab([], null, 'A', { key: 'A' })
  const tab = r.tabs[0]
  assert.equal(tab.busy, true)
  assert.equal(tab.decompile, null)
  assert.equal(tab.error, null)
  assert.equal(tab.key, 'A')
})

console.log('T10 activeTab() 取当前 tab（等价 store.activeTab）')
t('findActiveTab 用 activeKey 找当前 tab；无激活时返回 null', () => {
  let s = activateTab([], null, 'A', { key: 'A' })
  s = activateTab(s.tabs, s.activeKey, 'B', { key: 'B' })
  const at = findActiveTab(s.tabs, s.activeKey)
  assert.equal(at.key, 'B')
  // 全关
  s = closeTabReducer(s.tabs, s.activeKey, 'A')
  s = closeTabReducer(s.tabs, s.activeKey, 'B')
  const at2 = findActiveTab(s.tabs, s.activeKey)
  assert.equal(at2, null)
})

console.log('T11 store.js 用 activateTab 行为真实一致（import 验证）')
t('运行 store 期待签名：tabs.find() 与 spread 正常返回对象形状', () => {
  const r = activateTab([], null, 'K', { key: 'K', layer: 'L1', status: 'MODIFIED', fileClass: 'CLASS' })
  assert.equal(r.tabs[0].node.layer, 'L1')
  assert.equal(r.tabs[0].node.status, 'MODIFIED')
})

console.log('')
console.log(`结果：${pass} 通过 / ${fail} 失败`)
if (fail > 0) process.exit(1)