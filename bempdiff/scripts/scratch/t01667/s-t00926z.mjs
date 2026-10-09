// T00926 最终实验：RUNNING 确认后 2s 内关窗 + 高频时间线
import { connect } from './helpers.mjs';
import fs from 'node:fs';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00926z]', ...a);

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
log('compare submitted at', new Date().toISOString().slice(11, 23));
let running = null;
for (let i = 0; i < 60; i++) {
  await page.waitForTimeout(1000);
  running = await page.evaluate(async () => { try { return await (await fetch('/api/tasks/running')).json(); } catch { return null; } });
  if (running && running.running && running.running.length) break;
}
log('RUNNING confirmed at', new Date().toISOString().slice(11, 23), JSON.stringify(running).slice(0, 120));
if (!running || !running.running || !running.running.length) { log('no running task, abort'); process.exit(1); }

// RUNNING 后立刻（<2s）关窗
await page.evaluate(() => window.close());
log('window.close() at', new Date().toISOString().slice(11, 23));

// 高频探测（尽量在连接死掉前记录）
const events = [];
for (let i = 0; i < 20; i++) {
  await new Promise(r => setTimeout(r, 800));
  let state = 'page-ok';
  try { await page.evaluate(() => 1); } catch { state = 'page-gone'; }
  events.push({ t: new Date().toISOString().slice(11, 23), state });
  log(events[events.length - 1].t, state);
  if (state === 'page-gone') break;
}
fs.writeFileSync('t00926-events.json', JSON.stringify(events, null, 1));
process.exit(0);
