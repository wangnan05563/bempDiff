const PW = process.env.PW_CORE || 'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts()[0].pages().find(p => p.url().includes('18765'));
const info = await page.evaluate(() => {
  // 找包含 Base URL 的最深可见元素链
  const hits = [...document.querySelectorAll('*')].filter(el =>
    el.children.length === 0 && /Base URL/.test(el.textContent || ''));
  const chains = hits.slice(0, 3).map(el => {
    const chain = [];
    let n = el;
    for (let k = 0; k < 8 && n; k++) {
      const r = n.getBoundingClientRect();
      chain.push(`${n.tagName.toLowerCase()}.${(n.className||'').toString().slice(0,60)} [${Math.round(r.width)}x${Math.round(r.height)}]`);
      n = n.parentElement;
    }
    return chain;
  });
  // 顶部按钮条:modal 头部按钮
  const modalRoot = hits[0] && (() => { let n = hits[0]; while (n && !/modal/i.test(n.className||'')) n = n.parentElement; return n; })();
  const btns = modalRoot ? [...modalRoot.querySelectorAll('button')].map(b => ({
    text: (b.innerText||'').trim().slice(0,30), aria: b.getAttribute('aria-label')||'', cls: (b.className||'').toString().slice(0,50) })) : [];
  return { chains, modalRootCls: modalRoot ? modalRoot.className : null, btns };
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
