#!/usr/bin/env node
/**
 * BempDiff —— 生成「electronLanguages 全保留」的 electron-builder 配置（T01509 沙箱适配）。
 *
 * 为什么需要（实测踩坑，非拍脑袋）：
 *   electron-builder 的 `removeUnusedLanguagesIfNeeded`（app-builder-lib
 *   ElectronFramework.ts:98）会遍历 Electron 发行 zip 内**全部** locales/*.pak，
 *   逐个删除未列入 `electronLanguages` 的语言包。Electron 31.7.7 带了 **55 种**语言，
 *   保留 zh-CN/en-US 即意味着要删 53 个文件。
 *   而 WorkBuddy 沙箱的 safe-delete shim 对单轮批量删除有 **threshold=50** 的守卫，
 *   第 50 个删除即抛 `SAFE_DELETE_BULK_CONFIRM_REQUIRED` → 打包在「复制 app.asar 之前」中断。
 *   **这不是 electron-builder 缺陷，也不是配置错误，是沙箱与构建工具的结构性冲突。**
 *
 * 对策（仅影响沙箱内的验证打包，不改仓库配置）：
 *   把 electronLanguages 设为 zip 内全部语言 → 待删集合为空 → 不触发删除 → 打包走通。
 *   代价：产物多带 ~10MB 语言包。**正式产线打包仍用仓库原配置**（只留 zh-CN/en-US），
 *   本脚本产物仅用于「arm64 链路是否正确」的结构核对，不可对外发布。
 *
 * 用法：
 *   node tooling/scripts/eb-alllang-config.mjs                    # 写临时配置并打印后续命令
 *   node tooling/scripts/eb-alllang-config.mjs --out <path>      # 指定输出
 *   node tooling/scripts/eb-alllang-config.mjs --electron 31.7.7  # 指定 Electron 版本
 *
 * 后续命令（注意 -c 传的是 build 段本身，不是整包 package.json）：
 *   cd bempdiff
 *   # 解包目录（结构核对用，14s）
 *   npx electron-builder --arm64 --dir \
 *     -c ../<配置路径> --config.directories.output=../release/_arm64_verify
 *   # NSIS 安装包（实测 2m52s，113.5MB —— makensis 不受守卫限制，NSIS 目标可在沙箱内跑通）
 *   npx electron-builder --arm64 \
 *     -c ../<配置路径> --config.directories.output=../release/_arm64_nsis \
 *     --config.nsis.artifactName='BempDiff-${version}-arm64-setup.${ext}'
 *
 * 校验产物内架构（NSIS 是压缩包，需两层解开）：
 *   7z l -t# setup.exe          # 见 3.7z 载荷
 *   7z e -t# setup.exe 3.7z     # 抽出载荷
 *   7z e payload.7z BempDiff.exe "resources\\...\\jre\\bin\\java.exe"
 *   然后读 PE 头 machine 字段：0xaa64=ARM64 / 0x8664=x64
 *   （注意 setup.exe 本体是 0x014c 的 NSIS x86 引导桩，属正常，不是架构错）
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const REPO = path.resolve(HERE, '..', '..')
const BEMP = path.join(REPO, 'bempdiff')
const require = createRequire(path.join(BEMP, 'package.json'))

function parseArgs(argv) {
  const out = { out: null, electron: null }
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--out') out.out = argv[++i]
    else if (argv[i] === '--electron') out.electron = argv[++i]
  }
  return out
}

/** 从 Electron 缓存 zip 里读出全部语言代码；读不到就退回「宁可多留」的已知全集 */
function langsFromCache(electronVersion) {
  const cacheRoot = path.join(os.homedir(), 'AppData', 'Local', 'electron', 'Cache')
  if (!fs.existsSync(cacheRoot)) return null
  const zipName = `electron-v${electronVersion}-win32-arm64.zip`
  let found = null
  const walk = (dir) => {
    if (found) return
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (found) return
      const p = path.join(dir, e.name)
      if (e.isDirectory()) walk(p)
      else if (e.name === zipName) found = p
    }
  }
  walk(cacheRoot)
  if (!found) return null
  // 用 jar/unzip 不便，直接按 zip 中央目录读文件名（只取名字，不解压内容）
  const buf = fs.readFileSync(found)
  const names = []
  const sig = 0x02014b50
  for (let i = 0; i < buf.length - 46; i++) {
    if (buf.readUInt32LE(i) !== sig) continue
    const nameLen = buf.readUInt16LE(i + 28)
    const name = buf.toString('utf8', i + 46, i + 46 + nameLen)
    if (name.startsWith('locales/') && name.endsWith('.pak')) {
      names.push(name.slice('locales/'.length, -'.pak'.length))
    }
  }
  return names.length ? [...new Set(names)].sort() : null
}

const FALLBACK = ('af,am,ar,bg,bn,ca,cs,da,de,el,en-GB,en-US,es,es-419,et,fa,fi,fil,fr,gu,he,hi,hr,hu,id,it,ja,kn,ko,lt,lv,ml,mr,ms,nb,nl,pl,pt-BR,pt-PT,ro,ru,sk,sl,sr,sv,sw,ta,te,th,tr,uk,ur,vi,zh-CN,zh-TW').split(',')

const args = parseArgs(process.argv.slice(2))
const pkg = JSON.parse(fs.readFileSync(path.join(BEMP, 'package.json'), 'utf8'))
const electronVersion = args.electron || pkg.build?.electronVersion
if (!electronVersion) {
  console.error('无法确定 Electron 版本（package.json build.electronVersion 缺失），请用 --electron 指定')
  process.exit(2)
}

const langs = langsFromCache(electronVersion) || FALLBACK
const build = { ...pkg.build, electronLanguages: langs }
const outPath = args.out
  ? path.resolve(REPO, args.out)
  : path.join(REPO, '.workbuddy', 'tmp', 'eb-alllang.json')
fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, JSON.stringify(build, null, 2), 'utf8')

const origLangs = pkg.build?.electronLanguages || []
const cfgRel = path.relative(REPO, outPath).replace(/\\/g, '/')
console.log(`[eb-alllang] Electron ${electronVersion}`)
console.log(`  原 electronLanguages（${origLangs.length}）: ${origLangs.join(',')}`)
console.log(`  新 electronLanguages（${langs.length}）: 全部保留 → 待删集合为空，绕开 safe-delete threshold=50`)
console.log(`  配置已写入: ${cfgRel}`)
console.log('')
console.log('用法（cd bempdiff 后执行；-c 传 build 段本身，不是整包 package.json）：')
console.log('')
console.log('  # 1) 解包目录（结构核对，约 14s）')
console.log(`  npx electron-builder --arm64 --dir -c ../${cfgRel} \\`)
console.log('    --config.directories.output=../release/_arm64_verify')
console.log('')
console.log('  # 2) NSIS 安装包（约 3min，实测 113.5MB + blockmap）')
console.log(`  npx electron-builder --arm64 -c ../${cfgRel} \\`)
console.log('    --config.directories.output=../release/_arm64_nsis \\')
console.log('    --config.nsis.artifactName=\'BempDiff-${version}-arm64-setup.${ext}\'')
console.log('')
console.log('  # 前提：现役 dist_input/jre 必须是 arm64 —— 先跑')
console.log('  node tooling/scripts/switch-jre.mjs --arch arm64')
console.log('')
console.log('提醒：本产物多带 ~10MB 语言包，仅供 arm64 链路核对，勿对外发布；')
console.log('     正式产线打包请走 tooling/scripts/构建打包.bat（用仓库原配置）。')
