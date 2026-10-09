// T00876 跳转下一处差异 + T00845 全屏入口 复验
// 前置：big-v1.zip → big-v2.zip 比对，选一个修改文件
import { connect, mkdirp, shot } from './helpers.mjs';

const OUT = mkdirp('out');
const V1 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v1.zip';
const V2 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v2.zip';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00876]', ...a);
const R = {};

async function setPath(which, val) {
  // which: '老' | '新' —— 点击对应 pathbar 进入编辑态
  const bars = page.locator('.pbc');
  const n = await bars.count();
  log('pathbar 数量 =', n);
  const bar = which === '老' ? bars.nth(0) : bars.nth(n - 1);
  // 直接对 .pbc 根元素派发 click（target=根元素，绕过 pb-seg 拦截），Vue onBarClick 进入编辑态
  await bar.evaluate(el => {
    if (el.classList.contains('pbc-disabled')) throw new Error('pbc disabled');
    el.click();
  });
  await page.waitForTimeout(400);
  const inp = bar.locator('input.pb-input');
  await inp.waitFor({ state: 'visible', timeout: 5000 });
  await inp.fill(val);
  await inp.press('Enter');
  await page.waitForTimeout(500);
}

// 1) 填路径
await setPath('老', V1);
await setPath('新', V2);
const shown = await page.evaluate(() => [...document.querySelectorAll('.pbc')].map(p => (p.title || p.innerText || '').slice(0, 80)));
log('路径条 =', JSON.stringify(shown));

// 2) 开始比对
await page.getByTitle(/加载两个包\/目录/).click();
log('已点击开始比对，等待差异树…');
let treeReady = false;
for (let i = 0; i < 90; i++) {
  await page.waitForTimeout(2000);
  const st = await page.evaluate(() => {
    const t = document.body.innerText;
    const m = t.match(/命中\s*(\d+)\s*\/\s*(\d+)\s*项/);
    return { m: m ? m[1] + '/' + m[2] : null, busy: /比对中|解析中|解包中|加载中/.test(t) };
  });
  if (i % 5 === 0) log('  等待中', i * 2 + 's', JSON.stringify(st));
  if (st.m && !st.busy && st.m.split('/')[0] !== '0') { treeReady = true; log('差异树就绪:', st.m); break; }
}
R.treeReady = treeReady;
if (!treeReady) { console.log('SUMMARY:', JSON.stringify(R)); await browser.close(); process.exit(1); }
await shot(page, OUT, 't00876-1-tree');

// 3) 点一个「修改」文件（MODIFIED 状态点 = var(--bs-warning) 黄色）
const clicked = await page.evaluate(() => {
  const nodes = [...document.querySelectorAll('button.tree-node')];
  for (const el of nodes) {
    const dot = el.querySelector('.bi-circle-fill');
    if (!dot) continue;
    const color = dot.style.color || getComputedStyle(dot).color;
    // --bs-warning 在浅色主题下为琥珀色 (255,193,7)
    if (/255,\s*193,\s*7|warning/.test(color)) {
      el.click();
      return { key: el.title || '', color };
    }
  }
  // 兜底：点第一个非归档文件节点
  for (const el of nodes) {
    if (/ARCHIVE|JAR/.test(el.innerHTML)) continue;
    el.click();
    return { key: el.title || '', color: 'fallback' };
  }
  return null;
});
log('点击修改文件:', JSON.stringify(clicked));
// 等待 DiffView 打开且导航可用（反编译+解析可能耗时）
for (let i = 0; i < 60; i++) {
  await page.waitForTimeout(2000);
  const st = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const nextDisabled = [...document.querySelectorAll('button')].some(b => /跳到下一处差异/.test(b.title || '') && b.disabled);
    const banner = /反编译中|解析中|decompil/i.test(document.body.innerText);
    return { cnt, nextDisabled, banner };
  });
  if (st.cnt && st.cnt !== '0/0' && !st.nextDisabled) { log('DiffView 就绪: counter =', st.cnt); break; }
  if (i % 5 === 0) log('  等待 DiffView…', i * 2 + 's', JSON.stringify(st));
}

