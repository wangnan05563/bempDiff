// T00843 场景第 1 步：打开配置中心，枚举表单控件
const PW = process.env.PW_CORE ||
  'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
import fs from 'node:fs';

const OUT = 'out';
fs.mkdirSync(OUT, { recursive: true });
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts()[0].pages().find(p => p.url().includes('18765'));
page.setDefaultTimeout(15000);

// 顶栏右侧 gear：枚举所有按钮找 设置/配置 相关
const cand = await page.evaluate(() => {
  return [...document.querySelectorAll('button')].map((el, i) => ({
    i,
    aria: el.getAttribute('aria-label') || '',
    title: el.getAttribute('title') || '',
  })).filter(x => /设置|配置|settings|config/i.test(x.aria + x.title));
});
console.log('candidates:', JSON.stringify(cand));

if (cand.length) {
  const btns = page.locator('button');
  await btns.nth(cand[0].i).click();
  await page.waitForTimeout(1200);
  await page.screenshot({ path: OUT + '/01-config-open.png' });
  const fields = await page.evaluate(() => {
    const modal = document.querySelector('.modal.show, [class*="modal"][style*="block"], .settings, body');
    const root = [...document.querySelectorAll('.modal, [role="dialog"]')].find(m => {
      const r = m.getBoundingClientRect();
      return r.width > 0;
    }) || document.body;
    const items = [];
    root.querySelectorAll('input, select, textarea').forEach(el => {
      const r = el.getBoundingClientRect();
      if (r.width === 0) return;
      const lab = el.closest('label, .form-group, .mb-3, .row')?.innerText?.trim().slice(0, 50) || '';
      items.push({
        tag: el.tagName.toLowerCase(),
        type: el.type || '',
        id: el.id || '',
        ph: el.placeholder || '',
        label: lab,
        value: (el.value || '').slice(0, 30),
        visible: r.width > 0,
      });
    });
    const btns2 = [...root.querySelectorAll('button')].map(b => ({
      text: (b.innerText || '').trim().slice(0, 24),
      aria: b.getAttribute('aria-label') || '',
      cls: (b.className || '').toString().slice(0, 40),
    })).filter(b => b.text || b.aria);
    return { fields: items, buttons: btns2, title: root.querySelector('.modal-title, h5, h4')?.innerText || '' };
  });
  console.log('modal title:', fields.title);
  console.log('fields:', JSON.stringify(fields.fields, null, 1));
  console.log('buttons:', JSON.stringify(fields.buttons, null, 1));
}
await browser.close();
