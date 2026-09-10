# -*- coding: utf-8 -*-
"""双重编码（mojibake）扫描器 —— 比较法 + 已知样本自检。

用途：扫描目录树下的文本文件，找出「UTF-8 字节被按 CP936/GBK 误读」造成的乱码。

判据（三条同时满足才判定为乱码，避免把正常中文误判）：
    1. 非 ASCII 字符数 >= --min-non-ascii（默认 50）
    2. 逆转分 >= --threshold（非 ASCII 片段能按 GBK→UTF-8 逆转的比例；正常中文会失败 → 低分）
    3. 逆转后常用词命中数 > 原文常用词命中数（乱码原文几乎不含中文常用词）

用法：
    python mojibake_scan.py --root ~/.workbuddy/skills
    python mojibake_scan.py --root ./docs --out scan.json --ext .md,.yaml
    python mojibake_scan.py --root DIR --samples samples.txt   # 含已知乱码样本的自检清单

退出码：0 = 未发现乱码；1 = 发现 CONFIRMED-MOJIBAKE（可用于 CI 门禁）。
"""
import argparse
import json
import os
import re
import sys

NON_ASCII_RUN = re.compile(r"[^\x00-\x7f]+")
DEFAULT_EXTS = ".md,.yaml,.yml,.json,.txt"
DEFAULT_SKIP_DIR_PARTS = ".backup-,node_modules,.git,__pycache__,output"
COMMON = ["的", "是", "文件", "目录", "配置", "清理", "使用", "规则", "参数", "路径",
          "默认", "检查", "报告", "执行", "生成", "项目", "可以", "以及", "说明", "示例",
          "日志", "分析", "修复", "问题", "任务", "技能", "文档", "结构", "内容", "版本"]


def reverse_score(text):
    """返回 (逆转分, 非 ASCII 片段总数)。"""
    clean = [0]
    total = [0]

    def one_segment(seg):
        total[0] += 1
        try:
            seg.encode("gbk").decode("utf-8")
            clean[0] += 1
            return
        except Exception:
            pass
        i, n, hit_any = 0, len(seg), False
        while i < n:
            hit = False
            for j in range(n, i, -1):
                try:
                    seg[i:j].encode("gbk").decode("utf-8")
                    i = j
                    hit = True
                    hit_any = True
                    break
                except Exception:
                    continue
            if not hit:
                i += 1
        if hit_any:
            clean[0] += 0.5

    NON_ASCII_RUN.sub(lambda m: (one_segment(m.group(0)), "")[1], text)
    return ((clean[0] / total[0]) if total[0] else 0.0), total[0]


def reverse_text(text):
    """对每段非 ASCII 做 GBK→UTF-8 逆转，失败段保留原文。"""
    def one(m):
        seg = m.group(0)
        try:
            return seg.encode("gbk").decode("utf-8")
        except Exception:
            pass
        buf, i, n = [], 0, len(seg)
        while i < n:
            hit = False
            for j in range(n, i, -1):
                try:
                    buf.append(seg[i:j].encode("gbk").decode("utf-8"))
                    i = j
                    hit = True
                    break
                except Exception:
                    continue
            if not hit:
                buf.append(seg[i])
                i += 1
        return "".join(buf)
    return NON_ASCII_RUN.sub(one, text)


def count_hits(text):
    return sum(text.count(w) for w in COMMON)


def judge(rel, text, min_non_ascii, threshold):
    nz = len([c for c in text if ord(c) > 127])
    if nz < min_non_ascii:
        return None
    sc, seg = reverse_score(text)
    rev = reverse_text(text)
    o, r = count_hits(text), count_hits(rev)
    is_bad = (sc >= threshold) and (r > o)
    return {"rel": rel, "non_ascii": nz, "score": round(sc, 3), "seg": seg,
            "hits_orig": o, "hits_rev": r,
            "verdict": "CONFIRMED-MOJIBAKE" if is_bad else "HEALTHY",
            "bytes": None}


def read_text(path):
    return open(path, "rb").read().decode("utf-8-sig")


