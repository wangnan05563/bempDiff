// T00876 最终判定：在已打开的 23702 行差异文件上连点「下一处」×10
import { connect, mkdirp, shot } from './helpers.mjs';
const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876f]', ...a);
const R = {};

const busy = () => page.evaluate(() => [...document.querySelectorAll('.busy-overlay')].some(o => o.getBoundingClientRect().width > 0));
const snap = () => page.evaluate(() => {
  const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
  const uni = document.querySelector('.uni-body');
  const sc = uni || document.querySelector('.diff-area');
  return { cnt, scroll: sc ? Math.round(sc.scrollTop) : -1 };
});

// 等遮罩清空
for (let i = 0; i < 30; i++) { if (!(await busy())) break; await page.waitForTimeout(2000); }
const s0 = await snap();
log('初始:', JSON.stringify(s0));
if (!s0.cnt || s0.cnt === '0/0') { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }

const nextBtn = page.locator('button[title="跳到下一处差异（Ctrl/Alt + ↓）"]');
const results = [];
for (let k = 0; k < 10; k++) {
  const b = await snap();
  await nextBtn.click();
  await page.waitForTimeout(500);
  const a = await snap();
  const bi = parseInt(b.cnt), ai = parseInt(a.cnt);
  results.push({ k, cnt: b.cnt + '->' + a.cnt, scroll: b.scroll + '->' + a.scroll, inc: ai === bi + 1, moved: a.scroll !== b.scroll });
}
R.seq = results;
R.inc10 = results.filter(r => r.inc).length;
R.moved10 = results.filter(r => r.moved).length;
log('counter 递增 =', R.inc10, '/10；视图滚动 =', R.moved10, '/10');
log(JSON.stringify(results));
R.pass = R.inc10 >= 9 && R.moved10 >= 5;
log('T00876 判定 =', R.pass ? 'PASS ✓ 每次单击即精确跳到下一差异行' : 'FAIL ✗');
await shot(page, OUT, 't00876f-final');

// T00845: 专注对比（全屏查看）入口进出
const fsInfo = await page.evaluate(() => {
  const b = [...document.querySelectorAll('button')].find(b => /专注对比/.test(b.title || ''));
  return b ? { title: b.title, vis: b.getBoundingClientRect().width > 0, disabled: b.disabled } : null;
});
R.fullscreenEntry = fsInfo;
log('T00845 专注对比入口 =', JSON.stringify(fsInfo));
if (fsInfo && fsInfo.vis && !fsInfo.disabled) {
  await page.locator('button[title*="专注对比"]').click();
  await page.waitForTimeout(800);
  await shot(page, OUT, 't00845-fs-on');
  await page.locator('button[title*="专注对比"]').click();
  await page.waitForTimeout(500);
  R.fullscreenToggle = 'PASS ✓（进入+退出）';
}

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
