// T01542/T01506 GUI 复验驱动：通过 CDP 连接已启动的 BempDiff（--remote-debugging-port=9222）
// 用法: NODE_PATH=<workspace>/node_modules node cdp-gui.mjs <scenario> [args...]
const PW = process.env.PW_CORE ||
  'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.resolve(process.argv[2] || 'out');
fs.mkdirSync(OUT, { recursive: true });

export async function connect() {
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const ctx = browser.contexts()[0];
  const page = ctx.pages().find(p => p.url().includes('18765')) || ctx.pages()[0];
  page.setDefaultTimeout(15000);
  return { browser, page };
}

export async function shot(page, name) {
  const p = path.join(OUT, name + '.png');
  await page.screenshot({ path: p });
  console.log('  [shot]', p);
}

export async function dumpButtons(page, limit = 40) {
  return await page.evaluate((lim) => {
    const out = [];
    const els = document.querySelectorAll('button, [role="button"], a.btn');
    for (const el of els) {
      if (out.length >= lim) break;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      out.push({
        tag: el.tagName.toLowerCase(),
        text: (el.innerText || el.textContent || '').trim().slice(0, 40),
        aria: el.getAttribute('aria-label') || '',
        cls: (el.className || '').toString().slice(0, 60),
        xy: [Math.round(r.x), Math.round(r.y)],
      });
    }
    return out;
  }, limit);
}

// 直接调用：node cdp-gui.mjs <outdir>  -> 冒烟：连接 + 截图 + 按钮清单
import { pathToFileURL } from 'node:url';
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const { browser, page } = await connect();
  console.log('title:', await page.title());
  console.log('url  :', page.url());
  await page.waitForTimeout(2500);
  await shot(page, '00-boot');
  const btns = await dumpButtons(page);
  console.log('buttons:', JSON.stringify(btns, null, 1));
  const txt = await page.evaluate(() => document.body.innerText.slice(0, 1200));
  console.log('--- body text ---\n' + txt);
  await browser.close();
}
