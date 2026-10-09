import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const st = await page.evaluate(() => {
  const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
  const overlay = [...document.querySelectorAll('.busy-overlay')].map(o => ({ vis: o.getBoundingClientRect().width > 0, text: (o.innerText || '').slice(0, 60) }));
  const active = document.querySelector('.tree-node.active');
  return { cnt, overlay, activeKey: active ? active.title : null,
    bodyHint: (document.body.innerText.match(/(反编译|解析|解包)[^\n]{0,20}/) || [''])[0] };
});
console.log(JSON.stringify(st, null, 1));
await browser.close();
