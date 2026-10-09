import { connect } from './helpers.mjs';
const { browser, page } = await connect();
if (!(await page.evaluate(() => [...document.querySelectorAll('.modal-backdrop')].some(b => b.getBoundingClientRect().width > 0)))) {
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(900);
}
const info = await page.evaluate(() => {
  const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
  const root = bds[bds.length - 1];
  const out = [];
  for (const c of root.querySelectorAll('input[type="checkbox"]')) {
    const r = c.getBoundingClientRect();
    let g = c.closest('label') || c.parentElement;
    let found = null;
    for (let k = 0; k < 6 && g; k++) {
      const t = (g.innerText || '').trim().replace(/\n/g, ' | ');
      if (t && /记住|Key|令牌|Token|密钥/.test(t)) { found = t.slice(0, 60); break; }
      g = g.parentElement;
    }
    out.push({ visible: r.width > 0, checked: c.checked, id: c.id, label: found });
  }
  return out;
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
