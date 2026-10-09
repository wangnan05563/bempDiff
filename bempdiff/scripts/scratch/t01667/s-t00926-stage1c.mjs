// T00926 stage1c：发起比对并确认 RUNNING（不关窗，由外部 Alt+F4）
import { connect } from './helpers.mjs';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00926c]', ...a);

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
let running = null;
for (let i = 0; i < 60; i++) {
  await page.waitForTimeout(1000);
  running = await page.evaluate(async () => { try { return await (await fetch('/api/tasks/running')).json(); } catch { return null; } });
  if (running && running.running && running.running.length) break;
}
log('RUNNING =', JSON.stringify(running).slice(0, 140));
console.log('READY_FOR_ALTF4');
process.exit(0);
