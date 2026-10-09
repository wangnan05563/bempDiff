// T00876 大差异文件（12000行/80处修改）目录模式 复验
import { connect, mkdirp, shot } from './helpers.mjs';

const OUT = mkdirp('out');
const V1 = 'D:\\code\\otherProjects\\18_comparePakage\\bempdiff\\scripts\\scratch\\t01667\\fx-big\\v1';
const V2 = 'D:\\code\\otherProjects\\18_comparePakage\\bempdiff\\scripts\\scratch\\t01667\\fx-big\\v2';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876c]', ...a);
const R = {};

async function overlayBusy() {
  return await page.evaluate(() => [...document.querySelectorAll('.busy-overlay')].some(o => o.getBoundingClientRect().width > 0));
}

// 1) 切目录模式
await page.getByTitle(/以解压后的目录作为输入/).click();
await page.waitForTimeout(600);
log('已切目录模式');

// 2) 填路径
async function setPath(idx, val) {
  const bars = page.locator('.pbc');
  const bar = bars.nth(idx);
  await bar.evaluate(el => { if (el.classList.contains('pbc-disabled')) throw new Error('disabled'); el.click(); });
  await page.waitForTimeout(300);
  const inp = bar.locator('input.pb-input');
  await inp.waitFor({ state: 'visible', timeout: 5000 });
  await inp.fill(val);
  await inp.press('Enter');
  await page.waitForTimeout(400);
}
await setPath(0, V1);
await setPath(1, V2);
const shown = await page.evaluate(() => [...document.querySelectorAll('.pbc')].map(p => (p.title || '').slice(0, 70)));
log('路径 =', JSON.stringify(shown));

// 3) 比对
await page.getByTitle(/加载两个包\/目录/).click();
for (let i = 0; i < 60; i++) {
  await page.waitForTimeout(2000);
  const busy = await overlayBusy();
  const hit = await page.evaluate(() => { const m = document.body.innerText.match(/命中\s*(\d+)\s*\/\s*(\d+)\s*项/); return m ? m[1] : null; });
  if (i % 5 === 0) log('  比对中…', i * 2 + 's', 'hit=', hit, busy ? '(busy)' : '');
  if (!busy && hit && hit !== '0') break;
}
await shot(page, OUT, 't00876c-1-tree');

// 4) 点 Big.java（黄点）
const clicked = await page.evaluate(() => {
  for (const el of document.querySelectorAll('button.tree-node')) {
    if (/Big\.java/.test(el.title || '')) { el.click(); return el.title; }
  }
  return null;
});
log('点击 Big.java:', JSON.stringify(clicked));
if (!clicked) { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }

let ready = false;
for (let i = 0; i < 60; i++) {
  await page.waitForTimeout(2000);
  const busy = await overlayBusy();
  const ns = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t)) || null;
    const next = [...document.querySelectorAll('button')].find(b => /跳到下一处差异/.test(b.title || ''));
    return { cnt, disabled: next ? next.disabled : null };
  });
  if (i % 5 === 0) log('  DiffView…', i * 2 + 's', JSON.stringify(ns), busy ? '(busy)' : '');
  if (!busy && ns.cnt && ns.disabled === false && parseInt(ns.cnt) >= 50) { ready = true; log('就绪 counter =', ns.cnt); break; }
}
R.ready = ready;
if (!ready) { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }
await shot(page, OUT, 't00876c-2-open');

// 5) 连点下一处 ×10
const nextBtn = page.locator('button[title="跳到下一处差异（Ctrl/Alt + ↓）"]');
const results = [];
for (let k = 0; k < 10; k++) {
  const snap = () => page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const uni = document.querySelector('.uni-body');
    const sc = uni || document.querySelector('.diff-area');
    return { cnt, scroll: sc ? Math.round(sc.scrollTop) : -1 };
  });
  const b = await snap();
  await nextBtn.click();
  await page.waitForTimeout(450);
  const a = await snap();
  const bi = parseInt(b.cnt), ai = parseInt(a.cnt);
  results.push({ k, cnt: b.cnt + '->' + a.cnt, sc: b.scroll + '->' + a.scroll, inc: ai === bi + 1, moved: a.scroll !== b.scroll });
}
R.seq = results;
const inc = results.filter(r => r.inc).length;
const moved = results.filter(r => r.moved).length;
R.inc10 = inc; R.moved10 = moved;
log('T00876 counter 递增次数 =', inc, '/10；视图滚动变化 =', moved, '/10');
log('序列:', JSON.stringify(results));
R.pass = inc >= 9 && moved >= 5;
log('T00876 判定 =', R.pass ? 'PASS ✓ 单击即精确跳到下一差异行' : 'FAIL ✗');
await shot(page, OUT, 't00876c-3-nav');

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
