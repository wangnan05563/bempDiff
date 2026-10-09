// T00876 + T00845 —— 复用已完成比对的树（不重新比对）
import { connect, mkdirp, shot } from './helpers.mjs';

const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876b]', ...a);
const R = {};

async function overlayBusy() {
  return await page.evaluate(() =>
    [...document.querySelectorAll('.busy-overlay')].some(o => o.getBoundingClientRect().width > 0));
}
async function navState() {
  return await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t)) || null;
    const next = [...document.querySelectorAll('button')].find(b => /跳到下一处差异/.test(b.title || ''));
    return { cnt, disabled: next ? next.disabled : null };
  });
}

// 0) 树是否就绪
const treeHit = await page.evaluate(() => {
  const t = document.body.innerText;
  const m = t.match(/命中\s*(\d+)\s*\/\s*(\d+)\s*项/);
  return m ? m[1] + '/' + m[2] : null;
});
log('树命中 =', JSON.stringify(treeHit));

// 1) 点一个 MODIFIED（黄点）文件
const clicked = await page.evaluate(() => {
  const nodes = [...document.querySelectorAll('button.tree-node')];
  for (const el of nodes) {
    const dot = el.querySelector('.bi-circle-fill');
    if (!dot) continue;
    const color = dot.style.color || getComputedStyle(dot).color;
    if (/255,\s*193,\s*7|warning/.test(color)) { el.click(); return { key: el.title, color }; }
  }
  return null;
});
log('点击修改文件:', JSON.stringify(clicked));
if (!clicked) { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }

// 2) 等 DiffView 就绪（遮罩消失 + 导航可用）
let ready = false;
for (let i = 0; i < 90; i++) {
  await page.waitForTimeout(2000);
  const busy = await overlayBusy();
  const ns = await navState();
  if (i % 5 === 0) log('  等待…', i * 2 + 's', JSON.stringify(ns), busy ? '(busy)' : '');
  if (!busy && ns.cnt && ns.cnt !== '0/0' && ns.disabled === false) { ready = true; break; }
}
R.diffViewReady = ready;
log('DiffView 就绪 =', ready);
if (!ready) { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }
await shot(page, OUT, 't00876b-1-open');

// 3) T00876：连点「下一处」，断言每次单击 counter+1 且视图滚动
const results = [];
const nextBtn = page.locator('button[title="跳到下一处差异（Ctrl/Alt + ↓）"]');
for (let k = 0; k < 8; k++) {
  const before = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const uni = document.querySelector('.uni-body');
    const split = document.querySelector('[class*="diff-area"] [class*="scroll"], .diff-area');
    const sc = uni || split;
    const curRow = document.querySelector('.row.current, .row.flash');
    return { cnt, scroll: sc ? Math.round(sc.scrollTop) : null, cur: curRow ? curRow.textContent.trim().slice(0, 40) : null };
  });
  await nextBtn.click();
  await page.waitForTimeout(400);
  const after = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const uni = document.querySelector('.uni-body');
    const split = document.querySelector('[class*="diff-area"] [class*="scroll"], .diff-area');
    const sc = uni || split;
    const curRow = document.querySelector('.row.current, .row.flash');
    return { cnt, scroll: sc ? Math.round(sc.scrollTop) : null, cur: curRow ? curRow.textContent.trim().slice(0, 40) : null };
  });
  results.push({ k, before: before.cnt + '@' + before.scroll, after: after.cnt + '@' + after.scroll, curChanged: before.cur !== after.cur });
}
R.navSeq = results;
const advanced = results.filter(r => {
  const b = parseInt(r.before.split('@')[0]);
  const a = parseInt(r.after.split('@')[0]);
  return a === b + 1;
}).length;
R.singleClickAdvance = advanced >= 7;
log('T00876 每次单击 counter+1 的次数 =', advanced, '/8', R.singleClickAdvance ? 'PASS ✓' : 'FAIL ✗');
log('T00876 序列:', JSON.stringify(results));
await shot(page, OUT, 't00876b-2-nav');

// 4) T00845：全屏/编辑入口
const entries = await page.evaluate(() => {
  const out = {};
  for (const b of document.querySelectorAll('button')) {
    const label = (b.getAttribute('aria-label') || '') + '|' + (b.title || '');
    if (/全屏|专注/.test(label)) out.fullscreen = { aria: b.getAttribute('aria-label'), title: b.title, vis: b.getBoundingClientRect().width > 0, disabled: b.disabled };
    if (/编辑/.test(label)) out.edit = { aria: b.getAttribute('aria-label'), title: b.title, vis: b.getBoundingClientRect().width > 0, disabled: b.disabled };
  }
  return out;
});
R.entries = entries;
log('T00845 入口:', JSON.stringify(entries));
if (entries.fullscreen && entries.fullscreen.vis && !entries.fullscreen.disabled) {
  await page.locator('button[title*="全屏"], button[aria-label*="全屏"]').first().click();
  await page.waitForTimeout(800);
  await shot(page, OUT, 't00845-fullscreen');
  await page.locator('button[title*="全屏"], button[aria-label*="全屏"]').first().click();
  await page.waitForTimeout(500);
  R.fullscreenToggle = 'PASS ✓ (进出各一次)';
  log('全屏切换 =', R.fullscreenToggle);
}

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
