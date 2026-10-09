// CDP GUI 复验公共助手
const PW = process.env.PW_CORE ||
  'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
import fs from 'node:fs';

export async function connect() {
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const page = browser.contexts()[0].pages().find(p => p.url().includes('18765'));
  page.setDefaultTimeout(20000);
  return { browser, page };
}

export function mkdirp(d) { fs.mkdirSync(d, { recursive: true }); return d; }

export async function shot(page, dir, name) {
  const p = `${dir}/${name}.png`;
  await page.screenshot({ path: p, timeout: 45000, animations: 'disabled', caret: 'hide' })
    .catch(e => console.log('  [shot-fail]', name, String(e).split('\n')[0]));
  console.log('  [shot]', name);
}

// 配置中心弹窗根（.modal-backdrop 内可见者）
export async function cfgRoot(page) {
  return await page.evaluateHandle(() => {
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => {
      const r = b.getBoundingClientRect();
      return r.width > 0;
    });
    return bds.length ? bds[bds.length - 1] : null;
  });
}

export async function cfgOpen(page) {
  return await page.evaluate(() => {
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => {
      const r = b.getBoundingClientRect();
      return r.width > 0 && /Base URL|模型厂商|帮助|关于/.test(b.innerText || '');
    });
    return bds.length > 0;
  });
}

export async function cfgEnsureOpen(page) {
  if (await cfgOpen(page)) return true;
  await page.getByTitle(/打开配置中心/).click();
  await page.waitForTimeout(1000);
  return await cfgOpen(page);
}

export async function cfgClick(page, matcher) {
  // matcher: (text, aria, cls) => bool；在弹窗内点按钮
  const clicked = await page.evaluate((fnSrc) => {
    const fn = eval(fnSrc);
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
    const root = bds[bds.length - 1];
    if (!root) return null;
    const btns = [...root.querySelectorAll('button')].filter(b => {
      const r = b.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    });
    const b = btns.find(b => fn((b.innerText || '').trim(), b.getAttribute('aria-label') || '', (b.className || '').toString()));
    if (!b) return null;
    const label = (b.innerText || b.getAttribute('aria-label') || '').trim();
    b.click();
    return label;
  }, matcher.toString());
  if (clicked !== null) await page.waitForTimeout(500);
  return clicked;
}

export async function cfgInputByLabel(page, labelReSrc, value) {
  // 找 label 文本匹配的 text input，设置值（Vue 兼容 setter）
  return await page.evaluate(({ labelReSrc, value }) => {
    const re = eval(labelReSrc);
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
    const root = bds[bds.length - 1];
    if (!root) return 'NO_MODAL';
    const inputs = [...root.querySelectorAll('input, textarea')].filter(i => {
      const r = i.getBoundingClientRect();
      return r.width > 0 && i.type !== 'checkbox' && i.type !== 'radio';
    });
    for (const i of inputs) {
      let g = i.closest('label') || i.parentElement;
      for (let k = 0; k < 5 && g; k++) {
        if (re.test(g.innerText || '')) {
          if (value !== undefined) {
            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(i, value);
            i.dispatchEvent(new Event('input', { bubbles: true }));
            i.dispatchEvent(new Event('change', { bubbles: true }));
          }
          return i.value;
        }
        g = g.parentElement;
      }
    }
    return 'NOT_FOUND';
  }, { labelReSrc: labelReSrc.toString(), value });
}

export async function cfgCheckboxByLabel(page, labelReSrc) {
  return await page.evaluate((labelReSrc) => {
    const re = eval(labelReSrc);
    const bds = [...document.querySelectorAll('.modal-backdrop')].filter(b => b.getBoundingClientRect().width > 0);
    const root = bds[bds.length - 1];
    if (!root) return 'NO_MODAL';
    for (const i of root.querySelectorAll('input[type="checkbox"]')) {
      const r = i.getBoundingClientRect();
      if (r.width === 0) continue;
      let g = i.closest('label') || i.parentElement;
      for (let k = 0; k < 5 && g; k++) {
        if (re.test(g.innerText || '')) return { checked: i.checked };
        g = g.parentElement;
      }
    }
    return 'NOT_FOUND';
  }, labelReSrc.toString());
}

// 通过 UI 把 httpProxy 保存为指定值（并等待保存完成）
export async function saveProxy(page, val) {
  await cfgInputByLabel(page, /HTTP\s*代理/, val);
  const clicked = await cfgClick(page, (t, a, c) => t === '保存' && /btn-primary/.test(c));
  await page.waitForTimeout(1200);
  return clicked;
}
