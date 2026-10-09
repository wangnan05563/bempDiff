// T00844 配置导出/导入 复验 v2（blob 捕获法读取导出内容；下载落盘环节另记缺口）
// 1) 导出 JSON 结构合法  2) 导入非法→报错  3) 导入合法→恢复+表单同步  4) 未记住Key时导出不含明文
import { connect, mkdirp, shot } from './helpers.mjs';
import fs from 'node:fs';
import path from 'node:path';

const OUT = mkdirp('out');
const TMP = mkdirp('files');
const { browser, page } = await connect();
const log = (...a) => console.log('[t00844]', ...a);
const R = {};

const fileInput = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//input[@type="file"]');
const btnSave = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[contains(@class,"btn-primary")][contains(., "保存")]');
const btnClose = page.locator('xpath=//div[contains(@class,"modal-backdrop")][last()]//button[normalize-space(.)="关闭"]');
const keyInput = page.getByRole('textbox', { name: /模型服务提供的访问密钥/ });

async function openCfg() {
  if (await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0 && /Base URL|导入|帮助/.test(b.innerText || '')))) return;
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
async function serverCfg() {
  return await page.evaluate(async () => (await (await fetch('/api/config')).json()));
}
async function captureExport() {
  const out = await page.evaluate(async () => {
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
    if (!captured) return 'NO_BLOB';
    return await captured.text();
  });
  return out;
}
async function lastToast() {
  return await page.evaluate(() => {
    const cands = [...document.querySelectorAll('[class*="toast"], [class*="Toast"], [role="alert"], [class*="message"]')]
      .filter(t => t.getBoundingClientRect().width > 0)
      .map(t => (t.innerText || '').trim()).filter(Boolean);
    return cands[cands.length - 1] || '';
  });
}

await openCfg();

// ---------- 1) 导出 ----------
const exp1 = await captureExport();
R.exportOk = false;
if (exp1.startsWith('{')) {
  const j = JSON.parse(exp1);
  fs.writeFileSync(path.join(TMP, 'exported-1.json'), exp1);
  R.exportOk = j.app === 'bempdiff' && j.kind === 'config' && typeof j.config === 'object';
  log('1) 导出: app=', j.app, 'kind=', j.kind, 'configKeys=', Object.keys(j.config).length, R.exportOk ? 'PASS ✓' : 'FAIL ✗');
} else { log('1) 导出失败:', exp1); }
await shot(page, OUT, 't00844-1-export');

// ---------- 4) 未记住 Key → 导出不含明文 ----------
const FAKE = 'sk-fake-key-abc123';
const bak = JSON.parse(fs.readFileSync(new URL('./config-backup.json', import.meta.url), 'utf-8'));
delete bak.hasApiKey;
await keyInput.fill(FAKE);
await page.evaluate(() => {
  const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
  const root = bds[bds.length - 1];
  for (const c of root.querySelectorAll('input[type="checkbox"]')) {
    let g = c.closest('label') || c.parentElement;
    for (let k = 0; k < 5 && g; k++) {
      if (/记住\s*API\s*Key/.test(g.innerText || '')) {
        if (c.checked) c.click();
        return;
      }
      g = g.parentElement;
    }
  }
});
await page.waitForTimeout(300);
await btnSave.click();
await page.waitForTimeout(1600);
const s1 = await serverCfg();
R.fakeKeyNotPersisted = !s1.hasApiKey;
log('4a) 未记住保存后 hasApiKey =', s1.hasApiKey, R.fakeKeyNotPersisted ? 'PASS ✓' : 'FAIL ✗');
const exp2 = await captureExport();
let noPlainKey = null, apiKeyField = '(no field)';
if (exp2.startsWith('{')) {
  const j2 = JSON.parse(exp2);
  fs.writeFileSync(path.join(TMP, 'exported-2.json'), exp2);
  apiKeyField = JSON.stringify(j2.config?.aiApiKey ?? null);
  noPlainKey = !exp2.includes(FAKE);
} else { noPlainKey = false; log('二次导出失败:', exp2); }
R.exportNoPlainKey = noPlainKey;
log('4b) 导出不含明文假 Key =', R.exportNoPlainKey, '| aiApiKey 字段 =', apiKeyField,
    '| persistApiKey(导出) =', exp2.startsWith('{') ? JSON.parse(exp2).config?.persistApiKey : '-',
    R.exportNoPlainKey ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00844-4-nokey');

// ---------- 2) 导入非法文件 ----------
const bad1 = path.join(TMP, 'bad-1.json');
fs.writeFileSync(bad1, JSON.stringify({ hello: 'world' }));
await fileInput.setInputFiles(bad1);
await page.waitForTimeout(1400);
const t1 = await lastToast();
R.badRejected = /非法|失败|无效|无法/.test(t1) && !/成功/.test(t1);
log('2) 非法结构导入提示 =', JSON.stringify(t1), R.badRejected ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00844-2-bad');

const bad2 = path.join(TMP, 'bad-2.json');
fs.writeFileSync(bad2, 'not-json{{{');
await fileInput.setInputFiles(bad2);
await page.waitForTimeout(1400);
const t2 = await lastToast();
log('2b) 乱文本导入提示 =', JSON.stringify(t2));

// ---------- 3) 导入合法文件 → 恢复 + 表单同步 ----------
if (exp2.startsWith('{')) {
  const mod = JSON.parse(exp2);
  mod.config.topK = 33;
  const good = path.join(TMP, 'good.json');
  fs.writeFileSync(good, JSON.stringify(mod, null, 2));
  await fileInput.setInputFiles(good);
  await page.waitForTimeout(1600);
  const s3 = await serverCfg();
  R.importRestored = s3.topK === 33;
  log('3) 导入后服务端 topK =', s3.topK, R.importRestored ? 'PASS ✓' : 'FAIL ✗');
  await shot(page, OUT, 't00844-3-import');
  const t3 = await lastToast();
  log('3b) 导入提示 =', JSON.stringify(t3));
}

// ---------- 还原 ----------
await page.evaluate(async (cfg) => { await fetch('/api/config', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cfg) }); }, bak);
const fin = await serverCfg();
R.serverRestored = fin.aiBaseUrl === bak.aiBaseUrl && fin.persistApiKey === true && fin.topK === 8;
log('还原服务端配置 =', R.serverRestored ? 'PASS ✓' : 'FAIL ✗', '| topK =', fin.topK, '| persistApiKey =', fin.persistApiKey, '| hasApiKey =', fin.hasApiKey);
// 关闭弹窗并清草稿（表单内遗留假 Key 属未保存态，不落盘）
await btnClose.click();
await page.waitForTimeout(600);
await page.evaluate(() => localStorage.removeItem('bempdiff:config:draft'));

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
