#!/usr/bin/env bash
# ============================================================
# BempDiff 临时文件/磁盘缓存回收脚本（Linux / macOS / 任意 bash）
#
# 背景：BempDiff 会在系统临时目录生成 bempdiff-* 临时文件、在
#   $HOME/.bempdiff/runtime 生成逐层解包原子文件、在
#   $HOME/.bempdiff/decompile-cache 缓存反编译源码。
#   这些文件多依赖「进程退出 deleteOnExit」兜底，进程强杀/崩溃或长驻不重启时
#   进程内清理不触发，磁盘持续累积。
#
# 本脚本按"修改时间早于 N 天"的安全阈值回收，单任务解包生命周期分钟级 < N 天，
# 故不会中断进行中的解包任务。
# 设计：只匹配 BempDiff 已知前缀/目录；单文件失败静默跳过；默认保留 N(3) 天；
#       建议在业务低峰期（如 02:00）由 cron/systemd timer 调用。
#
# 用法：
#   bash cleanup_temp.sh            # 实删，保留 3 天
#   bash cleanup_temp.sh --days 7   # 保留 7 天
#   bash cleanup_temp.sh --preflight# 演练，仅打印不删除
#   bash cleanup_temp.sh --purge-cache --cache-max-mb 512
# ============================================================

set -uo pipefail

DAYS=3
PURGE_CACHE=0
CACHE_MAX_MB=512
PREFLIGHT=0
DRY_ECHO=""

while [ $# -gt 0 ]; do
  case "$1" in
    --days) DAYS="$2"; shift 2 ;;
    --purge-cache) PURGE_CACHE=1; shift ;;
    --cache-max-mb) CACHE_MAX_MB="$2"; shift 2 ;;
    --preflight) PREFLIGHT=1; shift ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

(( DAYS < 1 )) && DAYS=1   # 至少 1 天，避免误删进行中任务

TMPDIR_PATH="${TMPDIR:-/tmp}"
HOME_DOT="${HOME}/.bempdiff"
RUN_ROOT="${HOME_DOT}/runtime"
LOG_ROOT="${HOME_DOT}/logs"
CACHE_DIR="${HOME_DOT}/decompile-cache"

TOTAL_FILES=0
TOTAL_DIRS=0
TOTAL_BYTES=0
CACHE_FREED_BYTES=0

log() { echo "$*"; }
free_count() {
  TOTAL_FILES=$((TOTAL_FILES + $1)); TOTAL_DIRS=$((TOTAL_DIRS + $2)); TOTAL_BYTES=$((TOTAL_BYTES + $3))
}

rm_ok() { # rm_ok <path> <is_dir>
  if [ "$PREFLIGHT" -eq 1 ]; then
    echo "  (演练) rm $1"
  else
    if [ "${2:-0}" -eq 1 ]; then
      rm -rf -- "$1" 2>/dev/null && return 0
    else
      rm -f -- "$1" 2>/dev/null && return 0
    fi
    return 1      # 被占用/权限：静默跳过
  fi
}

echo "= [1/4] 系统临时文件 (dir=$TMPDIR_PATH, older than ${DAYS}d) ="
if [ -d "$TMPDIR_PATH" ]; then
  for pat in 'bempdiff-*.class' 'bempdiff-*.bin' 'bempdiff-*.jar' 'bempdiff-*.zip'; do
    while IFS= read -r -d '' f; do
      sz=$(stat -c %s "$f" 2>/dev/null || echo 0)
      free_count 1 0 "$sz"
      rm_ok "$f" 0
    done < <(find "$TMPDIR_PATH" -maxdepth 2 -type f -name "$pat" -mtime +"$DAYS" -print0 2>/dev/null)
  done
  while IFS= read -r -d '' d; do
    sz=$(du -sb "$d" 2>/dev/null | awk '{print $1}')
    free_count 0 1 "$sz"
    rm_ok "$d" 1
  done < <(find "$TMPDIR_PATH" -maxdepth 2 -type d -name 'bempdiff-unpack-*' -mtime +"$DAYS" -print0 2>/dev/null)
fi

echo "= [2/4] 作业解包运行时目录 (dir=$RUN_ROOT) ="
if [ -d "$RUN_ROOT" ]; then
  while IFS= read -r -d '' d; do
    sz=$(du -sb "$d" 2>/dev/null | awk '{print $1}')
    free_count 0 1 "$sz"
    rm_ok "$d" 1
  done < <(find "$RUN_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +"$DAYS" -print0 2>/dev/null)
fi

if [ "$PURGE_CACHE" -eq 1 ]; then
  echo "= [3/4] decompile-cache 回收 (dir=$CACHE_DIR, cap=${CACHE_MAX_MB}MB) ="
  if [ -d "$CACHE_DIR" ]; then
    total=$(du -sb "$CACHE_DIR" 2>/dev/null | awk '{print $1}')
    limit=$((CACHE_MAX_MB * 1024 * 1024))
    if [ "$total" -gt "$limit" ]; then
      # 超上限：按 mtime 升序淘汰最旧 .dec 直到回到上限
      while [ "$(du -sb "$CACHE_DIR" 2>/dev/null | awk '{print $1}')" -gt "$limit" ]; do
        f=$(find "$CACHE_DIR" -maxdepth 1 -type f -name '*.dec' -print0 2>/dev/null | xargs -0 -r ls -t 2>/dev/null | tail -n 1)
        [ -z "$f" ] && break
        sz=$(stat -c %s "$f" 2>/dev/null || echo 0)
        if rm_ok "$f" 0; then CACHE_FREED_BYTES=$((CACHE_FREED_BYTES + sz)); fi
      done
    fi
    # 按天：回收过老缓存
    while IFS= read -r -d '' f; do
      sz=$(stat -c %s "$f" 2>/dev/null || echo 0)
      if rm_ok "$f" 0; then CACHE_FREED_BYTES=$((CACHE_FREED_BYTES + sz)); fi
    done < <(find "$CACHE_DIR" -maxdepth 1 -type f -name '*.dec' -mtime +"$DAYS" -print0 2>/dev/null)
  fi
fi

echo "= [4/4] 运行日志 (dir=$LOG_ROOT) ="
if [ -d "$LOG_ROOT" ]; then
  while IFS= read -r -d '' f; do
    sz=$(stat -c %s "$f" 2>/dev/null || echo 0)
    free_count 1 0 "$sz"
    rm_ok "$f" 0
  done < <(find "$LOG_ROOT" -type f -mtime +"$DAYS" -print0 2>/dev/null)
fi

echo ""
echo "完成。文件 $TOTAL_FILES 个 / 目录 $TOTAL_DIRS 个，回收约 $((TOTAL_BYTES/1024/1024)) MB；cache 另回收 $((CACHE_FREED_BYTES/1024/1024)) MB。"
[ "$PREFLIGHT" -eq 1 ] && echo "(演练模式：未真正删除任何文件)"