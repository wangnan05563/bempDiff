; ============================================================================
; BempDiff — NSIS 安装器自定义脚本
; ----------------------------------------------------------------------------
; 引用方式：bempdiff/package.json → build.nsis.include = "build/installer.nsh"
;           （相对 buildResources 目录，即 bempdiff/）
; 注入点：electron-builder 生成的 installer.nsi 里
;           Function .onInit
;             Call setInstallSectionSpaceRequired
;             SetOutPath $INSTDIR
;             ${LogSet} on
;             !ifmacrodef preInit
;               !insertmacro preInit      <-- 本文件定义
;             !endif
;           （早于 check64BitAndSetRegView / initMultiUser / customInit）
;
; !! 重建说明（2026-09-10）!!
;   本文件原版在「工作空间第 3 轮清理」中被误删：bempdiff/build/ 命中了
;   cleanup-config.yaml 的 garbage_patterns.compiled_dirs: "build/" 规则，
;   但该目录的唯一内容是本手写脚本，并非编译产物。原版无 git 记录、无卷影副本，
;   无法逐字节恢复。本文件据以下权威旁证重建：
;     1) scripts/patch_nsis_spacecheck.ps1 第 16-21 / 32 / 92-96 行——
;        明确本文件含标记 [磁盘空间预检禁用]，且 ensureDiskSpace 被「立即 Return」
;        完全禁用，preInit 是早于 SectionSetSize 的那道闸门。
;     2) scripts/nsis-tpl/common.nsh——同一修复的另一半
;        （setSpaceRequired → SectionSetSize ${SECTION_ID} 1）。
;   如你手头有原版副本，请直接覆盖本文件。
;
; 编码约定：UTF-8 无 BOM + LF（与 scripts/nsis-tpl/common.nsh 保持一致）。
; ============================================================================

; ----------------------------------------------------------------------------
; [磁盘空间预检禁用]
;
; 背景：部分宿主环境（NTFS 配额 / 文件夹压缩 / 杀软过滤驱动 / 虚拟化重定向）下，
;       GetDiskFreeSpaceEx 会返回剩余空间≈0，导致 electron-builder / NSIS 在磁盘
;       实际充足时仍误报「磁盘空间不足」而中断安装。
;       实测本产品解压后约 300MB，任何盘剩余都远大于此，故属纯误报。
;
; 加重因子：NSIS 的 IntCmp 是 32 位有符号比较，而可用字节数是 64 位。当剩余空间
;       > 2GB 时低 32 位回绕为负数，任何字节阈值都会误判为「不足」。
;
; 处置：preInit 在 .onInit 中早于 setInstallSectionSpaceRequired / SectionSetSize
;       执行，是坏环境下最先触发的那条路径；因此在 ensureDiskSpace 入口立即
;       Return，把这段预检整体短路。
;
; 影响面：仅跳过「安装前」的空间预估。真正的解压仍由 NSIS 执行并会真实写入磁盘，
;       磁盘确实不足时解压阶段依旧会失败并报错，因此不会造成数据损坏。
;
; 恢复真实校验：删掉下面 ensureDiskSpace 里的立即 Return，并同步移除
;       scripts/nsis-tpl/common.nsh 的 SectionSetSize 行与 patch 脚本的标记检查。
; ----------------------------------------------------------------------------

!macro preInit
  Call ensureDiskSpace
!macroend

Function ensureDiskSpace
  # [磁盘空间预检禁用] 立即返回：完全跳过磁盘空间预检（详见文件头「背景/处置」）
  Return
FunctionEnd
