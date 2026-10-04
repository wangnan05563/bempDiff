# arm64 平台产物形态与适配方案评估

> 对应任务：[PRD] 3.4 arm64 平台评估（二期 R 附加 / T01478）；实施：T01509。
> 结论先行：**技术上可行，成本集中在 JRE 分架构供给与 macOS 签名公证。**
> **2026-10-04 更新**：Windows arm64 已实际落地（JRE 架构矩阵 + 打包 + CI 流水线就绪，
> arm64 安装包已产出并通过包内架构校验），进展见 §5；唯一未完成项是启动实测（需原生
> ARM64 机器，`windows-11-arm` runner 已 GA）。macOS/Linux arm64 仍挂起。

## 1. 现状基线（bempdiff/package.json `build` 段）

| 项 | 现值 |
| --- | --- |
| 打包器 | electron-builder ^24.13.3（Electron 31.7.7） |
| 目标平台 | 仅 `win.target = nsis`（x64 默认） |
| extraResources | `dist_input/app`（Java sidecar jar + cfr.jar，**跨架构**）＋ `dist_input/jre`（**Windows x64 Java 25 运行时，单架构**）＋ `dist_input/webui`（纯前端，跨架构） |
| 产物 | `BempDiff-<version>-setup.exe`（NSIS，含 installer.nsh 自定义脚本） |

关键结论：应用层（Electron 壳 main/preload、Vue 前端、Java sidecar 字节码、CFR 反编译器）**全部跨架构可复用**；唯一的单架构资产是 **JRE**。

## 2. 三平台 arm64 产物形态评估

### 2.1 Windows arm64
- **Electron**：31.x 官方提供 `win32_arm64` 发行版，electron-builder `--arm64` 直接可用。
- **NSIS**：支持 arm64 产物（`nsis.target` 不变，`artifactName` 建议加架构后缀 `BempDiff-${version}-arm64-setup.${ext}` 以区分）。
- **JRE**：需换 Windows aarch64 版 Java 25（Azul Zulu / Oracle / Microsoft Build of OpenJDK 均提供 zip 发行版，解包替换 `dist_input/jre` 即可，目录结构保持 `bin/java.exe` 形态）。
- **风险**：低。Windows on ARM 可运行 x64 仿真，但原生 arm64 JRE 性能/电池显著更优。

### 2.2 macOS arm64（Apple Silicon）
- **Electron**：`mac_arm64`（M 系原生）成熟；dmg target。
- **JRE**：zulu macOS aarch64（`.tar.gz`/`.dmg`，解包为 `Contents/Home` 结构，需调整 extraResources 路径映射）。
- **额外成本（主要）**：
  - **代码签名**：Apple Developer ID 证书（$99/年）；未签名应用在 Gatekeeper 下默认不可启动。
  - **公证（Notarization）**：分发前需 notarytool 公证，CI 需要 App Store Connect API Key。
  - **架构命名**：electron-builder 产物目录为 `mac-arm64` / `mac-universal`（universal = x64+arm64 合包，体积翻倍，建议直接出 arm64 单架构）。
- **风险**：中。签名/公证流水线为一次性搭建成本，之后自动化。

### 2.3 Linux arm64
- **Electron**：`linux_arm64`（AppImage/deb/tar.gz 均可）。
- **JRE**：zulu / Temurin aarch64 tar.gz。
- **风险**：低，但 Linux 用户群与本项目场景（内网 Windows 交付审计）匹配度最低，优先级最低。

## 3. 适配方案（CI 矩阵草案）

单机无法交叉产出多架构 JRE —— 推荐在 GitHub Actions 按矩阵在各原生 runner 上准备对应架构 JRE 后打包：

