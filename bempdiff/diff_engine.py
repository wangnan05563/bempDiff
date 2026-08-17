#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
WAR/JAR 差异比对引擎 —— 参考原型（纯 Python，仅标准库）
========================================================
对应需求文档 v1.1：
  - §5.2.1 差异粒度分层策略 (L0 包级 / L1 内部业务码 / L2 第三方依赖)
  - §5.2 按包类型的路径映射 (war / 普通 jar / Spring Boot fat jar)
  - FR3.1 全量差异计算 (ADDED/DELETED/MODIFIED/UNCHANGED)
  - FR4.7 删除类 / FR4.8 非文本边界（分类标记，供上层决策）
  - FR1.7 版本自动提取 (MANIFEST Implementation-Version)
  - T07/T08 反编译集成：--decompile 时对 L1 修改类做双栏源码 diff（调用 decompile.py / CFR）

本原型是「设计验证 + Java 移植参考」。量产版由 JavaFX 桌面程序承载，
反编译(CFR)在 L1 命中 class 时调用。

用法：
  python diff_engine.py make-fixture
  python diff_engine.py compare A.war B.war [--json out.json]
  # 真实双版本 jar 比对 + 反编译（expand-all 把自身 class 全展开为 L1）：
  python diff_engine.py compare lib_v1.jar lib_v2.jar --expand-all --decompile --report real_diff_report.md
