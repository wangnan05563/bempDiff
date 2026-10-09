// T00926 stage1 v2：关窗后高频监控进程/对话框存活
import { connect, mkdirp } from './helpers.mjs';
import { execSync } from 'node:child_process';

const OUT = mkdirp('out');
const V1 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v1.zip';
const V2 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v2.zip';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00926b]', ...a);

const appAlive = () => {
  try {
    const out = execSync('tasklist /FI "IMAGENAME eq BempDiff.exe" /FO CSV', { encoding: 'utf-8' });
    return (out.match(/BempDiff\.exe/g) || []).length;
  } catch { return 0; }
};
log('初始进程数 =', appAlive());

// 填路径并比对
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
  running = await page.evaluate(async () => {
    try { const r = await fetch('/api/tasks/running'); return await r.json(); } catch { return null; }
  });
  if (running && running.running && running.running.length) break;
}
log('RUNNING =', JSON.stringify(running).slice(0, 160));
if (!running || !running.running || !running.running.length) {
  log('⚠ 无运行任务，中止');
  process.exit(1);
}

// 触发关窗
await page.evaluate(() => window.close());
log('已触发 window.close()');
const timeline = [];
for (let i = 0; i < 15; i++) {
  await page.waitForTimeout(700);
  const procs = appAlive();
  let cdp = 0;
  try { cdp = (await page.evaluate(() => 1)) ? 1 : 1; } catch { cdp = 0; }
  let api = '';
  try { api = await page.evaluate(async () => { const r = await fetch('/api/tasks/running'); return JSON.stringify(await r.json()).slice(0, 80); }); } catch { api = 'CDP_GONE'; }
  timeline.push({ t: (i + 1) * 0.7 + 's', procs, cdp, api });
  log('  ', JSON.stringify(timeline[timeline.length - 1]));
  if (procs === 0) break;
}
console.log('TIMELINE:', JSON.stringify(timeline, null, 1));
process.exit(0);
