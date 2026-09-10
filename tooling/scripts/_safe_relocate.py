# -*- coding: utf-8 -*-
"""安全搬迁 —— 把文件/目录「重命名」到新路径，绝不删除。

本项目铁律：**不受版本控制的目标一律走隔离（重命名到新路径），绝不直接删除。**
（背景：一次清理脚本直接删除未入库的手写 NSIS 脚本、又绕过回收站，导致不可恢复。）

本脚本只做 rename，不调用任何 unlink/rmtree：
  * 整目录搬迁（目标不存在）→ 单次 os.rename，原子且同盘秒级
  * 逐条搬迁（目标已存在 / 指定 --entry）→ 逐项 os.rename
  * 文件搬迁默认做 sha256 前后比对，不一致即报错
  * 目标已存在时**拒绝覆盖**（除非显式 --allow-overwrite）
  * 只有源目录搬空后才移除空壳目录，且用 os.rmdir（仅能删空目录，删不掉有内容的）

用法：
    python _safe_relocate.py --src ./logs/_tmp --dst ../.cleanup-quarantine/proj-20260910
    python _safe_relocate.py --src a.txt --dst ./archive/a.txt --dry-run
    python _safe_relocate.py --dst DIR --entry logs/_tmp1 --entry logs/_tmp2   # 多条目进同一目标
"""
import argparse
import hashlib
import os
import sys


def sha256(path, chunk=1 << 20):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def summarize(path, limit=40):
    n_files = n_dirs = size = 0
    for dp, dn, fn in os.walk(path):
        n_dirs += len(dn)
        for f in fn:
            n_files += 1
            try:
                size += os.path.getsize(os.path.join(dp, f))
            except OSError:
                pass
    return n_files, n_dirs, size


def move_one(src, dst, dry_run, allow_overwrite, verify):
    src = os.path.abspath(src)
    dst = os.path.abspath(dst)
    if not os.path.exists(src):
        print("  跳过（源不存在）: %s" % src)
        return False, 0
    if os.path.exists(dst):
        if not allow_overwrite:
            raise SystemExit("目标已存在，拒绝覆盖（加 --allow-overwrite 可强制）: %s" % dst)
        print("  !! 目标已存在且允许覆盖: %s" % dst)

    if os.path.isdir(src):
        n_files, n_dirs, size = summarize(src)
        print("  目录 %s\n       -> %s\n       文件 %d / 子目录 %d / %.1f KB"
              % (src, dst, n_files, n_dirs, size / 1024))
        if dry_run:
            return True, size
        parent = os.path.dirname(dst)
        if parent and not os.path.isdir(parent):
            os.makedirs(parent, exist_ok=True)
        os.rename(src, dst)          # 原子；同盘即时
        print("      moved=%s" % (not os.path.exists(src)))
        return True, size

    h_before = sha256(src) if verify else None
    size = os.path.getsize(src)
    print("  文件 %s (%d B)\n       -> %s" % (src, size, dst))
    if dry_run:
        return True, size
    parent = os.path.dirname(dst)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent, exist_ok=True)
    os.rename(src, dst)
    if verify:
        h_after = sha256(dst)
        if h_after != h_before:
            raise SystemExit("哈希不一致！源=%s 目标=%s" % (h_before[:16], h_after[:16]))
        print("      sha256 %s OK" % h_after[:16])
    return True, size


def main():
    ap = argparse.ArgumentParser(description="安全搬迁（只 rename，不删除）")
    ap.add_argument("--src", help="源文件或目录（与 --entry 二选一）")
    ap.add_argument("--entry", action="extend", nargs="+", default=[],
                    help="待搬迁条目路径，可重复/可一次给多个；与 --dst 组合进同一目标目录")
    ap.add_argument("--dst", required=True, help="目标文件路径（--src 为文件）或目标目录")
    ap.add_argument("--dry-run", action="store_true", help="只预览，不实际移动")
    ap.add_argument("--allow-overwrite", action="store_true", help="允许覆盖已存在的目标")
    ap.add_argument("--no-verify", action="store_true", help="跳过文件 sha256 校验")
    ap.add_argument("--remove-empty-src-dir", action="store_true",
                    help="整目录搬迁后移除遗留的空源目录（os.rmdir，仅能删空目录）")
    args = ap.parse_args()

    if not args.src and not args.entry:
        ap.error("需要 --src 或至少一个 --entry")
    if args.src and args.entry:
        ap.error("--src 与 --entry 互斥")

    print("=" * 74)
    print("安全搬迁%s" % ("  [DRY-RUN 预览]" if args.dry_run else ""))
    print("=" * 74)

    moved, total = 0, 0
    if args.src:
        ok, size = move_one(args.src, args.dst, args.dry_run,
                            args.allow_overwrite, not args.no_verify)
        moved += 1 if ok else 0
        total += size
        src = os.path.abspath(args.src)
        if ok and not args.dry_run and args.remove_empty_src_dir \
                and os.path.isdir(src) and not os.listdir(src):
            os.rmdir(src)
            print("  已移除空源目录: %s" % src)
    else:
        if not args.dry_run and not os.path.isdir(args.dst):
            os.makedirs(args.dst, exist_ok=True)
        for e in args.entry:
            ok, size = move_one(e, os.path.join(args.dst, os.path.basename(
                os.path.normpath(e))), args.dry_run, args.allow_overwrite,
                not args.no_verify)
            moved += 1 if ok else 0
            total += size

    print("-" * 74)
    print("完成：%d 项 / %.2f MB%s" % (moved, total / 1048576,
                                   "（预览，未落盘）" if args.dry_run else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
