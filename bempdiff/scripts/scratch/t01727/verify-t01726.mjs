// T01726 真机 CDP 验证：立即更新按钮显隐（upToDate=true 隐藏 / upToDate=false 显示）
// 通过路由拦截 mock POST /api/update/check 两个分支，不依赖真实 GitHub 状态。
import { createRequire } from 'node:module'
const workspaceRequire = createRequire('C:/Users/hspcadmin/.workbuddy/binaries/node/workspace/node_modules/')
const { chromium } = workspaceRequire('playwright-core')

const browser = await chromium.connectOverCDP('http://127.0.0.1:9222')
const ctx = browser.contexts()[0]
let page = null
for (const p of ctx.pages()) { if (/18765|index\.html/.test(p.url())) { page = p; break } }
if (!page) page = ctx.pages()[0] || null
if (!page) throw new Error('无可用的页面目标')
await page.bringToFront()

const MOCK = (upToDate) => ({
  ok: true, current: '0.1.2026100902', upToDate,
  latest: {
    tag: 'v0.1.2026100901', name: 'BempDiff 0.1.2026100901',
    url: 'https://github.com/wangnan05563/bempDiff/releases/tag/v0.1.2026100901',
    downloadUrl: 'https://github.com/wangnan05563/bempDiff/releases/download/v0.1.2026100901/BempDiff-0.1.2026100901-arm64-setup.exe',
    publishedAt: '2026-10-09T12:00:00Z', body: 'mock release body'
  },
  repo: 'wangnan05563/bempDiff', message: upToDate ? '当前已是最新版本' : '发现新版本 v0.1.2026100901'
})

let result = { pass: true, detail: [] }
const assert = (cond, name) => { result.detail.push(`${cond ? 'PASS' : 'FAIL'}: ${name}`); if (!cond) result.pass = false }

async function runBranch(upToDate) {
  await page.route('**/api/update/check', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK(upToDate)) })
  })
  // 打开配置中心 → 关于页
  await page.evaluate(() => { localStorage.removeItem('bempdiff:config:draft') })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(2500)
  // 点击工具栏「配置」按钮（打开配置中心）
  const cfgBtn = page.locator('button', { hasText: /配置中心|设置|配置/ }).first()
  if (await cfgBtn.count() > 0) { await cfgBtn.click().catch(() => {}) } else {
    await page.evaluate(() => { const b=[...document.querySelectorAll('button,i,.toolbar [title]')].find(e=>/配置|设置/.test(e.title||e.textContent||'')); b&&b.click() })
  }
  await page.waitForTimeout(1200)
  // 关于 tab
  const aboutTab = page.locator('button.nav-link, .modal [role="tab"], button', { hasText: /^关于$/ }).first()
  if (await aboutTab.count() > 0) { await aboutTab.click().catch(() => {}) } else {
    await page.evaluate(() => { const t=[...document.querySelectorAll('button,a,.nav-link')].find(e=>e.textContent.trim()==='关于'); t&&t.click() })
  }
  await page.waitForTimeout(800)
  // 点「检查更新」
  await page.evaluate(() => { const b=[...document.querySelectorAll('button')].find(e=>/检查更新/.test(e.textContent)); b&&b.click() })
  await page.waitForTimeout(2500)
  const bodyText = await page.evaluate(() => document.body.innerText)
  const bannerUpToDate = bodyText.includes('当前已是最新版本')
  const bannerNewFound = bodyText.includes('发现新版本')
  const oneClickBtn = await page.locator('button:has-text("立即更新")').count()
  return { bannerUpToDate, bannerNewFound, oneClickBtn }
}

try {
  // 分支1：upToDate=true（已最新）→ 无「立即更新」按钮
  const b1 = await runBranch(true)
  assert(b1.bannerUpToDate, `upToDate=true 显示「已是最新」横幅 (got=${b1.bannerUpToDate})`)
  assert(b1.oneClickBtn === 0, `upToDate=true 不显示「立即更新」按钮 (count=${b1.oneClickBtn})`)

  // 分支2：upToDate=false（确有新版）→ 有「立即更新」按钮
  const b2 = await runBranch(false)
  assert(b2.bannerNewFound, `upToDate=false 显示「发现新版本」横幅 (got=${b2.bannerNewFound})`)
  assert(b2.oneClickBtn >= 1, `upToDate=false 显示「立即更新」按钮 (count=${b2.oneClickBtn})`)
} catch (e) {
  result.pass = false
  result.detail.push(`EXCEPTION: ${String(e).slice(0, 300)}`)
}
console.log(JSON.stringify(result, null, 2))
await browser.close()
