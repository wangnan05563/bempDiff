// 场景浏览器：连接 CDP，枚举指定区域的控件（aria/文本/占位），便于编写复验脚本
const PW = process.env.PW_CORE ||
  'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
import fs from 'node:fs';

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts()[0].pages().find(p => p.url().includes('18765'));
page.setDefaultTimeout(15000);

const mode = process.argv[2] || 'topbar';
const out = {};

if (mode === 'topbar' || mode === 'all') {
  out.topbar = await page.evaluate(() => {
    const els = document.querySelectorAll('header button, nav button, .toolbar button, body > * button');
    return [...els].slice(0, 60).map(el => ({
      aria: el.getAttribute('aria-label') || '',
      title: el.getAttribute('title') || '',
      text: (el.innerText || '').trim().slice(0, 20),
      id: el.id || '',
    })).filter(x => x.aria || x.title || x.text);
  });
}
console.log(JSON.stringify(out, null, 1));
await browser.close();
