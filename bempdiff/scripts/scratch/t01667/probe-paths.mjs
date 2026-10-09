import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const info = await page.evaluate(() => {
  const inputs = [...document.querySelectorAll('input')].map(i => {
    const r = i.getBoundingClientRect();
    return { type: i.type, ph: i.placeholder, val: (i.value || '').slice(0, 60), title: (i.title || '').slice(0, 50), aria: i.getAttribute('aria-label') || '', vis: r.width > 0, id: i.id };
  }).filter(x => x.vis);
  return inputs;
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
