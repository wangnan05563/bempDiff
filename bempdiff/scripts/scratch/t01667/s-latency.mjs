// /api/tasks/running 比对期间延迟测量（不关窗）
import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const log = (...a) => console.log('[latency]', ...a);

const V1 = ['D:', 'code', 'otherProjects', '18_comparePakage', '.workbuddy', 'tmp', 'perf50k', 'big-v1.zip'].join('\\');
const V2 = ['D:', 'code', 'otherProjects', '18_comparePakage', '.workbuddy', 'tmp', 'perf50k', 'big-v2.zip'].join('\\');

await page.getByTitle(/以单个 war\/jar 包作为输入/).click();
await page.waitForTimeout(500);
async function setPath(idx, val) {
  const bar = page.locator('.pbc').nth(idx);
  await bar.evaluate(el => el.click());
  await page.waitForTimeout(300);
  const inp = bar.locator('input.pb-input');
  await inp.waitFor({ state: 'visible', timeout: 5000 });
  await inp.fill(val);
  await inp.press('Enter');
  await page.waitForTimeout(300);
}
await setPath(0, V1);
await setPath(1, V2);
await page.getByTitle(/加载两个包\/目录/).click();

const samples = [];
for (let i = 0; i < 90; i++) {
  const t0 = Date.now();
  let ok = true, running = null;
  try {
    running = await page.evaluate(async () => {
      const r = await fetch('/api/tasks/running');
      return await r.json();
    });
  } catch { ok = false; }
  const ms = Date.now() - t0;
  samples.push({ t: (i * 0.5).toFixed(1) + 's', ms, ok, has: !!(running && running.running && running.running.length) });
  if (i % 20 === 0) log(samples[samples.length - 1]);
  await page.waitForTimeout(300);
}
const bad = samples.filter(s => s.ms > 1500 || !s.ok);
console.log('MAX_MS:', Math.max(...samples.map(s => s.ms)), '| >1500ms:', bad.length, '| 失败:', samples.filter(s => !s.ok).length);
if (bad.length) console.log('SLOW:', JSON.stringify(bad.slice(0, 12)));
await browser.close();
