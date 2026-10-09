// T00862 向导引导系统 复验
// A) 默认左下角无引导按钮  B) 帮助文档→开始引导→按钮出现+引导播放  C) 关闭引导→按钮消失
// D) 开关状态重启保持（重启由外部脚本执行，本脚本输出重启前状态）
import { connect, mkdirp, shot } from './helpers.mjs';

const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00862]', ...a);
const R = {};

const btnKey = () => page.evaluate(() => localStorage.getItem('bempdiff-guide-btn'));
async function guideBtnInDom() {
  return await page.evaluate(() => {
    const btns = [...document.querySelectorAll('button')].filter(b => {
      const r = b.getBoundingClientRect();
      return r.width > 0 && r.y > window.innerHeight * 0.6 && r.x < window.innerWidth * 0.25;
    });
    return btns.map(b => ({ text: (b.innerText || '').trim().slice(0, 20), aria: b.getAttribute('aria-label') || '', title: b.getAttribute('title') || '' }));
  });
}

// ---- A) 默认状态 ----
R.defaultBtnKey = await btnKey();
R.defaultBottomLeft = await guideBtnInDom();
R.defaultNoGuideBtn = R.defaultBtnKey !== 'on' &&
  !R.defaultBottomLeft.some(b => /引导|guide/i.test(b.text + b.aria + b.title));
log('A) localStorage[guide-btn] =', JSON.stringify(R.defaultBtnKey), '| 左下角按钮:', JSON.stringify(R.defaultBottomLeft), R.defaultNoGuideBtn ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00862-A-default');

// ---- B) 帮助文档 → 开启引导 ----
if (!(await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0)))) {
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
await page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="帮助文档"]').click();
await page.waitForTimeout(600);
// 找引导开关按钮（title 来自 hd.guideTip，文本为 hd.guideOff/On）
const guideToggle = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[contains(@class,"btn")][*[contains(.,"引导")] or contains(., "引导")]');
const toggleInfo = await guideToggle.first().evaluate(b => ({ text: (b.innerText || '').trim(), title: b.getAttribute('title') || '' })).catch(() => null);
log('引导开关按钮:', JSON.stringify(toggleInfo));
await guideToggle.first().click();
await page.waitForTimeout(1200);
// 引导应立即播放：出现引导浮层
const overlayShown = await page.evaluate(() => {
  const hits = [...document.querySelectorAll('*')].filter(el => {
    const r = el.getBoundingClientRect();
    return r.width > 0 && /引导|下一步|跳过|开始使用/.test(el.innerText || '') && el.children.length < 30 && r.width < window.innerWidth * 0.6;
  });
  return hits.length > 0;
});
R.guidePlayed = overlayShown;
log('B) 引导浮层出现 =', overlayShown, overlayShown ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00862-B-guide-playing');
// 关掉引导浮层（保留按钮开关为 on）
const closeLabels = ['跳过', '关闭引导', '完成', '×'];
for (const lb of closeLabels) {
  const ok = await page.locator(`xpath=//button[normalize-space(.)="${lb}"]`).first().isVisible().catch(() => false);
  if (ok) { await page.locator(`xpath=//button[normalize-space(.)="${lb}"]`).first().click(); log('点击', lb, '关闭浮层'); break; }
}
await page.waitForTimeout(700);
// 左下角引导按钮应出现
R.btnAfterEnable = await guideBtnInDom();
R.bottomLeftBtnOn = R.btnAfterEnable.some(b => /引导|guide/i.test(b.text + b.aria + b.title)) || (await btnKey()) === 'on';
log('B2) 开启后左下角 =', JSON.stringify(R.btnAfterEnable), '| key =', await btnKey(), R.bottomLeftBtnOn ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00862-B2-btn-on');

// ---- C) 再点开关（关闭引导按钮）→ 按钮消失 ----
// 弹窗还开着：再点一次开关 = 关闭引导按钮
await guideToggle.first().click();
await page.waitForTimeout(800);
R.btnAfterDisable = await guideBtnInDom();
R.bottomLeftBtnOff = !R.btnAfterDisable.some(b => /引导|guide/i.test(b.text + b.aria + b.title)) && (await btnKey()) !== 'on';
log('C) 关闭后左下角 =', JSON.stringify(R.btnAfterDisable), '| key =', await btnKey(), R.bottomLeftBtnOff ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00862-C-btn-off');

// ---- 收尾：为 D 步（重启验证）把开关置回 on，再由外部重启 ----
await guideToggle.first().click();
await page.waitForTimeout(600);
R.keyForRestart = await btnKey();
log('D-准备) 重启前 key =', R.keyForRestart, '(应为 on)');

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
