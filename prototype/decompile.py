#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
decompile.py — 反编译集成参考实现（对应需求 v1.1 §5.4 / T07 / T08）

设计目标（量产版为 Java 移植参考）：
  - 对 L1 内部业务 class，按需反编译为可读 Java 源码，供双栏比对与 AI 深读。
  - 反编译器后端可插拔：CFR（生产首选）优先；环境无 CFR 时降级为 javap（JDK 自带，输出方法签名级）。
  - 统一接口：decompile(class_path) -> {source, engine, ok, error}
  - 批量反编译带超时保护，避免个别坏 class 卡死整个比对。

生产版（Java/JavaFX）对应关系：
  - CFR 以 `java -jar cfr.jar <class>` 子进程调用（本参考实现同形）。
  - 量产版会把 cfr.jar 打进 exe 资源目录，随包分发，无需联网。
"""
import os
import shutil
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))


def _find_java():
    java = os.environ.get("JAVA_HOME", "")
    if java:
        cand = os.path.join(java, "bin", "java")
        if os.path.exists(cand):
            return cand
    found = shutil.which("java")
    return found


def _find_cfr():
    """优先用随包 cfr.jar；否则尝试常见相对路径。"""
    cands = [
        os.path.join(HERE, "cfr.jar"),
        os.path.join(HERE, "lib", "cfr.jar"),
    ]
    for c in cands:
        if os.path.exists(c) and os.path.getsize(c) > 0:
            return c
    return None


def _decode(bs):
    """CFR/javap 在有中文常量的 class 上可能按系统编码（GBK）输出，需容错解码。"""
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return bs.decode(enc)
        except UnicodeDecodeError:
            continue
    return bs.decode("utf-8", errors="replace")


def _cfr_decompile(java_bin, cfr_jar, class_path):
    # CFR 输出到 stdout；sugarenums/hideutf 让反编译结果更接近原始写法
    cmd = [
        java_bin, "-jar", cfr_jar, class_path,
        "--sugarenums", "false",
        "--hideutf", "false",
        "--silent", "true",
    ]
    p = subprocess.run(cmd, capture_output=True, timeout=60)
    out = _decode(p.stdout)
    if p.returncode != 0 or not out.strip():
        # 某些 class（如多版本/jdk 内部类）CFR 可能报错，向上抛给调用方降级
        raise RuntimeError(_decode(p.stderr).strip() or "CFR 返回空")
    return out


def _javap_decompile(java_bin, class_path):
    """降级后端：用 JDK 自带 javap 输出方法签名级信息（非完整源码，但可读、可比对）。"""
    cmd = [java_bin, "-p", "-c", class_path]
    p = subprocess.run(cmd, capture_output=True, timeout=30)
    if p.returncode != 0:
        raise RuntimeError(_decode(p.stderr).strip() or "javap 失败")
    return "// [降级] javap 签名级反编译（环境无 CFR）\n" + _decode(p.stdout)


def decompile(class_path, cfr_jar=None, java_bin=None):
    """反编译单个 .class 文件，返回结构化结果。"""
    java_bin = java_bin or _find_java()
    if not java_bin:
        return {"source": "", "engine": "none", "ok": False, "error": "未找到 java 运行时"}
    if not os.path.exists(class_path):
        return {"source": "", "engine": "none", "ok": False, "error": f"文件不存在: {class_path}"}

    cfr_jar = cfr_jar or _find_cfr()
    if cfr_jar:
        try:
            src = _cfr_decompile(java_bin, cfr_jar, class_path)
            return {"source": src, "engine": "cfr", "ok": True, "error": ""}
        except Exception as e:
            # CFR 失败 → 尝试 javap 降级
            try:
                src = _javap_decompile(java_bin, class_path)
                return {"source": src, "engine": "javap", "ok": True, "error": f"CFR 失败降级: {e}"}
            except Exception as e2:
                return {"source": "", "engine": "cfr", "ok": False, "error": f"{e} | javap: {e2}"}
    # 无 CFR：直接用 javap
    try:
        src = _javap_decompile(java_bin, class_path)
        return {"source": src, "engine": "javap", "ok": True, "error": ""}
    except Exception as e:
        return {"source": "", "engine": "javap", "ok": False, "error": str(e)}


def batch_decompile(class_paths, cfr_jar=None, java_bin=None):
    """批量反编译，逐个保护，返回 {path: result}。"""
    out = {}
    for cp in class_paths:
        out[cp] = decompile(cp, cfr_jar=cfr_jar, java_bin=java_bin)
    return out


if __name__ == "__main__":
    import difflib
    import json

    v1 = os.path.join(HERE, "out", "v1", "com", "internal", "InvoiceService.class")
    v2 = os.path.join(HERE, "out", "v2", "com", "internal", "InvoiceService.class")

    r1 = decompile(v1)
    r2 = decompile(v2)
    print(f"[decompile] v1 engine={r1['engine']} ok={r1['ok']}")
    print(f"[decompile] v2 engine={r2['engine']} ok={r2['ok']}")

    # 源码级 diff（统一格式），供双栏比对 / AI 深读
    a = r1["source"].splitlines()
    b = r2["source"].splitlines()
    diff = list(difflib.unified_diff(a, b, fromfile="v1/InvoiceService", tofile="v2/InvoiceService", lineterm=""))
    diff_text = "\n".join(diff)

    os.makedirs(os.path.join(HERE, "out"), exist_ok=True)
    with open(os.path.join(HERE, "decompiled_src_v1.java"), "w", encoding="utf-8") as f:
        f.write(r1["source"])
    with open(os.path.join(HERE, "decompiled_src_v2.java"), "w", encoding="utf-8") as f:
        f.write(r2["source"])
    with open(os.path.join(HERE, "decompiled_diff.txt"), "w", encoding="utf-8") as f:
        f.write(diff_text)

    print(f"[diff] 行级差异 {len(diff)} 行，已写出 decompiled_diff.txt")
    print("---- preview ----")
    print("\n".join(diff[:40]))
