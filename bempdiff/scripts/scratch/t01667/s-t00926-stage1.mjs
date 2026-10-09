// T00926 关闭前任务检测 —— 复验主流程编排（大包比对 RUNNING 期触发关窗）
import { connect, mkdirp, shot } from './helpers.mjs';

const OUT = mkdirp('out');
const V1 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v1.zip';
const V2 = 'D:\\code\\otherProjects\\18_comparePakage\\.workbuddy\\tmp\\perf50k\\big-v2.zip';
const { browser, page } = await connect();
const log = (...a) => console.log('[t00926]', ...a);
const R = {};

// 1) 切回 包 模式并填 big zip 路径
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
log('路径已设为 big zip 对');

// 2) 开始比对，并等 RUNNING（最多 90s；50000 条目比对约 2min，窗口足够）
await page.getByTitle(/加载两个包\/目录/).click();
log('比对已发起，等待 RUNNING…');
let running = null;
for (let i = 0; i < 90; i++) {
  await page.waitForTimeout(1000);
  running = await page.evaluate(async () => {
    try { const r = await fetch('/api/tasks/running'); return await r.json(); }
    catch (e) { return { err: String(e) }; }
  });
  if (i % 10 === 0) log('  ', i + 's', JSON.stringify(running).slice(0, 150));
  if (running && running.running && running.running.length) break;
}
R.runningAtClose = running;
log('/api/tasks/running =', JSON.stringify(running));

// 3) 仅在确认有 RUNNING 任务时才触发关窗
if (R.runningAtClose && R.runningAtClose.running && R.runningAtClose.running.length) {
  await page.evaluate(() => window.close());
  log('已触发 window.close()，等待原生确认框…');
  await page.waitForTimeout(3500);
  console.log('WAIT_DIALOG_DETECT');
} else {
  log('⚠ 无 RUNNING 任务，不触发关窗（避免误关应用）');
}
console.log('SUMMARY:', JSON.stringify(R, null, 1));
process.exit(0);   // 由外部 PowerShell 检测/操作原生对话框
