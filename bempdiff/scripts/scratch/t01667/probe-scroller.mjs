import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const info = await page.evaluate(() => {
  const area = document.querySelector('.diff-area');
  const scrollers = [];
  for (const el of document.querySelectorAll('*')) {
    if (el.scrollHeight > el.clientHeight + 50 && el.clientHeight > 100) {
      scrollers.push({ cls: (el.className || '').toString().slice(0, 50), st: el.scrollTop, sh: el.scrollHeight, ch: el.clientHeight });
    }
  }
  const cur = [...document.querySelectorAll('[class*="current"], [class*="cur-"]')].slice(0, 5).map(e => ({ cls: e.className.toString().slice(0, 50), top: Math.round(e.getBoundingClientRect().top), text: (e.textContent || '').slice(0, 40) }));
  const mode = { uni: !!document.querySelector('.diff-unified'), split: !!document.querySelector('[class*="split"]'), wrap: !!document.querySelector('[class*="wrap"]') };
  return { scrollers: scrollers.slice(0, 8), cur, mode, areaCls: area ? area.innerHTML.length : null };
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