def self_test(root, samples, min_non_ascii, threshold):
    print("=" * 78)
    print("自检（已知样本；未列出即表示该文件不存在）")
    print("=" * 78)
    for rel in samples:
        p = rel if os.path.isabs(rel) else os.path.join(root, rel)
        if not os.path.isfile(p):
            print("  %-46s (不存在)" % rel)
            continue
        j = judge(rel, read_text(p), min_non_ascii, threshold)
        if j:
            print("  %-46s %s  score=%.2f  hits orig=%d rev=%d" %
                  (rel, j["verdict"], j["score"], j["hits_orig"], j["hits_rev"]))
    print()


def main():
    ap = argparse.ArgumentParser(description="双重编码（mojibake）扫描器")
    ap.add_argument("--root", required=True, help="扫描根目录")
    ap.add_argument("--out", default="mojibake_scan.json", help="JSON 结果输出路径")
    ap.add_argument("--ext", default=DEFAULT_EXTS, help="扫描扩展名，逗号分隔")
    ap.add_argument("--skip-dir", default=DEFAULT_SKIP_DIR_PARTS,
                    help="目录名片段黑名单，逗号分隔（子串匹配即跳过）")
    ap.add_argument("--min-non-ascii", type=int, default=50, help="非 ASCII 字符数下限")
    ap.add_argument("--threshold", type=float, default=0.40, help="逆转分阈值")
    ap.add_argument("--samples", help="自检清单文件（每行一个相对/绝对路径）")
    args = ap.parse_args()

    root = os.path.abspath(os.path.expanduser(args.root))
    if not os.path.isdir(root):
        print("根目录不存在: %s" % root, file=sys.stderr)
        return 2
    exts = {e.strip().lower() for e in args.ext.split(",") if e.strip()}
    skip = tuple(s.strip() for s in args.skip_dir.split(",") if s.strip())

    samples = []
    if args.samples and os.path.isfile(args.samples):
        samples = [ln.strip() for ln in open(args.samples, encoding="utf-8")
                   if ln.strip() and not ln.startswith("#")]
    if samples:
        self_test(root, samples, args.min_non_ascii, args.threshold)

    rows = []
    for dp, dn, fn in os.walk(root):
        dn[:] = [d for d in dn if not any(p in d for p in skip)]
        for f in fn:
            if os.path.splitext(f)[1].lower() not in exts or f.endswith(".orig"):
                continue
            p = os.path.join(dp, f)
            try:
                j = judge(os.path.relpath(p, root), read_text(p),
                          args.min_non_ascii, args.threshold)
            except Exception as ex:
                print("  跳过（读取失败）%s: %s" % (p, ex), file=sys.stderr)
                continue
            if j:
                j["bytes"] = os.path.getsize(p)
                rows.append(j)

    confirmed = [r for r in rows if r["verdict"] == "CONFIRMED-MOJIBAKE"]
    print("=" * 78)
    print("扫描结果  root=%s" % root)
    print("=" * 78)
    print("  达标文件: %d | CONFIRMED-MOJIBAKE: %d | HEALTHY: %d"
          % (len(rows), len(confirmed), len(rows) - len(confirmed)))
    if confirmed:
        for r in sorted(confirmed, key=lambda x: -x["non_ascii"]):
            print("  x 非ASCII=%-6d %8d B score=%.2f hits %d->%d  %s"
                  % (r["non_ascii"], r["bytes"], r["score"], r["hits_orig"],
                     r["hits_rev"], r["rel"]))
    else:
        print("  OK 未发现确认乱码")

    out = os.path.abspath(os.path.expanduser(args.out))
    parent = os.path.dirname(out)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent, exist_ok=True)
    try:
        with open(out, "w", encoding="utf-8") as f:
            json.dump({"confirmed": confirmed, "all": rows}, f, ensure_ascii=False, indent=1)
        print("\nwritten: %s" % out)
    except OSError as ex:
        print("\n结果写入失败(%s)，仅输出到终端: %s" % (ex, out), file=sys.stderr)
    return 1 if confirmed else 0
    return 1 if confirmed else 0


if __name__ == "__main__":
    sys.exit(main())
