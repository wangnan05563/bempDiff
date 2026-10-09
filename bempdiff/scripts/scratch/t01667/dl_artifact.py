#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T01667: 并行分块下载 GitHub Actions artifact（Range 并发 + 断点续传）"""
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

TOKEN = os.environ.get("GH_TOKEN", "")
ARTIFACT_URL = (
    "https://api.github.com/repos/wangnan05563/bempDiff/"
    "actions/artifacts/11568600353/zip"
)
OUT = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else "arm64-artifact.zip"
CHUNK = 8 * 1024 * 1024  # 8MB
WORKERS = 12


def get_final_url():
    req = urllib.request.Request(ARTIFACT_URL, method="GET")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *a, **k):
            return None

    op = urllib.request.build_opener(NoRedirect)
    try:
        op.open(req, timeout=60)
        raise RuntimeError("expected 302")
    except urllib.error.HTTPError as e:
        if e.code in (301, 302, 303, 307):
            return e.headers["Location"]
        raise


def fetch_chunk(url, idx, start, end):
    part = "%s.part%04d" % (OUT, idx)
    have = os.path.getsize(part) if os.path.exists(part) else 0
    want = end - start + 1
    if have == want:
        print("chunk %02d done (%d B)" % (idx, want), flush=True)
        return
    for attempt in range(8):
        try:
            req = urllib.request.Request(url)
            req.add_header("Range", "bytes=%d-%d" % (start + have, end))
            with urllib.request.urlopen(req, timeout=180) as r, \
                    open(part, "ab" if have else "wb") as f:
                while True:
                    buf = r.read(256 * 1024)
                    if not buf:
                        break
                    f.write(buf)
            have = os.path.getsize(part)
            if have == want:
                print("chunk %02d done (%d B)" % (idx, want), flush=True)
                return
            print("chunk %02d short: %d/%d, retry" % (idx, have, want), flush=True)
        except Exception as ex:
            print("chunk %02d attempt %d err: %s" % (idx, attempt, ex), flush=True)
            time.sleep(2 + attempt * 3)
            have = os.path.getsize(part) if os.path.exists(part) else 0
    raise RuntimeError("chunk %02d failed permanently" % idx)


def main():
    url = get_final_url()
    print("final host:", url.split("/")[2], flush=True)
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req, timeout=60) as r:
        total = int(r.headers["Content-Length"])
        print("total:", total, flush=True)
    n = (total + CHUNK - 1) // CHUNK
    t0 = time.time()
    with ThreadPoolExecutor(WORKERS) as ex:
        futs = []
        for i in range(n):
            s = i * CHUNK
            e = min(total, s + CHUNK) - 1
            futs.append(ex.submit(fetch_chunk, url, i, s, e))
        for f in futs:
            f.result()
    with open(OUT, "wb") as out:
        for i in range(n):
            with open("%s.part%04d" % (OUT, i), "rb") as f:
                while True:
                    buf = f.read(1024 * 1024)
                    if not buf:
                        break
                    out.write(buf)
    size = os.path.getsize(OUT)
    print("assembled:", OUT, size, "bytes, elapsed %.0fs" % (time.time() - t0), flush=True)
    if size != total:
        raise SystemExit("SIZE MISMATCH")
    for i in range(n):
        os.remove("%s.part%04d" % (OUT, i))
    print("OK", flush=True)


if __name__ == "__main__":
    main()
