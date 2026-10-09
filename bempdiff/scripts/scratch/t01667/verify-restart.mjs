import { connect } from './helpers.mjs';
const { browser, page } = await connect();
await page.waitForTimeout(2000);
const key = await page.evaluate(() => localStorage.getItem('bempdiff-guide-btn'));
const btn = await page.evaluate(() => {
  return [...document.querySelectorAll('button')]
    .filter(b => { const r = b.getBoundingClientRect(); return r.width > 0 && r.y > window.innerHeight * 0.6 && r.x < window.innerWidth * 0.2; })
    .map(b => ({ text: (b.innerText || '').trim().slice(0, 20), title: b.getAttribute('title') || '' }));
});
console.log('[t00862-D] 重启后 localStorage[guide-btn] =', JSON.stringify(key));
console.log('[t00862-D] 左下角按钮 =', JSON.stringify(btn));
console.log('RESULT:', key === 'on' && btn.some(b => /引导/.test(b.text + b.title)) ? 'PASS ✓ 按钮状态重启保持' : 'FAIL ✗');
await browser.close();
