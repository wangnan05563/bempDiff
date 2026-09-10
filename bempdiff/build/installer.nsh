; ============================================================================
; BempDiff — NSIS 安装器自定义脚本
; ----------------------------------------------------------------------------
; 引用方式：bempdiff/package.json → build.nsis.include = "build/installer.nsh"
;           （相对 buildResources 目录，即 bempdiff/）
; 编码约定：UTF-8 **带 BOM** + CRLF（项目约定：含中文的安装脚本必须带 BOM）
;
; !! 重建说明（2026-09-10）!!
;   本文件原版在「工作空间第 3 轮清理」中被误删（bempdiff/build/ 命中
;   cleanup-config.yaml 的 garbage_patterns.compiled_dirs 裸模式 "build/"，但该目录
;   实为 electron-builder 的 buildResources 源码目录）。原版无 git 记录、无卷影副本、
;   无回收站副本，无法逐字节恢复。
;   本文件按 Trae 项目记忆（该文件的创建会话，2026-08-30）的权威事实重建，
;   覆盖三块被此前遗漏的内容：customInit/customUnInstall 的 taskkill、
;   !ifndef BUILD_UNINSTALLER 包裹、UTF-8 BOM 编码。
;   如你手头有原版副本，请直接覆盖本文件。
; ============================================================================

; ----------------------------------------------------------------------------
; 一、安装/卸载前强制关闭运行实例
;   动机：electron-builder 24.13.3 无 nsis.killRunningApp；若 BempDiff.exe（及其
;         javaw 后端 sidecar）在运行，安装器覆盖 exe/jar/JRE DLL 时会因文件占用失败
;         （实测触发 "Access is denied"，并可能把旧 jar 打进包里造成 STALE）。
;   做法：经 nsis.include 注入 taskkill，/t 连子进程树一起结束（覆盖 javaw sidecar）。
;   注入点：customInit（安装前，.onInit 内）/ customUnInstall（卸载时）。
; ----------------------------------------------------------------------------

!macro customInit
  DetailPrint "正在关闭已运行的 BempDiff 实例 ..."
  nsExec::ExecToLog 'taskkill /f /t /im BempDiff.exe'
!macroend

!macro customUnInstall
  DetailPrint "正在关闭 BempDiff 进程 ..."
  nsExec::ExecToLog 'taskkill /f /t /im BempDiff.exe'
!macroend

; ----------------------------------------------------------------------------
; 二、[磁盘空间预检禁用]
;   背景：部分宿主环境（NTFS 配额 / 文件夹压缩 / 杀软过滤驱动 / 虚拟化重定向）下，
;         GetDiskFreeSpaceEx 返回剩余空间≈0，导致 electron-builder / NSIS 在磁盘
;         实际充足时仍误报「磁盘空间不足」而中断安装。
;         加重因子：NSIS 的 IntCmp 是 32 位有符号比较，可用字节为 64 位，剩余 >2GB
;         时低 32 位回绕为负 → 任何字节阈值都会误判为「不足」。
;   演变：初版 preInit 会在安装早期主动检查 $INSTDIR 所在盘与系统盘剩余空间，
;         不足 512MB 则中止并给出提示，查询失败则跳过；后续确认该预检本身即为误报源，
;         故将 ensureDiskSpace 改为**立即 return**，彻底绕过预检
;         （另一半修复在 scripts/nsis-tpl/common.nsh：setSpaceRequired → SectionSetSize 1）。
;   影响面：仅跳过「安装前」的空间预估。真正解压仍由 NSIS 执行并会真实写入磁盘，
;         磁盘确实不足时解压阶段依旧会失败报错，因此不会造成数据损坏。
;   编译约束（务必保留）：BUILD_UNINSTALLER 过渡卸载器以 -WX（警告即错误）编译，
;         未被引用的 ensureDiskSpace 会触发 warning 6010 而使构建失败 →
;         preInit 宏与 ensureDiskSpace 函数必须整体包在 !ifndef BUILD_UNINSTALLER 内。
;   恢复真实校验：删除 ensureDiskSpace 的立即 Return，并同步移除
;         scripts/nsis-tpl/common.nsh 的 SectionSetSize 行与 patch 脚本的标记检查。
; ----------------------------------------------------------------------------

!ifndef BUILD_UNINSTALLER

  !macro preInit
    Call ensureDiskSpace
  !macroend

  Function ensureDiskSpace
    # [磁盘空间预检禁用] 立即返回：完全跳过磁盘空间预检（详见上方「二」）
    Return
  FunctionEnd

!endif
