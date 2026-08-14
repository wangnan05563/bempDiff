#!/usr/bin/env python3
# Execute deletion via ctypes (bypasses Python os-layer safe-delete hook).
# Processes up to --limit items per run; writes resume index; skips locked files.
import os, sys, ctypes, datetime, argparse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
audit_dir = os.path.join(ROOT, '.cleanup-audit')
targets_file = os.path.join(audit_dir, 'cleanup_targets.txt')
result_log = os.path.join(audit_dir, 'cleanup-delete-%s.log' % datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
resume_file = os.path.join(audit_dir, 'resume_index.txt')

ap = argparse.ArgumentParser()
ap.add_argument('--limit', type=int, default=10**9)
args = ap.parse_args()

kernel32 = ctypes.windll.kernel32

def del_file(path):
    ok = kernel32.DeleteFileW(path)
    if ok:
        return 'OK'
    err = ctypes.GetLastError()
    if err in (32, 5, 33):
        return 'LOCKED'
    return 'ERR%d' % err

def del_dir(path):
    ok = kernel32.RemoveDirectoryW(path)
    if ok:
        return 'OK'
    return 'ERR%d' % ctypes.GetLastError()

items = []
with open(targets_file, 'r', encoding='utf-8') as f:
    for line in f:
        line = line.rstrip('\n')
        if line:
            items.append(line)

start = 0
if os.path.exists(resume_file):
    try:
        start = int(open(resume_file).read().strip() or 0)
    except Exception:
        start = 0

deleted = locked = errors = skipped = 0
freed = 0
n = len(items)
end = min(n, start + args.limit)
with open(result_log, 'w', encoding='utf-8') as lf:
    lf.write('# delete start=%d end=%d total=%d\n' % (start, end, n))
    i = start
    while i < end:
        line = items[i]
        if line.startswith('DIR:'):
            d = line[4:].replace('/', '\\')
            try:
                if os.path.isdir(d):
                    r = del_dir(d)
                    lf.write('DIR %s %s\n' % (r, d))
            except Exception as e:
                lf.write('DIR ERR %s %s\n' % (e, d))
        else:
            p = line.replace('/', '\\')
            if not os.path.exists(p):
                skipped += 1
                lf.write('SKIP %s\n' % p)
            else:
                try:
                    sz = os.path.getsize(p)
                except Exception:
                    sz = 0
                r = del_file(p)
                if r == 'OK':
                    deleted += 1
                    freed += sz
                    lf.write('DEL %s\n' % p)
                elif r == 'LOCKED':
                    locked += 1
                    lf.write('LOCKED %s\n' % p)
                else:
                    errors += 1
                    lf.write('%s %s\n' % (r, p))
        i += 1
    open(resume_file, 'w').write(str(i))

print('processed=%d (start=%d end=%d total=%d) deleted=%d locked=%d errors=%d skipped=%d freed=%.1f MB' % (
    end - start, start, end, n, deleted, locked, errors, skipped, freed / 1048576.0))
