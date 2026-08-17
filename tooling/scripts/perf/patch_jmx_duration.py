#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""定点把指定 decompile JMX 的 ThreadGroup.duration 由 600 改为 180（绕开被锁的 parse 文件）。"""
import os

OUT = "tooling/jmeter"
TARGETS = [
    "decompile_scale_T8", "decompile_scale_T16",
    "decompile_conc_T1", "decompile_conc_T2", "decompile_conc_T4", "decompile_conc_T8",
]
OLD = '<stringProp name="ThreadGroup.duration">600</stringProp>'
NEW = '<stringProp name="ThreadGroup.duration">180</stringProp>'

for name in TARGETS:
    p = os.path.join(OUT, name + ".jmx")
    if not os.path.exists(p):
        print("SKIP (missing):", p)
        continue
    pr = p.replace(".jmx", "_r.jmx")
    with open(p, "r", encoding="utf-8") as f:
        s = f.read()
    if OLD not in s:
        print("NO-OP (already patched?):", p)
        continue
    s2 = s.replace(OLD, NEW)
    with open(pr, "w", encoding="utf-8") as f:
        f.write(s2)
    print("PATCHED ->", pr)
print("DONE")
