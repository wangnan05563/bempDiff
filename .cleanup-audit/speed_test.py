#!/usr/bin/env python3
import os, ctypes, time, glob
kernel32 = ctypes.windll.kernel32
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cands = []
for dp, dn, fn in os.walk(os.path.join(ROOT, 'prototype')):
    for f in fn:
        if f.endswith('.class'):
            cands.append(os.path.join(dp, f))
    if len(cands) >= 60:
        break
cands = cands[:50]
t0 = time.time()
ok = 0
for p in cands:
    if not os.path.exists(p):
        continue
    r = kernel32.DeleteFileW(p)
    if r:
        ok += 1
dt = time.time() - t0
print('deleted=%d attempted=%d elapsed=%.2fs per_file=%.3fs' % (ok, len(cands), dt, dt/len(cands) if cands else 0))
