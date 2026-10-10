# T01830 处理结果：GitHub 社交预览图（1280×640）

## 交付物

更新 `bempdiff/assets/social-preview-1280x640.png`（严格 1280×640 PNG）。

## 制作流程

1. **AI 生成底图**：基于现有 logo（`bempdiff/bempdiff-logo.png`）走 Agnes Image 2.5 Flash（文生/图生图，免费）生成右侧留白底图——左侧 logo + 深蓝斜向渐变（#2B63EC→#12296E），2K/16:9 输出。
2. **精确裁切**：16:9 底图中心裁成 2:1 后缩至 1280×640（Lanczos），避免变形/裁剪 logo。
3. **叠字**：用系统 Segoe UI 字体叠加清晰标题 **BempDiff** + 绿色（#10B981）下划线重音 + 两行标语（"Package Diff Tool with AI Analysis" / "for Java / War / JAR / Zip"）。
   - 直接让模型出文字会出现乱码/中文字符瑕疵，故改为「AI 出背景 + 本地字体叠字」保证标语零错误。

## 验证

- 尺寸严格 1280×640（已 `Image.size` 确认），无变形、logo 未失真。
- 文字清晰、符合 GitHub 社交预览规范（顶部/底部约 40px 安全边距已留白）。
- 中间产物留存：`bempdiff/assets/_wip_bg_20261010.png`（底图）、`_wip_v1_20261010.png`（首稿）。

## 关联

- T01822 README 已引用本图作为社交预览。
