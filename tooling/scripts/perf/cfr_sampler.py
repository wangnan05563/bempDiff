#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CFR 子进程数采样器：每 10s 记录 java.exe 进程总数（基线 9 = 8 个基础设施 + 1 个 harness，
多出来的即为并发 CFR 子进程），用于量化反编译瓶颈。Ctrl+C 或父进程退出即止。"""
import time, subprocess, sys

out_path = sys.argv[1] if len(sys.argv) > 1 else "logs/cfr_procs.csv"
try:
    with open(out_path, "w", encoding="utf-8") as f:
        while True:
            try:
                r = subprocess.run(['tasklist'], capture_output=True, timeout=10)
                n = r.stdout.decode('utf-8', 'replace').lower().count('java.exe')
            except Exception:
                n = -1
            try:
                f.write('%d,%d\n' % (int(time.time()), n))
                f.flush()
            except Exception:
                pass
            time.sleep(10)
except KeyboardInterrupt:
    pass
