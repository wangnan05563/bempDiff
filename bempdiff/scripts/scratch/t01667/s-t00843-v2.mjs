// T00843 v2 —— 受控实验：明确验证 模态开关 / 草稿写入 / 保存到服务端 三个环节
import { connect, mkdirp, shot, cfgOpen } from './helpers.mjs';

const OUT = mkdirp('out');
const TEMP = '198.51.100.7:3128';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00843v2]', ...a);
const R = {};

const baseUrlInput = page.locator('xpath=//label[contains(., "Base URL")]/following::input[1]');
const btnSave = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[contains(@class,"btn-primary")][contains(., "保存")]');
const btnClose = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="关闭"]');

async function modalOpen() { return await cfgOpen(page); }
async function draft() {
  return await page.evaluate(() => localStorage.getItem('bempdiff:config:draft'));
}

// ---- 0: 初始状态（若上次实验遗留弹层，先关闭） ----
log('初始 modal open =', await modalOpen());
if (await modalOpen()) {
  await btnClose.click();
  await page.waitForTimeout(700);
  log('关闭遗留后 modal open =', await modalOpen());
}
log('初始 draft =', (await draft()) || '(空)');

// ---- 1: 打开，fill Base URL，关闭（不保存） ----
await page.getByTitle(/打开配置中心/).click();
await page.waitForTimeout(900);
log('打开后 modal open =', await modalOpen());
const v0 = await baseUrlInput.inputValue();
log('Base URL 初始值 =', JSON.stringify(v0));
await baseUrlInput.fill(TEMP);
await shot(page, OUT, 'v2-1-filled');
await btnClose.click();
await page.waitForTimeout(700);
R.modalClosedAfterClose = !(await modalOpen());
log('点关闭后 modal 真正关闭 =', R.modalClosedAfterClose);
const d1 = await draft();
R.draftWritten = !!(d1 && d1.includes(TEMP));
log('关闭后 draft 含临时值 =', R.draftWritten, '|', (d1 || '').slice(0, 120));

// ---- 2: 重开 → 草稿应恢复 ----
await page.getByTitle(/打开配置中心/).click();
await page.waitForTimeout(900);
const v1 = await baseUrlInput.inputValue();
R.draftRestoredOnReopen = (v1 === TEMP);
log('重开后 Base URL =', JSON.stringify(v1), R.draftRestoredOnReopen ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 'v2-2-reopened');

// ---- 3: 保存 → 服务端应变 ----
await btnSave.click();
await page.waitForTimeout(1800);
const serverNow = await page.evaluate(async () => (await (await fetch('/api/config')).json()).aiBaseUrl);
R.saveReachedServer = (serverNow === TEMP);
log('保存后服务端 aiBaseUrl =', JSON.stringify(serverNow), R.saveReachedServer ? 'PASS ✓' : 'FAIL ✗');
const d2 = await draft();
log('保存后 draft 应清空 =', JSON.stringify(d2), d2 === null ? 'PASS ✓' : '(未清)');

// ---- 4: 关闭再开 → 应显示服务端真值 ----
await btnClose.click();
await page.waitForTimeout(700);
await page.getByTitle(/打开配置中心/).click();
await page.waitForTimeout(900);
const v2 = await baseUrlInput.inputValue();
R.noStaleDraft = (v2 === TEMP);
log('保存后重开 Base URL =', JSON.stringify(v2), R.noStaleDraft ? 'PASS ✓' : 'FAIL ✗');

// ---- 5: 还原服务端 aiBaseUrl（走 UI 正常保存路径） ----
await baseUrlInput.fill(v0);
await btnSave.click();
await page.waitForTimeout(1800);
const restored = await page.evaluate(async () => (await (await fetch('/api/config')).json()).aiBaseUrl);
R.serverRestored = (restored === v0);
log('还原后服务端 aiBaseUrl =', JSON.stringify(restored), R.serverRestored ? 'PASS ✓' : 'FAIL ✗');
await btnClose.click();
await page.waitForTimeout(500);

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
