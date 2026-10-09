const PW = process.env.PW_CORE || 'file:///C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/playwright-core/index.mjs';
const { chromium } = await import(PW);
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
const page = browser.contexts()[0].pages().find(p => p.url().includes('18765'));
const info = await page.evaluate(() => {
  const bd = document.querySelector('.modal-backdrop');
  if (!bd) return { open: false };
  const btns = [...bd.querySelectorAll('button')].map(b => ({
    text: (b.innerText||'').trim().slice(0,30), aria: b.getAttribute('aria-label')||'', cls: (b.className||'').toString().slice(0,50) }));
  const inputs = [...bd.querySelectorAll('input')].map(i => ({type: i.type, ph: i.placeholder, val: (i.value||'').slice(0,25)}));
  return { open: true, btnCount: btns.length, btns, inputCount: inputs.length };
});
console.log(JSON.stringify(info, null, 1));
await browser.close();
