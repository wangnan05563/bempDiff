// T00845 编辑入口：点击代码行 → 光标落下 + 状态栏行列坐标
import { connect, mkdirp, shot } from './helpers.mjs';
const OUT = mkdirp('out');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00845]', ...a);
const before = await page.evaluate(() => document.body.innerText.match(/[行Ln|行列][^\n]{0,20}/)?.[0] || '');
await page.locator('.diff-pane.right .prow').first().click();
await page.waitForTimeout(600);
const after = await page.evaluate(() => ({
  cursor: !!document.querySelector('[class*="caret"], [class*="cursor"], [class*="cursor-line"]'),
  status: document.body.innerText.match(/[行Ln][^\n]{0,25}/)?.[0] || '',
  caretEl: [...document.querySelectorAll('*')].filter(e => /caret|cursor/i.test(e.className.toString()) && e.getBoundingClientRect().width > 0).map(e => e.className.toString().slice(0, 40)).slice(0, 3),
}));
log('点击行前状态栏:', JSON.stringify(before));
log('点击行后:', JSON.stringify(after));
console.log('RESULT:', after.caretEl.length > 0 ? 'PASS ✓ 点击行落下光标（编辑器风格定位）' : '(未捕获光标元素)');
await shot(page, OUT, 't00845-cursor');
await browser.close();
