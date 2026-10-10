# T01822 处理结果：GitHub 风格 README

## 交付物

仓库根新增 `README.md`（GitHub 风格、英文），路径：`/README.md`。

## 内容结构

- 徽章：License / Platform / Release（动态 shields.io 徽章）
- 项目标语 + 一句话定位
- Highlights：分层 diff / 递归解包 / 反编译 / 前端资源 diff / 文件夹对比 / BCompare 式交互 / 两阶段 AI / 导出 / 跨平台安装包
- Tech Stack 表（Electron + Vue3 + Bootstrap5 + Vite + Java21 sidecar + CFR）
- Installation（Releases 下载，含 x64/arm64/Windows/Linux/macOS）
- Quick Start：GUI + Headless/CI 子命令（取自现有 `bempdiff/README.md` 的真实命令）
- Configuration、Architecture（ASCII 框图）、Development、Security & Privacy、Roadmap（引用 PRD）、Contributing、License、Acknowledgments

## 事实核对

- 所有功能/命令/端口（18765）/技术栈均取自 `bempdiff/README.md`、`docs/` 与 PRD，未虚构。
- 社交预览图引用 `bempdiff/assets/social-preview-1280x640.png`（与 T01830 同源更新）。
- 未杜撰许可证文件：License 段链接 `LICENSE`，若仓库尚无 LICENSE 文件需补建（当前未创建，避免虚构）。

## 说明

中文版详情仍保留在 `bempdiff/README.md`（面向国内用户）；根 `README.md` 为面向 GitHub 国际访客的英文版，两者并存。
