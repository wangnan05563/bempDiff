import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const info = await page.evaluate(() => {
  const colors = {};
  for (const el of document.querySelectorAll('button.tree-node')) {
    const dot = el.querySelector('.bi-circle-fill');
    if (!dot) continue;
    const c = dot.style.color || getComputedStyle(dot).color;
    colors[c] = (colors[c] || 0) + 1;
  }
  const badge = [...document.querySelectorAll('button.tree-node .badge')].slice(0, 5).map(b => b.innerText.trim());
  return { colors, badgeSample: badge, total: document.querySelectorAll('button.tree-node').length };
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
