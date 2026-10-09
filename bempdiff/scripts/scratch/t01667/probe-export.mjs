import { connect, mkdirp } from './helpers.mjs';
const OUT = mkdirp('out');
const { browser, page } = await connect();
const cdp = await browser.contexts()[0].newCDPSession(page);
await cdp.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: 'D:/code/otherProjects/18_comparePakage/bempdiff/scripts/scratch/t01667/files', eventsEnabled: true });
page.on('console', m => console.log('[console]', m.type(), m.text().slice(0, 150)));
page.on('pageerror', e => console.log('[pageerror]', String(e).slice(0, 200)));
// 打开配置中心
if (!(await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0)))) {
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
// 点击导出并观察
const clicked = await page.evaluate(() => {
  const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
  const root = bds[bds.length - 1];
  const btn = [...root.querySelectorAll('button')].find(b => /导出配置/.test(b.innerText || ''));
  if (!btn) return 'NOT_FOUND';
  btn.click();
  return 'clicked:' + btn.innerText.trim();
});
console.log('export click:', clicked);
await page.waitForTimeout(3000);
// 检查 files 目录是否有新文件（从 node 侧无法直接看，改为列出下载目录 via CDP 不行，先截图）
await page.screenshot({ path: OUT + '/probe-export.png' });
// 也测试 a[download] 的行为
const testDl = await page.evaluate(() => {
  const a = document.createElement('a');
  const blob = new Blob(['hello-download-test'], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  a.href = url; a.download = 'dl-test.txt';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
  return url;
});
console.log('test download url:', testDl);
await page.waitForTimeout(3000);
await browser.close();
