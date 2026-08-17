#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析 JMeter JTL（XML 或 CSV），输出：样本数、墙钟时长、TPS、平均/最小/最大、
p50/p90/p95/p99 响应时间、错误数与错误率，并按 label 拆分。

墙钟时长 = max(ts+elapsed) - min(ts)，对长耗时请求（如大包解析/反编译）比
"起始时间戳差"更准，从而 TPS 反映真实吞吐。

用法：
  python tooling/scripts/perf/analyze_jtl.py tooling/jmeter/results/parse_diff_fast.jtl [more.jtl ...]
"""
import csv, sys, statistics, xml.etree.ElementTree as ET


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0
    k = (len(sorted_vals) - 1) * p
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return sorted_vals[f]
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def analyze(path):
    els = {}
    ok = {}
    err = {}
    ts_min = None
    end_max = 0
    with open(path, "rb") as fh:
        head = fh.read(64)
    if head.lstrip().startswith(b"<?xml") or head.lstrip().startswith(b"<testResults"):
        tree = ET.parse(path)
        for s in tree.iter("httpSample"):
            label = s.get("lb") or "ALL"
            try:
                e = int(s.get("t"))
            except (ValueError, TypeError):
                continue
            try:
                t = int(s.get("ts"))
            except (ValueError, TypeError):
                t = 0
            succ = (s.get("s") or "true").lower() == "true"
            els.setdefault(label, []).append(e)
            ok[label] = ok.get(label, 0) + (1 if succ else 0)
            err[label] = err.get(label, 0) + (0 if succ else 1)
            ts_min = t if ts_min is None else min(ts_min, t)
            end_max = max(end_max, t + e)
    else:
        with open(path, newline="", encoding="utf-8", errors="replace") as f:
            r = csv.DictReader(f)
            for row in r:
                label = row.get("label") or "ALL"
                try:
                    e = int(row["elapsed"])
                except (ValueError, KeyError, TypeError):
                    continue
                try:
                    t = int(row["timeStamp"])
                except (ValueError, KeyError, TypeError):
                    t = 0
                succ = (row.get("success") or "true").lower() == "true"
                els.setdefault(label, []).append(e)
                ok[label] = ok.get(label, 0) + (1 if succ else 0)
                err[label] = err.get(label, 0) + (0 if succ else 1)
                ts_min = t if ts_min is None else min(ts_min, t)
                end_max = max(end_max, t + e)
    dur = (end_max - ts_min) / 1000.0 if ts_min is not None else 0
    return els, ok, err, dur


def report(path):
    els, ok, err, dur = analyze(path)
    print("=" * 78)
    print("FILE :", path)
    print("DURATION(s): %.1f" % dur)
    for label in sorted(els.keys()):
        v = sorted(els[label])
        n = len(v)
        tps = n / dur if dur > 0 else 0
        e = err.get(label, 0)
        er = e / n * 100 if n else 0
        print("-" * 78)
        print("LABEL: %s  (n=%d)" % (label, n))
        print("  TPS=%.2f  avg=%.1fms  min=%dms  max=%dms" % (tps, statistics.mean(v), v[0], v[-1]))
        print("  p50=%.1f  p90=%.1f  p95=%.1f  p99=%.1f ms" %
              (pct(v, .5), pct(v, .9), pct(v, .95), pct(v, .99)))
        print("  errors=%d  error_rate=%.2f%%" % (e, er))
    tot_n = sum(len(v) for v in els.values())
    tot_err = sum(err.values())
    print("-" * 78)
    print("TOTAL n=%d  TPS=%.2f  error_rate=%.2f%%" %
          (tot_n, tot_n / dur if dur else 0, tot_err / tot_n * 100 if tot_n else 0))
    print("=" * 78)


if __name__ == "__main__":
    for p in sys.argv[1:]:
        report(p)
