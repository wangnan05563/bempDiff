"""Lightweight CPD (copy-paste detector) for Java sources.

Mimics SonarQube's default duplication rule: report blocks of >= 10
consecutive duplicated lines (token-normalized) across files.
Output: merged duplicate block pairs sorted by size, so fixes can be
prioritized by line count against the 3% duplication gate.
"""
import hashlib
import os
import re
import sys
from collections import defaultdict

SRC = os.path.join(os.path.dirname(__file__), "..", "..", "..", "bempdiff", "java_core", "src")
WINDOW = 10
MIN_BLOCK = 10  # SonarQube default: blocks of >=10 duplicated token lines

TOKEN_RE = re.compile(r"[A-Za-z0-9_$]+|\S")


def normalize_line(line):
    toks = TOKEN_RE.findall(line)
    # ignore pure punctuation-only lines and comments
    if not toks:
        return None
    if len(toks) == 1 and not re.match(r"[A-Za-z0-9_$]", toks[0]):
        return None
    return " ".join(toks)


def collect():
    entries = []  # (file, start_idx, [normalized lines])
    for root, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(root, f)
            with open(path, encoding="utf-8", errors="replace") as fh:
                lines = fh.read().splitlines()
            norm = []
            for i, ln in enumerate(lines):
                n = normalize_line(ln)
                norm.append((i + 1, n))
            entries.append((path, norm))
    return entries


def main():
    entries = collect()
    index = defaultdict(list)  # hash -> list of (file, line_no)
    for path, norm in entries:
        valid = [(ln, n) for ln, n in norm if n is not None]
        for s in range(len(valid) - WINDOW + 1):
            chunk = "\n".join(n for _, n in valid[s:s + WINDOW])
            h = hashlib.md5(chunk.encode()).hexdigest()
            index[h].append((path, valid[s][0]))

    # candidate blocks: hash seen in >=2 distinct locations
    blocks = []
    for h, locs in index.items():
        if len(locs) < 2:
            continue
        files = {p for p, _ in locs}
        if len(files) < 2 and len(locs) < 2:
            continue
        blocks.append((h, locs))

    # merge adjacent windows per (hash-group, file) into intervals
    def intervals_for(locs):
        by_file = defaultdict(list)
        for p, ln in locs:
            by_file[p].append(ln)
        out = {}
        for p, lns in by_file.items():
            lns.sort()
            start = prev = lns[0]
            ivs = []
            for ln in lns[1:]:
                if ln == prev + 1:
                    prev = ln
                else:
                    ivs.append((start, prev + WINDOW - 1))
                    start = prev = ln
            ivs.append((start, prev + WINDOW - 1))
            out[p] = ivs
        return out

    merged = {}
    for h, locs in blocks:
        merged[h] = intervals_for(locs)

    # build report: for each hash with >=2 files or intra-file far apart
    reported = []
    seen_pairs = set()
    for h, per_file in merged.items():
        items = sorted(per_file.items())
        for i in range(len(items)):
            for j in range(i + 1, len(items)):
                f1, iv1 = items[i]
                f2, iv2 = items[j]
                if f1 == f2:
                    continue
                for a in iv1:
                    for b in iv2:
                        size = min(a[1] - a[0], b[1] - b[0]) + 1
                        if size < MIN_BLOCK:
                            continue
                        key = tuple(sorted([(f1, a), (f2, b)]))
                        if key in seen_pairs:
                            continue
                        seen_pairs.add(key)
                        reported.append((size, f1, a, f2, b))

    # intra-file duplicates (same file, disjoint intervals)
    for h, per_file in merged.items():
        for f, ivs in per_file.items():
            for i in range(len(ivs)):
                for j in range(i + 1, len(ivs)):
                    a, b = ivs[i], ivs[j]
                    size = min(a[1] - a[0], b[1] - b[0]) + 1
                    if size < MIN_BLOCK and not (b[0] > a[1]):
                        continue
                    if size < MIN_BLOCK:
                        continue
                    reported.append((size, f, a, f, b))

    reported.sort(reverse=True)
    total = sum(size for size, *_ in reported)
    for size, f1, a, f2, b in reported[:40]:
        r1 = os.path.relpath(f1, SRC)
        r2 = os.path.relpath(f2, SRC)
        print(f"{size:4d} lines  {r1}:{a[0]}-{a[1]}  <->  {r2}:{b[0]}-{b[1]}")
    print(f"\ntotal duplicated block lines (>= {MIN_BLOCK}): {total}")


if __name__ == "__main__":
    sys.exit(main())
