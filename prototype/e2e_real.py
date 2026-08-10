#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
端到端验证驱动：用真实 Java 包验证整套设计（对应 WBS 真实 war 验证风险项）
  A) 真实两版本 jar 比对 + 反编译：commons-lang3 3.12.0 -> 3.14.0（真实字节码版本 diff）
  B) 真实 BEMP war 解析（分层结构）：bemp-adapter.war
  C) 真实 BEMP 业务 class 反编译（CFR 处理真实业务字节码）
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import diff_engine as de
import decompile as dc

HERE = os.path.dirname(os.path.abspath(__file__))
CFR = os.path.join(HERE, "cfr.jar")
BEMP = r"D:/code/QJ/BEMP5.0DEV"

print("=" * 64)
print("A) 真实两版本 jar 比对 + 反编译：commons-lang3 3.12.0 -> 3.14.0")
print("=" * 64)
de.run_compare(
    os.path.join(HERE, "lib_v1.jar"), os.path.join(HERE, "lib_v2.jar"),
    out_json=os.path.join(HERE, "real_diff.json"),
    decompile=True, cfr=CFR, expand_all=True, top_k=12,
    report=os.path.join(HERE, "real_diff_report.md"),
)

print("\n" + "=" * 64)
print("B) 真实 BEMP war 解析（分层结构）：bemp-adapter.war")
print("=" * 64)
war = os.path.join(BEMP, "banks/ext-hnnxbank/hnnxbank-adapter-deploy/target/bemp-adapter.war")
info = de.inspect_package(war)
print("包类型:", info["ptype"], "| 版本:", info["version"], "| 总条目:", info["total"])
print("层级分布:", info["layer_counts"])
classes = [k for k in info["entries"] if k.endswith(".class")]
print("抽样 class 条目（前 6）:")
for k in classes[:6]:
    print("   ", k)

print("\n" + "=" * 64)
print("C) 真实 BEMP 业务 class 反编译（CFR 处理真实业务字节码）")
print("=" * 64)
cands = []
for dp, dn, fn in os.walk(BEMP):
    if "/.git/" in dp:
        continue
    for f in fn:
        if f.endswith(".class") and "/com/hundsun/bemp/" in dp.replace("\\", "/") \
           and ("service" in dp or "impl" in dp or "biz" in dp):
            cands.append(os.path.join(dp, f))
cands.sort()
print("候选真实 BEMP 业务 class 数:", len(cands))
chosen = None
for c in cands[:8]:
    r = dc.decompile(c, cfr_jar=CFR)
    if r["ok"] and len(r["source"]) > 80:
        chosen = c
        break
    else:
        print("  (跳过反编译失败/过小) ", os.path.basename(c), r.get("error", "")[:40])
print("选取:", chosen)
if chosen:
    r = dc.decompile(chosen, cfr_jar=CFR)
    out = os.path.join(HERE, "bemp_real_class.java")
    with open(out, "w", encoding="utf-8") as f:
        f.write(r["source"])
    print("反编译引擎:", r["engine"], "| 成功:", r["ok"], "| 源码行数:", r["source"].count("\n") + 1)
    print("已写出:", out)
    print("---- 前 28 行 ----")
    print("\n".join(r["source"].splitlines()[:28]))
print("\n=== 端到端验证完成 ===")
