#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
T01667: Windows arm64 安装包架构硬校验（NSIS 两层压缩解包 + PE machine 读取）

用法:
  python verify_arm64_pe.py <setup.exe> [--workdir <临时目录>]

要点（沿用 T01622 x64 定型手法）:
  1. NSIS 安装包是两层 7z：外层 7z 段含 `3.7z`，内层才是真实 payload
     - `7z l -t# <setup.exe>`  列出外层段
     - `7z e -t# <setup.exe> 3.7z` 抽出内层包
     - `7z e <内层包> <目标文件>` 抽取具体文件
  2. setup.exe 自身 PE machine 恒为 0x014c（NSIS x86 引导桩），**不能**作为判据
  3. 判据文件：BempDiff.exe / jre/bin/java.exe / jre/bin/javaw.exe
     - x64    -> 0x8664
     - arm64  -> 0xaa64
"""
import os
import re
import struct
import subprocess
import sys
import tempfile

SEVEN_ZIP = r"C:\Program Files\7-Zip\7z.exe"
EXPECT = {"x64": 0x8664, "arm64": 0xAA64, "x86": 0x014C}

TARGETS = [
    "BempDiff.exe",
    "jre/bin/java.exe",
    "jre/bin/javaw.exe",
]


def seven_zip(args, cwd):
    cmd = [SEVEN_ZIP] + args
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def pe_machine(path):
    with open(path, "rb") as f:
        if f.read(2) != b"MZ":
            return None, "not MZ"
        f.seek(0x3C)
        (e_lfanew,) = struct.unpack("<I", f.read(4))
        f.seek(e_lfanew)
        sig = f.read(4)
        if sig != b"PE\0\0":
            return None, "no PE signature at 0x%x" % e_lfanew
        (machine,) = struct.unpack("<H", f.read(2))
        return machine, "ok"


def main():
    if len(sys.argv) < 2:
        print("usage: verify_arm64_pe.py <setup.exe> [--expect arm64|x64] [--workdir dir]")
        return 2
    setup = os.path.abspath(sys.argv[1])
    expect_name = "arm64"
    workdir = None
    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--expect":
            expect_name = args[i + 1]
            i += 2
        elif args[i] == "--workdir":
            workdir = args[i + 1]
            i += 2
        else:
            i += 1
    expect = EXPECT[expect_name]

    if not os.path.isfile(setup):
        print("FAIL: setup not found:", setup)
        return 1
    if not os.path.isfile(SEVEN_ZIP):
        print("FAIL: 7z not found:", SEVEN_ZIP)
        return 1

    tmp = workdir or tempfile.mkdtemp(prefix="pechk_")
    os.makedirs(tmp, exist_ok=True)
    print("setup :", setup)
    print("workdir:", tmp)

    # 层 1: 外层 7z 段
    rc, out = seven_zip(["l", "-t#", setup], tmp)
    if rc != 0:
        print("FAIL: outer list rc=%d\n%s" % (rc, out[-1500:]))
        return 1
    m = re.search(r"^\s*\d+\s+(\S+\.7z)\s", out, re.M)
    # NSIS 外层段名通常为 "3.7z"，兜底取列出的第一个 .7z
    if not m:
        m = re.search(r"(\d+\.7z)", out)
    if not m:
        print("FAIL: outer 7z segment not found\n" + out[-1500:])
        return 1
    inner = m.group(1)
    print("outer segment:", inner)

    rc, out = seven_zip(["e", "-t#", setup, inner, "-o" + tmp, "-y"], tmp)
    if rc != 0:
        print("FAIL: extract inner rc=%d\n%s" % (rc, out[-1500:]))
        return 1
    inner_path = os.path.join(tmp, inner)
    # 有时期望名与实际落盘名不同（如 3.7z -> 3.7z）
    if not os.path.isfile(inner_path):
        cands = [f for f in os.listdir(tmp) if f.endswith(".7z")]
        if not cands:
            print("FAIL: inner 7z not on disk; dir=%s" % os.listdir(tmp))
            return 1
        inner_path = os.path.join(tmp, cands[0])
    print("inner payload:", inner_path, os.path.getsize(inner_path), "bytes")

    ok = True
    for t in TARGETS:
        outd = os.path.join(tmp, "out_" + t.replace("/", "_").replace("\\", "_"))
        os.makedirs(outd, exist_ok=True)
        rc, out = seven_zip(["e", inner_path, t, "-o" + outd, "-y", "-r"], tmp)
        found = None
        for root, _dirs, files in os.walk(outd):
            for fn in files:
                if fn.lower() == os.path.basename(t).lower():
                    found = os.path.join(root, fn)
        if rc != 0 or not found:
            print("  %-22s MISSING (rc=%d)" % (t, rc))
            ok = False
            continue
        machine, why = pe_machine(found)
        if machine is None:
            print("  %-22s UNREADABLE: %s" % (t, why))
            ok = False
            continue
        good = (machine == expect)
        ok = ok and good
        print("  %-22s machine=0x%04x  expect=0x%04x  %s"
              % (t, machine, expect, "PASS" if good else "FAIL"))

    print("\nRESULT:", "PASS" if ok else "FAIL", "(expect %s)" % expect_name)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
