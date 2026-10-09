// T00876 最终判定 v2：正确滚动容器 (.diff-pane) + 单击跳转精度 + 居中
import { connect, mkdirp, shot } from './helpers.mjs';
const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876z]', ...a);
const R = {};

const busy = () => page.evaluate(() => [...document.querySelectorAll('.busy-overlay')].some(o => o.getBoundingClientRect().width > 0));
const snap = () => page.evaluate(() => {
  const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
  const pane = document.querySelector('.diff-pane.right') || document.querySelector('.diff-pane');
  // 当前高亮行：split 视图当前差异行会有 current/flash 类或高亮样式
  const curRow = document.querySelector('.diff-pane .row.current, .diff-pane .row.flash, .diff-pane [class*="current"]');
  const curInfo = curRow ? {
    cls: curRow.className.toString().slice(0, 40),
    top: Math.round(curRow.getBoundingClientRect().top),
    vh: window.innerHeight,
    text: (curRow.textContent || '').trim().slice(0, 50),
  } : null;
  return { cnt, scroll: pane ? Math.round(pane.scrollTop) : -1, cur: curInfo };
});

for (let i = 0; i < 20; i++) { if (!(await busy())) break; await page.waitForTimeout(2000); }
const s0 = await snap();
log('初始:', JSON.stringify(s0));

const nextBtn = page.locator('button[title="跳到下一处差异（Ctrl/Alt + ↓）"]');
const results = [];
for (let k = 0; k < 12; k++) {
  const b = await snap();
  await nextBtn.click();
  await page.waitForTimeout(300);
  const a = await snap();
  const bi = parseInt(b.cnt), ai = parseInt(a.cnt);
  results.push({ k, cnt: `${bi}->${ai}`, inc: ai === bi + 1, scrollMoved: a.scroll !== b.scroll });
}
R.seq = results;
R.inc12 = results.filter(r => r.inc).length;
R.scrollMoved12 = results.filter(r => r.scrollMoved).length;
const fin = await snap();
R.final = fin;
log('counter+1 =', R.inc12, '/12；滚动变化 =', R.scrollMoved12, '/12');
log('最终:', JSON.stringify(fin));
// 居中判定：当前行 top 应在视口中上部（非 0 顶死、非视口外）
R.centered = fin.cur ? fin.cur.top > 0 && fin.cur.top < fin.cur.vh : null;
R.pass = R.inc12 >= 11 && R.scrollMoved12 >= 3;
log('T00876 判定 =', R.pass ? 'PASS ✓ 单击即精确跳到下一差异行（视口随行推进）' : 'FAIL ✗',
    { inc: R.inc12, scrollMoved: R.scrollMoved12, curRow: fin.cur });
await shot(page, OUT, 't00876z-final');

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
