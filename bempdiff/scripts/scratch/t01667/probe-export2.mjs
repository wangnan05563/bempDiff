import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const ctx = browser.contexts()[0];
// 旧版 Page.setDownloadBehavior + Browser 版双保险
const cdp1 = await ctx.newCDPSession(page);
await cdp1.send('Page.setDownloadBehavior', { behavior: 'allow', downloadPath: 'D:/code/otherProjects/18_comparePakage/bempdiff/scripts/scratch/t01667/files' });
await page.evaluate(() => {
  const a = document.createElement('a');
  const blob = new Blob(['hello-download-test'], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  a.href = url; a.download = 'dl-test.txt';
  document.body.appendChild(a); a.click(); a.remove();
});
await page.waitForTimeout(2500);
// blob 捕获法：导出内容直接读
const out = await page.evaluate(async () => {
  const orig = URL.createObjectURL.bind(URL);
  let captured = null;
  URL.createObjectURL = (blob) => { captured = blob; return orig(blob); };
  const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
  const root = bds[bds.length - 1];
  const btn = [...root.querySelectorAll('button')].find(b => /导出配置/.test(b.innerText || ''));
  if (!btn) return 'BTN_NOT_FOUND';
  btn.click();
  await new Promise(r => setTimeout(r, 600));
  URL.createObjectURL = orig;
  if (!captured) return 'NO_BLOB';
  const text = await captured.text();
  return text;
});
if (typeof out === 'string' && out.startsWith('{')) {
  const fs = await import('node:fs');
  fs.writeFileSync('files/exported-capture.json', out);
  const j = JSON.parse(out);
  console.log('captured export: app=', j.app, 'kind=', j.kind, 'configKeys=', Object.keys(j.config||{}).length, 'persistApiKey=', j.config?.persistApiKey, 'aiApiKey=', JSON.stringify(j.config?.aiApiKey ?? null));
} else {
  console.log('capture result:', out);
}
await browser.close();
