#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BempDiff 性能测试夹具生成器（test-only，不进产品）。

产出：
  bempdiff/fixtures/small_real_v1.jar / v2.jar
      - 24 个真实 .class（取自 lib_v1.jar），其中 MODIFIED=4 / ADDED=2 / DELETED=2。
      - 用途：ai / report / export / 小批量 decompile 等需要"真实可反编译字节码"的端点。
  bempdiff/fixtures/big200_v1.jar / v2.jar   (~200MB, ZIP_STORED)
  bempdiff/fixtures/big500_v1.jar / v2.jar   (~500MB, ZIP_STORED)
      - 用真实 .class 复制到唯一路径，v2 对 ~2% 条目换用不同真实类字节（MODIFIED），
        其余 UNCHANGED。用途：解析 + 差异树 SLA（≤200MB <30s）与峰值压力。

真实字节码来源：bempdiff/lib_v1.jar（345 个真实编译类）。
"""
import os, random, zipfile, sys

ROOT = "bempdiff/fixtures"
SRC = "bempdiff/lib_v1.jar"
os.makedirs(ROOT, exist_ok=True)
random.seed(42)

# ---- 载入真实 .class 字节池 ----
classes = []  # (orig_name, bytes)
with zipfile.ZipFile(SRC) as z:
    for n in z.namelist():
        if n.endswith(".class"):
            classes.append((n, z.read(n)))
print("真实类字节池规模:", len(classes), file=sys.stderr)


def write_pairs(v1path, v2path, gen1, gen2):
    with zipfile.ZipFile(v1path, "w", zipfile.ZIP_STORED) as z1, \
         zipfile.ZipFile(v2path, "w", zipfile.ZIP_STORED) as z2:
        for name, b in gen1:
            z1.writestr(name, b)
        for name, b in gen2:
            z2.writestr(name, b)


def build_small():
    N = 24
    sel = random.sample(classes, N)
    base = "com/bempdiff/gen/"
    paths = [base + "C%02d.class" % i for i in range(N)]
    mod_idx = set(random.sample(range(N), 4))
    del_idx = set(random.sample(range(N), 2))
    added_paths = [base + "ExtraA.class", base + "ExtraB.class"]
    other = {i: classes[(i + 50) % len(classes)][1] for i in mod_idx}

    gen1 = [(paths[i], sel[i][1]) for i in range(N)]
    gen2 = []
    for i in range(N):
        if i in del_idx:
            continue  # DELETED：仅 v1 有
        if i in mod_idx:
            gen2.append((paths[i], other[i]))      # MODIFIED
        else:
            gen2.append((paths[i], sel[i][1]))     # UNCHANGED
    for p in added_paths:
        gen2.append((p, random.choice(classes)[1]))  # ADDED
    write_pairs(ROOT + "/small_real_v1.jar", ROOT + "/small_real_v2.jar", gen1, gen2)
    print("small: v1=%d v2=%d (MOD=%d ADD=%d DEL=%d)" %
          (len(gen1), len(gen2), len(mod_idx), len(added_paths), len(del_idx)), file=sys.stderr)


def build_big(target_mb, mod_frac=0.02):
    target = target_mb * 1024 * 1024
    v1 = ROOT + "/big%d_v1.jar" % target_mb
    v2 = ROOT + "/big%d_v2.jar" % target_mb
    z1 = zipfile.ZipFile(v1, "w", zipfile.ZIP_STORED)
    z2 = zipfile.ZipFile(v2, "w", zipfile.ZIP_STORED)
    total = 0
    i = 0
    cnt = 0
    while total < target:
        src = classes[i % len(classes)]
        base_name = src[0].split("/")[-1]
        name = "com/bempdiff/gen/G%06d/%s" % (i, base_name)
        b = src[1]
        z1.writestr(name, b)
        if random.random() < mod_frac:
            z2.writestr(name, classes[(i + 50) % len(classes)][1])  # MODIFIED
        else:
            z2.writestr(name, b)
        total += len(b)
        i += 1
        cnt += 1
        if cnt % 5000 == 0:
            print("  ...%d entries, %.1f MB" % (cnt, total / 1024 / 1024), file=sys.stderr)
    z1.close()
    z2.close()
    print("big%d: entries=%d file1=%.1fMB file2=%.1fMB" %
          (target_mb, cnt, os.path.getsize(v1) / 1024 / 1024, os.path.getsize(v2) / 1024 / 1024),
          file=sys.stderr)


if __name__ == "__main__":
    build_small()
    build_big(200)
    build_big(500)
    print("ALL FIXTURES DONE", file=sys.stderr)