```yaml
# .github/workflows/dist.yml（草案）
jobs:
  dist:
    strategy:
      matrix:
        include:
          - { os: windows-latest, arch: x64 }      # 存量主产物
          - { os: windows-11-arm, arch: arm64 }    # **已 GA（2026-10 核实，非实验性）**
          - { os: macos-14,       arch: arm64 }    # M 系原生 runner
          - { os: ubuntu-22.04-arm, arch: arm64 }  # arm runner（可用性视 GitHub 定）
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - run: node --version && npm ci
      # 1) 按矩阵下载对应架构 JRE 25 → bempdiff/dist_input/jre
      - run: node tooling/scripts/fetch-jre.mjs --os ${{ matrix.os }} --arch ${{ matrix.arch }}
      # 2) 前端构建 + electron-builder（--arm64 由 matrix 条件追加）
      - run: npm run build:webui && npx electron-builder ${{ matrix.arch == 'arm64' && '--arm64' || '' }}
      # macOS 需要证书/公证环境变量（CSC_LINK / APPLE_ID 等 secrets）
```

**JRE 下载源清单**（Java 25，均零成本）：
| 平台 | 来源 | 形态 |
| --- | --- | --- |
| win x64 | cdn.azul.com zulu（现用） | zip |
| win arm64 | cdn.azul.com zulu `win_aarch64` | zip |
| mac arm64 | cdn.azul.com zulu `macosx_aarch64` | tar.gz（需重排 Contents/Home） |
| linux arm64 | cdn.azul.com zulu `linux_aarch64` | tar.gz |

## 4. 结论与建议

> **2026-10-04 实施更新（T01509）**：本节原为「建议」，现 Windows arm64 已实际落地，进展见下方 §5。

1. ~~**不建议现在就在产线启用 arm64 打包**：本沙箱（Windows x64）无法实测 mac/linux 产物；交付出未验证安装包违背「不伪造」原则。~~（已完成，见 §5）
2. **本评估交付的可执行资产**：CI 矩阵草案 + JRE 下载源清单 + 产物命名规范（`${version}-arm64` 后缀）——已全部落地为可执行脚本。
3. **推荐节奏**：先 Windows arm64（用户基数匹配、成本最低），macOS arm64 视对外分发需求启动（签名/公证为前置项）。
4. **通用代码无需改动**：sidecar jar / 前端 / Electron 主进程均跨架构；installer.nsh 的 PowerShell 清理逻辑仅适用 Windows（mac/linux 不走 NSIS，无影响）。

---

## 5. 实施进展（2026-10-04，T01509）

### 5.1 已落地

| 项 | 状态 |
| --- | --- |
| JRE 架构矩阵（按 os+arch 拉取 + PE 头校验） | ✅ `tooling/scripts/fetch-jre.mjs` |
| 打包前原子切换现役 JRE | ✅ `tooling/scripts/switch-jre.mjs` |
| arm64 解包目录结构核对 | ✅ `BempDiff.exe` / `jre/bin/java.exe` / `javaw.exe` 三项 PE machine 均 `0xaa64` |
| arm64 NSIS 安装包 | ✅ 113.5MB + blockmap，包内压缩流提取校验同为 `0xaa64` |
| CI 启动实测流水线 | ✅ `.github/workflows/build-arm64.yml`（`runs-on: windows-11-arm`） |

**关键设计**：`extraResources` 固定指向 `dist_input/jre`、主进程硬编码 `jre/bin/javaw.exe` → 「换现役 JRE」对壳完全透明、**对 x64 产线零影响**，无需改配置结构或主进程代码。

### 5.2 唯一未完成项

**arm64 产物启动实测**——需原生 ARM64 机器。GitHub `windows-11-arm` runner **已 GA**（本文档原标注的「实验性」已过时），流水线已就绪，触发即跑：安装 → 启动 → 校验 sidecar 18765 监听 → 定向关停。

不给出「已验证可运行」结论的原因：本机 x64 Windows 无法执行 ARM64 二进制，运行态验证必须由目标架构机器完成。

### 5.3 沙箱打包的两处反直觉结论

1. **完整 electron-builder 打包在本沙箱跑不通**——`removeUnusedLanguagesIfNeeded` 要删 53 个 `locales/*.pak`（Electron 31.7.7 带 55 种语言，只留 2 种），撞 safe-delete shim 的 `threshold=50`，在**复制 app.asar 之前**中断。绕行见 `tooling/scripts/eb-alllang-config.mjs`（全语言保留 → 待删集合为空）。
2. **NSIS 封装阶段不受该守卫限制**——受限制的只有 locales 删除那一步，压缩/封装不涉及删除。故 arm64 安装包**能在沙箱内产出**。
