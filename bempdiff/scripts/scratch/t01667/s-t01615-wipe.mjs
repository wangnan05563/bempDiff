// 聚焦实验：取消「记住 API Key」→ 保存(二次确认) → 磁盘 aiApiKey 行必须被删除（T01615 核心）
import { connect } from './helpers.mjs';
import fs from 'node:fs';
const PROP = process.env.HOME + '/.bempdiff/bempdiff-web.properties';
const diskKey = () => {
  try { return fs.readFileSync(PROP, 'utf-8').split(/\r?\n/).find(l => l.startsWith('aiApiKey=')) || '(无行)'; }
  catch (e) { return 'ERR:' + e.message; }
};
const { browser, page } = await connect();
const log = (...a) => console.log('[t01615]', ...a);

if (!(await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0)))) {
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
log('实验前磁盘:', diskKey());
await page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="界面与高级"]').click();
await page.waitForTimeout(500);
const chk = page.locator('#cfgPersist');
if (await chk.isChecked()) await chk.click();
log('persist 取消勾选 =', !(await chk.isChecked()));
const saveBtn = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[contains(@class,"btn-primary")][contains(., "保存")]');
await saveBtn.click();
await page.waitForTimeout(800);
const confirmShown = await page.evaluate(() => /无法找回|仍要|误删/.test(document.body.innerText));
log('二次确认出现 =', confirmShown);
if (confirmShown) { await saveBtn.click(); await page.waitForTimeout(1600); }
log('保存后磁盘:', diskKey());
const s = await page.evaluate(async () => (await (await fetch('/api/config')).json()));
log('服务端: persistApiKey =', s.persistApiKey, '| hasApiKey =', s.hasApiKey, '(hasApiKey=true 为会话内仍持有，属设计语义)');
await browser.close();