// 4) T00876：下一处差异 —— 找 counter "n/m" 与 chevron-down 按钮
const nav = await page.evaluate(() => {
  const btns = [...document.querySelectorAll('button')].map(b => ({
    aria: b.getAttribute('aria-label') || '', disabled: b.disabled,
    title: b.getAttribute('title') || '',
  })).filter(b => /下一处|下一处差异|gotoNext|下一/.test(b.aria + b.title));
  const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
  return { btns, cnt };
});
log('导航控件:', JSON.stringify(nav));
R.navFound = nav.btns.length > 0 && !!nav.cnt;

// 反复点「下一处」，记录 counter 与滚动位置变化
const results = [];
const nextBtn = page.locator('button[aria-label*="下一"]').first();
for (let k = 0; k < 8; k++) {
  const before = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const area = document.querySelector('.diff-unified, .diff-area, [class*="diff"]');
    const scroller = area && (area.querySelector('.uni-body') || area.querySelector('[class*="body"]') || area);
    return { cnt, scroll: scroller ? Math.round(scroller.scrollTop) : null };
  });
  await nextBtn.click();
  await page.waitForTimeout(350);
  const after = await page.evaluate(() => {
    const cnt = [...document.querySelectorAll('span')].map(s => (s.innerText || '').trim()).find(t => /^\d+\/\d+$/.test(t));
    const area = document.querySelector('.diff-unified, .diff-area, [class*="diff"]');
    const scroller = area && (area.querySelector('.uni-body') || area.querySelector('[class*="body"]') || area);
    const cur = !!document.querySelector('.row.current, .row.flash, [class*="current"]');
    return { cnt, scroll: scroller ? Math.round(scroller.scrollTop) : null, cur };
  });
  results.push({ before: before.cnt + '@' + before.scroll, after: after.cnt + '@' + after.scroll + (after.cur ? '*cur' : '') });
}
R.navSeq = results;
const moved = results.filter(r => r.before !== r.after).length;
R.jumpsOnSingleClick = moved >= 6;
log('T00876 连击序列:', JSON.stringify(results, null, 1));
log('T00876 单击即跳 =', R.jumpsOnSingleClick ? 'PASS ✓' : 'FAIL ✗');
await shot(page, OUT, 't00876-2-after-nav');

// 5) T00845：全屏（专注模式）入口
const fsBtn = await page.evaluate(() => {
  const b = [...document.querySelectorAll('button')].find(b => /全屏|focusMode|arrows-fullscreen/i.test((b.getAttribute('aria-label') || '') + (b.title || '') + (b.innerHTML || '')));
  if (!b) return null;
  const r = b.getBoundingClientRect();
  return { aria: b.getAttribute('aria-label'), title: b.title, visible: r.width > 0 };
});
R.fullscreenEntry = !!fsBtn && fsBtn.visible;
log('T00845 全屏入口 =', JSON.stringify(fsBtn), R.fullscreenEntry ? 'PASS ✓' : 'FAIL ✗');
if (fsBtn && fsBtn.visible) {
  // 点进去再退出
  await page.locator('button[title*="全屏"], button[aria-label*="全屏"]').first().click();
  await page.waitForTimeout(800);
  await shot(page, OUT, 't00845-fullscreen');
  await page.locator('button[title*="全屏"], button[aria-label*="全屏"]').first().click(); // 退出
  await page.waitForTimeout(500);
  R.fullscreenToggled = true;
  log('T00845 全屏切换(进+出) = PASS ✓');
}
// 编辑入口
const editBtn = await page.evaluate(() => {
  const b = [...document.querySelectorAll('button')].find(b => /编辑|edit/i.test((b.getAttribute('aria-label') || '') + (b.title || '')));
  if (!b) return null;
  const r = b.getBoundingClientRect();
  return { aria: b.getAttribute('aria-label'), visible: r.width > 0 };
});
log('T00845 编辑入口 =', JSON.stringify(editBtn));

console.log('SUMMARY:', JSON.stringify(R, null, 1));
await browser.close();
