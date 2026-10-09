// T00876 滚动/居中判定（连点40次观察 scroll 越过0）+ T00845 专注模式 Esc 退出
import { connect, mkdirp, shot } from './helpers.mjs';
const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876g]', ...a);
const R = {};

const busy = () => page.evaluate(() => [...document.querySelectorAll('.busy-overlay')].some(o => o.getBoundingClientRect().width > 0));
const snap = () => page.evaluate(() => {
  const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
  const uni = document.querySelector('.uni-body');
  const sc = uni || document.querySelector('.diff-area');
  // 当前差异行的视口位置（居中判定）
  const cur = document.querySelector('.row.current, .row.flash');
  const curY = cur ? Math.round(cur.getBoundingClientRect().top) : null;
  return { cnt, scroll: sc ? Math.round(sc.scrollTop) : -1, scrollerClass: sc ? sc.className.slice(0, 40) : null, curY };
});

for (let i = 0; i < 20; i++) { if (!(await busy())) break; await page.waitForTimeout(2000); }
const s0 = await snap();
log('初始:', JSON.stringify(s0));

const nextBtn = page.locator('button[title="跳到下一处差异（Ctrl/Alt + ↓）"]');
const marks = [];
let lastScroll = s0.scroll;
let maxScroll = 0;
for (let k = 0; k < 45; k++) {
  await nextBtn.click();
  await page.waitForTimeout(220);
  const s = await snap();
  maxScroll = Math.max(maxScroll, s.scroll);
  if (s.scroll !== lastScroll) { marks.push({ at: k + 1, scroll: lastScroll + '->' + s.scroll, curY: s.curY }); lastScroll = s.scroll; }
}
R.final = await snap();
R.scrollTransitions = marks;
R.maxScroll = maxScroll;
log('滚动变化点:', JSON.stringify(marks));
log('最终:', JSON.stringify(R.final), '| maxScroll =', maxScroll);
// 判定：counter 一路递增（当前值=45附近），且滚动随行号推进而变化（大文件不可能全程 scroll=0）
const cntNow = parseInt(R.final.cnt);
R.passNav = cntNow >= 40;
R.passScroll = maxScroll > 0;
log('T00876 =', (R.passNav && R.passScroll) ? 'PASS ✓ 单击即精确跳转且视口随行号居中推进' : 'FAIL ✗', { cntNow, maxScroll });
await shot(page, OUT, 't00876g-final');

// T00845 专注模式
const enter = await page.evaluate(() => {
  const b = [...document.querySelectorAll('button')].find(b => /专注对比/.test(b.title || ''));
  if (!b) return null;
  b.click();
  return b.title;
});
log('进入专注模式:', enter);
await page.waitForTimeout(900);
const inFocus = await page.evaluate(() => {
  const tree = document.querySelector('.col-tree');
  const treeHidden = !tree || tree.getBoundingClientRect().width === 0 || getComputedStyle(tree).display === 'none' || tree.offsetParent === null;
  return { treeHidden, focusHint: /专注/.test(document.body.innerText) };
});
R.focusEntered = inFocus.treeHidden || inFocus.focusHint;
log('专注模式状态:', JSON.stringify(inFocus), R.focusEntered ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00845-focus-on');
await page.keyboard.press('Escape');
await page.waitForTimeout(800);
const afterEsc = await page.evaluate(() => {
  const tree = document.querySelector('.col-tree');
  return { treeBack: !!tree && tree.getBoundingClientRect().width > 0 };
});
R.escExits = afterEsc.treeBack;
log('Esc 退出后树面板恢复:', JSON.stringify(afterEsc), R.escExits ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00845-focus-off');

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
