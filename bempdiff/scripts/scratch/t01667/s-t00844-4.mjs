// T00844 第 4 项修正复验 v2 + 假 Key 清理（走「清除密钥」语义：取消记住 + 二次确认保存）
import { connect, mkdirp, shot } from './helpers.mjs';
import fs from 'node:fs';

const OUT = mkdirp('out');
const FAKE = 'sk-fake-key-abc123';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00844-4]', ...a);
const R = {};

const btnSave = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[contains(@class,"btn-primary")][contains(., "保存")]');
const btnClose = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="关闭"]');

async function openCfg() {
  if (await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0 && /Base URL|导入|帮助/.test(b.innerText || '')))) return;
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
async function gotoTab(name) {
  await page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="' + name + '"]').click();
  await page.waitForTimeout(500);
}
async function serverCfg() {
  return await page.evaluate(async () => (await (await fetch('/api/config')).json()));
}
async function captureExport() {
  return await page.evaluate(async () => {
    const orig = URL.createObjectURL.bind(URL);
    let captured = null;
    URL.createObjectURL = (blob) => { captured = blob; return orig(blob); };
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
    const root = bds[bds.length - 1];
    const btn = [...root.querySelectorAll('button')].find(b => /导出配置/.test(b.innerText || ''));
    if (!btn) { URL.createObjectURL = orig; return 'BTN_NOT_FOUND'; }
    btn.click();
    await new Promise(r => setTimeout(r, 700));
    URL.createObjectURL = orig;
    return captured ? await captured.text() : 'NO_BLOB';
  });
}

await openCfg();
let s0 = await serverCfg();
log('0) 清理前: hasApiKey =', s0.hasApiKey, '| persistApiKey =', s0.persistApiKey);

// 切到 界面与高级 tab，取消「记住 API Key」
await gotoTab('界面与高级');
const chkPersist = page.locator('#cfgPersist');
await chkPersist.waitFor({ state: 'visible', timeout: 8000 });
if (await chkPersist.isChecked()) { await chkPersist.click(); }
log('取消记住后 checked =', await chkPersist.isChecked());

// 回 AI 服务 tab 填假 Key（或留在本 tab，Key 输入框在 AI tab）
await gotoTab('AI 服务');
const keyInput = page.getByRole('textbox', { name: /模型服务提供的访问密钥/ });
await keyInput.fill(FAKE);

// 第一次保存 → 应弹二次确认（T01615：persist=false 且磁盘有 Key → wipeRisk）
await btnSave.click();
await page.waitForTimeout(900);
const bodyTxt = await page.evaluate(() => document.body.innerText);
const confirmShown = /无法找回|仍要|二次|误删/.test(bodyTxt);
log('T01615 二次确认出现 =', confirmShown, confirmShown ? 'PASS ✓' : '(若磁盘无Key则不应出现)');
if (confirmShown) {
  await shot(page, OUT, 't00844-4b-confirm');
  await btnSave.click();
  await page.waitForTimeout(1600);
}
const s1 = await serverCfg();
R.persistOff = s1.persistApiKey === false;
log('保存后 persistApiKey =', s1.persistApiKey, R.persistOff ? 'PASS ✓' : 'FAIL ✗');
R.wiped = !s1.hasApiKey;
log('磁盘密钥已清除 hasApiKey =', s1.hasApiKey, R.wiped ? 'PASS ✓' : 'FAIL ✗');

const exp = await captureExport();
let noPlain = null, field = '-';
if (exp.startsWith('{')) {
  const j = JSON.parse(exp);
  field = JSON.stringify(j.config?.aiApiKey ?? null);
  noPlain = !exp.includes(FAKE);
} else { log('导出失败:', exp); }
R.exportNoPlainKey = noPlain;
log('4) 导出不含明文假 Key =', noPlain, '| aiApiKey 字段 =', field, noPlain ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00844-4c-after');

// 还原 persist=true（假 Key 已清）
await gotoTab('界面与高级');
const chk2 = page.locator('#cfgPersist');
if (!(await chk2.isChecked())) { await chk2.click(); }
await page.waitForTimeout(300);
await btnSave.click();
await page.waitForTimeout(900);
// 若出现二次确认（不应）再点一次
const again = await page.evaluate(() => /无法找回|仍要|误删/.test(document.body.innerText));
if (again) { await btnSave.click(); await page.waitForTimeout(1200); }
const fin = await serverCfg();
R.restored = fin.persistApiKey === true && !fin.hasApiKey;
log('还原 persistApiKey =', fin.persistApiKey, '| hasApiKey =', fin.hasApiKey, R.restored ? 'PASS ✓' : 'FAIL ✗');
await btnClose.click();
await page.waitForTimeout(500);
await page.evaluate(() => localStorage.removeItem('bempdiff:config:draft'));

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
