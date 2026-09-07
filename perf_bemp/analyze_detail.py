"""按阶段+端点交叉分析压测 jsonl"""
import json, glob, collections, os

PERC = [50, 90, 95, 99]

def pct(vals, p):
    if not vals: return None
    idx = max(0, int(round((p/100.0)*len(vals))-1))
    return vals[min(idx, len(vals)-1)]

def analyze(path):
    rows=[]
    for line in open(path, encoding="utf-8"):
        line=line.strip()
        if not line: continue
        try: rows.append(json.loads(line))
        except: pass
    groups=collections.defaultdict(list)
    for r in rows: groups[r.get("label","?")].append(r)
    print(f"\n===== {os.path.basename(path)}  (total={len(rows)}) =====")
    print(f"{'label':<22}{'n':>5}{'err':>4}{'p50':>9}{'p95':>9}{'p99':>9}{'max':>9}")
    for label in sorted(groups):
        rs=groups[label]; els=sorted(x["el"] for x in rs if isinstance(x.get("el"),(int,float)))
        errs=sum(1 for x in rs if not x.get("ok",True))
        if not els: print(f"{label:<22}{len(rs):>5}{errs:>4}  n/a"); continue
        print(f"{label:<22}{len(rs):>5}{errs:>4}"+ "".join(f"{pct(els,p):>9.1f}" if pct(els,p) is not None else f"{'-':>9}" for p in PERC)+f"{els[-1]:>9.1f}")
    slow=sorted([r for r in rows if isinstance(r.get("el"),(int,float)) and r["el"]>3000], key=lambda r:r["el"], reverse=True)[:10]
    if slow:
        print("  -- slow samples (>3000ms) --")
        for r in slow: print(f"    {r.get('label'):<22} el={r.get('el'):.0f}ms code={r.get('code')}")

for ph in ["smoke_api","load20_api","soak_api","smoke_heavy","load5_heavy","soak_heavy"]:
    p=os.path.join("output","results",ph+".jsonl")
    if os.path.exists(p): analyze(p)