"""
import io
import os
import sys
import json
import zipfile
import hashlib
import argparse
import tempfile
import difflib

# 可配置的内部包前缀（需求 FR9.5 / §5.2.1）。命中的 lib jar 会被展开到 L1。
INTERNAL_PREFIXES = ("com/", "cn/")

STATUS_ADDED = "ADDED"
STATUS_DELETED = "DELETED"
STATUS_MODIFIED = "MODIFIED"
STATUS_UNCHANGED = "UNCHANGED"


# ------------------------- 基础工具 -------------------------
def sha256_of(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def classify(name: str) -> str:
    """FR2.3 文件分类：class / jar / config / static / other"""
    if name.endswith(".class"):
        return "class"
    if name.endswith(".jar"):
        return "jar"
    if name.endswith((".xml", ".properties", ".yml", ".yaml", ".json", ".conf", ".cfg")):
        return "config"
    if name.endswith((".jsp", ".html", ".js", ".css", ".png", ".jpg", ".gif", ".svg",
                       ".woff", ".woff2", ".ttf", ".eot")):
        return "static"
    return "other"


# ------------------------- 包类型识别与路径映射 -------------------------
def detect_package_type(zf: zipfile.ZipFile) -> str:
    """FR1.3 / FR2.7：依据内部布局判断 war / fat_jar / jar"""
    names = zf.namelist()
    joined = "\n".join(names)
    if any(n.startswith("BOOT-INF/classes/") for n in names) or "BOOT-INF/lib/" in joined:
        return "fat_jar"
    if any(n.startswith("WEB-INF/classes/") for n in names) or "WEB-INF/lib/" in joined:
        return "war"
    return "jar"


def path_mapping(ptype: str):
    """返回 (classes_prefix, lib_prefix)"""
    if ptype == "war":
        return ("WEB-INF/classes/", "WEB-INF/lib/")
    if ptype == "fat_jar":
        return ("BOOT-INF/classes/", "BOOT-INF/lib/")
    return ("", "")  # 普通 jar：class 在根


def extract_version(zf: zipfile.ZipFile):
    """FR1.7 版本自动提取：MANIFEST Implementation-Version / pom.properties"""
    try:
        mf = zf.read("META-INF/MANIFEST.MF").decode("utf-8", "ignore")
        for line in mf.splitlines():
            if line.startswith("Implementation-Version:"):
                return line.split(":", 1)[1].strip()
    except KeyError:
        pass
    for n in zf.namelist():
        if n.startswith("META-INF/maven/") and n.endswith("pom.properties"):
            try:
                props = zf.read(n).decode("utf-8", "ignore")
                for line in props.splitlines():
                    if line.startswith("version="):
                        return line.split("=", 1)[1].strip()
            except Exception:
                pass
    return None


# ------------------------- 分层逻辑核心 -------------------------
def build_logical_entries(zf, internal_prefixes=INTERNAL_PREFIXES, expand_all=False):
    """
    将压缩包解析为「逻辑文件集合」(key -> meta)，meta 含 src=(outer,inner) 供反编译回填。
    关键：用命名空间避免同名类冲突（§5.2.1）。
      - L0 包级：顶层文件、META-INF、WEB-INF/classes 之外的资源
      - L1 内部业务码：WEB-INF/classes 下的文件 + 命中的内部 lib jar 展开后的 class
      - L2 第三方依赖：未命中的 lib jar 仅保留 jar 级条目（不展开）
    普通 jar（无 WEB-INF）：--expand-all 时所有 class 视为 L1（同构件两版比对场景）。
    """
    ptype = detect_package_type(zf)
    classes_prefix, lib_prefix = path_mapping(ptype)
    entries = {}

    def add(key, layer, classification, data, src):
        entries[key] = {
            "key": key, "layer": layer, "type": classification,
            "size": len(data), "sha256": sha256_of(data), "src": src,
        }

    lib_jars = {}
    for info in zf.infolist():
        if info.is_dir():
            continue
        n = info.filename
        if lib_prefix and n.startswith(lib_prefix) and n.endswith(".jar"):
            lib_jars[n] = zf.read(n)

    for jar_path, jar_data in lib_jars.items():
        try:
            jz = zipfile.ZipFile(io.BytesIO(jar_data))
        except Exception:
            add(jar_path, "L2", "jar", jar_data, (jar_path, None))
            continue
        inner = jz.namelist()
        is_internal = any(e.endswith(".class") and e.startswith(internal_prefixes) for e in inner)
        if is_internal:
            for e in inner:
                if e.endswith("/"):
                    continue
                d = jz.read(e)
                ns = f"{jar_path}/{e}"
                add(ns, "L1", classify(e), d, (jar_path, e))
        else:
            add(jar_path, "L2", "jar", jar_data, (jar_path, None))

    for info in zf.infolist():
        if info.is_dir():
            continue
        n = info.filename
        if lib_prefix and n.startswith(lib_prefix) and n.endswith(".jar"):
            continue
        data = zf.read(n)
        if ptype == "jar" and n.endswith(".class"):
            layer = "L1" if (expand_all or n.startswith(internal_prefixes)) else "L0"
            add(n, layer, "class", data, (n, None))
        elif classes_prefix and n.startswith(classes_prefix):
            add(n, "L1", classify(n), data, (n, None))
        else:
            add(n, "L0", classify(n), data, (n, None))

    return entries


def _extract_bytes(zf, src):
    """src=(outer_zip_entry, inner_entry_or_None) -> 该 class 的字节"""
    outer, inner = src
    data = zf.read(outer)
    if inner is None:
        return data
    jz = zipfile.ZipFile(io.BytesIO(data))
    return jz.read(inner)


# ------------------------- 单包检视（端到端验证用） -------------------------
def inspect_package(path):
    with zipfile.ZipFile(path) as z:
        ptype = detect_package_type(z)
        entries = build_logical_entries(z)
        v = extract_version(z)
    layer_counts = {}
    for e in entries.values():
        layer_counts[e["layer"]] = layer_counts.get(e["layer"], 0) + 1
    return {"path": path, "ptype": ptype, "version": v,
            "total": len(entries), "layer_counts": layer_counts,
            "entries": entries}


# ------------------------- 差异计算 -------------------------
def compute_diff(old, new):
    keys = set(old) | set(new)
    result = {STATUS_ADDED: [], STATUS_DELETED: [], STATUS_MODIFIED: [], STATUS_UNCHANGED: []}
    for k in sorted(keys):
        o, n = old.get(k), new.get(k)
        if o and not n:
            result[STATUS_DELETED].append(k)
        elif n and not o:
            result[STATUS_ADDED].append(k)
        elif o["sha256"] != n["sha256"]:
            result[STATUS_MODIFIED].append(k)
        else:
            result[STATUS_UNCHANGED].append(k)
    return result


def compute_stats(diff, old, new):
    stats = {k: len(v) for k, v in diff.items()}
    internal_jar_changed = sum(
        1 for k in diff[STATUS_MODIFIED] + diff[STATUS_ADDED] + diff[STATUS_DELETED]
        if k.endswith(".jar") and ("/lib/" in k)
    )
    stats["internal_or_biz_changed"] = sum(
        1 for st in (STATUS_MODIFIED, STATUS_ADDED, STATUS_DELETED)
        for k in diff[st] if not k.endswith(".jar")
    )
    stats["jar_level_changed"] = internal_jar_changed
    return stats


# ------------------------- 展示 -------------------------
STATUS_MARK = {STATUS_ADDED: "[新增 +]", STATUS_DELETED: "[删除 -]",
               STATUS_MODIFIED: "[修改 ~]", STATUS_UNCHANGED: "[未变 =]"}
LAYER_MARK = {"L0": "包级", "L1": "业务", "L2": "三方"}


def print_tree(diff, old_entries=None, new_entries=None):
    """差异文件树。若传入 entries，则从条目取真实 layer（避免普通 jar 比对时
    路径不含 WEB-INF 被误判为 L0 的 bug）；否则回退到路径启发式推导。"""
    print("\n===== 差异文件树（按状态/层级）=====")
    for st in (STATUS_MODIFIED, STATUS_ADDED, STATUS_DELETED, STATUS_UNCHANGED):
        for k in diff[st]:
            # 优先用真实 layer：删除类只在老包、新增类只在新包
            lay = None
            if old_entries and k in old_entries:
                lay = old_entries[k]["layer"]
            elif new_entries and k in new_entries:
                lay = new_entries[k]["layer"]
            if lay is None:
                # 回退：路径启发式
                if "/lib/" in k and k.endswith(".jar"):
                    lay = "L2"
                elif "/classes/" in k or k.endswith(".class"):
                    lay = "L1"
                else:
                    lay = "L0"
            indent = "  " * k.count("/")
            print(f"{indent}{STATUS_MARK[st]} [{LAYER_MARK.get(lay, lay)}] {k}")


# ------------------------- 反编译 + 源码级 diff（T07/T08） -------------------------
def _decompile_modified(zo, zn, diff, old_entries, new_entries, cfr, top_k):
    """对 L1 修改类做双栏源码 diff，返回 {key: {old,new,diff,engine}}（上限 top_k）。"""
    import decompile as dec
    out = {}
    cands = []
    for k in diff[STATUS_MODIFIED]:
        e = old_entries.get(k)
        if e and e["type"] == "class" and e["layer"] == "L1":
            cands.append((k, e["src"], new_entries[k]["src"]))
    skipped = max(0, len(cands) - top_k)
    cands = cands[:top_k]
    tmp = tempfile.mkdtemp(prefix="decomp_")
    for k, so, sn in cands:
        try:
            b1 = _extract_bytes(zo, so)
            b2 = _extract_bytes(zn, sn)
            p1 = os.path.join(tmp, "o.class"); p2 = os.path.join(tmp, "n.class")
            with open(p1, "wb") as f: f.write(b1)
            with open(p2, "wb") as f: f.write(b2)
            r1 = dec.decompile(p1, cfr_jar=cfr)
            r2 = dec.decompile(p2, cfr_jar=cfr)
            if r1["ok"] and r2["ok"]:
                d = "\n".join(difflib.unified_diff(
                    r1["source"].splitlines(), r2["source"].splitlines(),
                    fromfile="old/" + k, tofile="new/" + k, lineterm=""))
                out[k] = {"old": r1["source"], "new": r2["source"],
                          "diff": d, "engine": r1["engine"]}
        except Exception as ex:
            out[k] = {"error": str(ex)}
    return out, skipped


def _strip_src(entries):
    return {k: {kk: vv for kk, vv in v.items() if kk != "src"} for k, v in entries.items()}


# ------------------------- Markdown 报告（FR7） -------------------------
def write_markdown_report(old_path, new_path, v_old, v_new, stats, diff, decompiled, skipped, path):
    L = []
    L.append(f"# 差异分析报告（真实包比对）\n")
    L.append(f"- 老包：`{old_path}`（版本 {v_old}）")
    L.append(f"- 新包：`{new_path}`（版本 {v_new}）\n")
    L.append("## 一、差异统计")
    L.append(f"- 新增 **{stats[STATUS_ADDED]}** · 删除 **{stats[STATUS_DELETED]}** · "
             f"修改 **{stats[STATUS_MODIFIED]}** · 未变 {stats[STATUS_UNCHANGED]}")
    L.append(f"- 业务/类级变更(非jar)：{stats['internal_or_biz_changed']} · jar 级变更：{stats['jar_level_changed']}\n")
    L.append("## 二、差异文件树")
    for st in (STATUS_MODIFIED, STATUS_ADDED, STATUS_DELETED):
        for k in diff[st]:
            L.append(f"- `{STATUS_MARK[st]}` {k}")
    L.append("\n## 三、反编译源码级差异（Top-K 修改类）")
    L.append(f"> 共 {len(decompiled)} 个修改类已反编译；另 {skipped} 个修改类未展开（受 Top-K 限制）。\n")
    for k, v in decompiled.items():
        L.append(f"### {k}")
        if "error" in v:
            L.append(f"- 反编译失败：{v['error']}")
        else:
            L.append(f"- 反编译引擎：{v['engine']}")
            L.append("```diff")
            L.append(v["diff"])
            L.append("```\n")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    return "\n".join(L)


# ------------------------- 主比对流程 -------------------------
def run_compare(old_path, new_path, out_json=None, decompile=False, cfr=None,
                expand_all=False, top_k=12, report=None):
    if not (os.path.exists(old_path) and os.path.exists(new_path)):
        print(f"ERROR: 文件不存在 -> {old_path} / {new_path}", file=sys.stderr)
        sys.exit(2)
    with zipfile.ZipFile(old_path) as zo, zipfile.ZipFile(new_path) as zn:
        old_entries = build_logical_entries(zo, expand_all=expand_all)
        new_entries = build_logical_entries(zn, expand_all=expand_all)
        diff = compute_diff(old_entries, new_entries)
        stats = compute_stats(diff, old_entries, new_entries)
        v_old, v_new = extract_version(zo), extract_version(zn)
        decompiled, skipped = ({}, 0)
        if decompile:
            decompiled, skipped = _decompile_modified(
                zo, zn, diff, old_entries, new_entries, cfr, top_k)

    print(f"老包: {old_path}  版本={v_old}")
    print(f"新包: {new_path}  版本={v_new}")
    print(f"\n===== 差异统计 =====")
    print(f"新增={stats[STATUS_ADDED]} 删除={stats[STATUS_DELETED]} "
          f"修改={stats[STATUS_MODIFIED]} 未变={stats[STATUS_UNCHANGED]}")
    print(f"业务/类级变更(非jar)={stats['internal_or_biz_changed']}  jar级变更={stats['jar_level_changed']}")
    if decompile:
        print(f"反编译修改类: {len(decompiled)} 个（跳过 {skipped} 个，Top-K={top_k}）")
    print_tree(diff, old_entries, new_entries)

    if out_json:
        payload = {"old": old_path, "new": new_path, "old_version": v_old,
                   "new_version": v_new, "stats": stats, "diff": diff,
                   "decompiled": {k: {kk: vv for kk, vv in v.items() if kk != "diff"}
                                  for k, v in decompiled.items()}}
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"\nJSON 已写出: {out_json}")
    if report:
        md = write_markdown_report(old_path, new_path, v_old, v_new, stats,
                                   diff, decompiled, skipped, report)
        print(f"Markdown 报告已写出: {report}")
    return diff, stats, decompiled


# ------------------------- 夹具生成（用于自测） -------------------------
def _write_war(path, files):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in files.items():
            z.writestr(name, data)


def make_fixture():
    base = os.path.dirname(os.path.abspath(__file__))
    v1, v2 = {}, {}
    v1["META-INF/MANIFEST.MF"] = b"Implementation-Version: 5.0.1\n"
    v2["META-INF/MANIFEST.MF"] = b"Implementation-Version: 5.0.2\n"
    v1["WEB-INF/classes/com/internal/A.class"] = b"A body v1"
    v2["WEB-INF/classes/com/internal/A.class"] = b"A body v2 changed"
    v1["WEB-INF/classes/com/internal/Del.class"] = b"to be deleted"
    v2["WEB-INF/classes/com/internal/Add.class"] = b"newly added"
    v1["WEB-INF/classes/com/internal/Cfg.properties"] = b"k=v1"
    v2["WEB-INF/classes/com/internal/Cfg.properties"] = b"k=v2"
    inner_v1 = {"com/internal/B.class": b"B body v1", "com/internal/Util.class": b"util v1"}
    inner_v2 = {"com/internal/B.class": b"B body v2 changed", "com/internal/Util.class": b"util v1"}
    buf1, buf2 = io.BytesIO(), io.BytesIO()
    with zipfile.ZipFile(buf1, "w") as z:
        for n, d in inner_v1.items(): z.writestr(n, d)
    with zipfile.ZipFile(buf2, "w") as z:
        for n, d in inner_v2.items(): z.writestr(n, d)
    v1["WEB-INF/lib/internal-core.jar"] = buf1.getvalue()
    v2["WEB-INF/lib/internal-core.jar"] = buf2.getvalue()
    tp1, tp2 = io.BytesIO(), io.BytesIO()
    with zipfile.ZipFile(tp1, "w") as z: z.writestr("org/third/C.class", b"C v1")
    with zipfile.ZipFile(tp2, "w") as z:
        z.writestr("org/third/C.class", b"C v1"); z.writestr("org/third/Extra.class", b"extra")
    v1["WEB-INF/lib/third-party.jar"] = tp1.getvalue()
    v2["WEB-INF/lib/third-party.jar"] = tp2.getvalue()
    p1 = os.path.join(base, "sample_v1.war"); p2 = os.path.join(base, "sample_v2.war")
    _write_war(p1, v1); _write_war(p2, v2)
    print(f"夹具已生成:\n  {p1}\n  {p2}")


# ------------------------- CLI -------------------------
def main():
    ap = argparse.ArgumentParser(description="WAR/JAR 差异比对引擎参考原型")
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("make-fixture")
    cmp_p = sub.add_parser("compare", help="比对两个包")
    cmp_p.add_argument("old")
    cmp_p.add_argument("new")
    cmp_p.add_argument("--json", help="输出 JSON 路径")
    cmp_p.add_argument("--decompile", action="store_true", help="对 L1 修改类反编译并源码 diff")
    cmp_p.add_argument("--cfr", help="cfr.jar 路径")
    cmp_p.add_argument("--expand-all", action="store_true", help="普通 jar：把所有 class 当 L1 展开")
    cmp_p.add_argument("--top-k", type=int, default=12, help="反编译修改类上限")
    cmp_p.add_argument("--report", help="输出 Markdown 报告路径")
    args = ap.parse_args()
    if args.cmd == "make-fixture":
        make_fixture()
    elif args.cmd == "compare":
        run_compare(args.old, args.new, out_json=args.json, decompile=args.decompile,
                    cfr=args.cfr, expand_all=args.expand_all, top_k=args.top_k,
                    report=args.report)
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
