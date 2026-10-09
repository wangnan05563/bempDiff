// T00843 配置表单断点续录 复验
// A) 改字段不保存关闭→重开恢复  B) 保存后重开→真值生效且无旧草稿遮蔽  C) 结束还原
import { connect, mkdirp, shot, cfgEnsureOpen, cfgClick, cfgInputByLabel, saveProxy } from './helpers.mjs';

const OUT = mkdirp('out');
const TEMP_PROXY = '198.51.100.7:3128';
const browser = await connect();
const page = browser.page;
const log = (...a) => console.log('[t00843]', ...a);
const R = {};

await cfgEnsureOpen(page);

// ---- A: 不保存直接关闭 ----
const orig = await cfgInputByLabel(page, /HTTP\s*代理/); // 读现值
log('原始 httpProxy =', JSON.stringify(orig));
await cfgInputByLabel(page, /HTTP\s*代理/, TEMP_PROXY);
await shot(page, OUT, 't00843-A1-typed');
await cfgClick(page, (t, a, c) => t === '关闭');           // 不保存关闭
await page.waitForTimeout(600);
await cfgEnsureOpen(page);
const afterReopen = await cfgInputByLabel(page, /HTTP\s*代理/);
R.A_draftRestored = (afterReopen === TEMP_PROXY);
log('A) 重开后 =', JSON.stringify(afterReopen), R.A_draftRestored ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00843-A2-reopened');

// ---- B: 保存 → 重开应显示真值（无旧草稿遮蔽） ----
const saved = await saveProxy(page, TEMP_PROXY);
log('点击保存:', saved);
await shot(page, OUT, 't00843-B1-after-save');
// 保存后可能出现的确认/提示层截图留证
const overlay = await page.evaluate(() => {
  const bds = [...document.querySelectorAll('.modal-backdrop, .toast, [class*="confirm"]')]
    .filter(b => b.getBoundingClientRect().width > 0);
  return bds.map(b => (b.innerText || '').trim().slice(0, 100)).filter(Boolean);
});
if (overlay.length) log('保存后弹层:', JSON.stringify(overlay));

await cfgClick(page, (t, a, c) => t === '关闭');
await page.waitForTimeout(600);
await cfgEnsureOpen(page);
const afterSave = await cfgInputByLabel(page, /HTTP\s*代理/);
R.B_savedValueShown = (afterSave === TEMP_PROXY);
log('B) 保存重开后 =', JSON.stringify(afterSave), R.B_savedValueShown ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00843-B2-reopened');

// ---- C: 还原为原始空值并保存 ----
await saveProxy(page, orig === 'NOT_FOUND' ? '' : (orig ?? ''));
await cfgClick(page, (t, a, c) => t === '关闭');
await page.waitForTimeout(500);
const verify = await page.evaluate(async () => {
  const r = await fetch('/api/config');
  const d = await r.json();
  return d.httpProxy;
});
R.C_restored = (verify === '');
log('C) 服务端 httpProxy 还原 =', JSON.stringify(verify), R.C_restored ? 'PASS ✓' : 'FAIL ✗');

console.log('SUMMARY:', JSON.stringify(R));
await browser.close();
