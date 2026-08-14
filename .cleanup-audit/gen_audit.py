#!/usr/bin/env python3
# Generate cleanup target list + SHA256 audit log (pre-deletion evidence).
import os, hashlib, datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # .cleanup-audit/.. = workspace
audit_dir = os.path.join(ROOT, '.cleanup-audit')
os.makedirs(audit_dir, exist_ok=True)

files = []

# 1) build/ (recursive compile output)
b = os.path.join(ROOT, 'build')
if os.path.isdir(b):
    for dp, dn, fn in os.walk(b):
        for f in fn:
            files.append(os.path.join(dp, f))

# 2) prototype/**/*.class (recursive compile intermediates)
for dp, dn, fn in os.walk(os.path.join(ROOT, 'prototype')):
    for f in fn:
        if f.endswith('.class'):
            files.append(os.path.join(dp, f))

# 3) logs/* (files directly in logs/, keep the directory)
logs = os.path.join(ROOT, 'logs')
if os.path.isdir(logs):
    for f in os.listdir(logs):
        p = os.path.join(logs, f)
        if os.path.isfile(p):
            files.append(p)

# 4) dist/BempDiff._old_* (redundant old packaging backup, recursive)
old = os.path.join(ROOT, 'dist', 'BempDiff._old_20260813222712')
if os.path.isdir(old):
    for dp, dn, fn in os.walk(old):
        for f in fn:
            files.append(os.path.join(dp, f))

# Directories to remove after files (bottom-up by depth desc)
dirs = []
if os.path.isdir(b):
    dirs.append(b)
if os.path.isdir(old):
    sub = []
    for dp, dn, fn in os.walk(old):
        for d in dn:
            sub.append(os.path.join(dp, d))
    sub.append(old)
    dirs.extend(sorted(sub, key=lambda x: -x.count(os.sep)))

ts = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
targets_file = os.path.join(audit_dir, 'cleanup_targets.txt')
audit_log = os.path.join(audit_dir, 'cleanup-%s.log' % ts)

def npath(p):
    return p.replace('\\', '/')

with open(targets_file, 'w', encoding='utf-8') as tf:
    for p in files:
        tf.write(npath(p) + '\n')
    for d in dirs:
        tf.write('DIR:' + npath(d) + '\n')

with open(audit_log, 'w', encoding='utf-8') as lf:
    lf.write('# cleanup audit %s\n' % ts)
    lf.write('# total_files=%d total_dirs=%d\n' % (len(files), len(dirs)))
    for p in files:
        try:
            sz = os.path.getsize(p)
            h = hashlib.sha256()
            with open(p, 'rb') as fh:
                for chunk in iter(lambda: fh.read(1 << 20), b''):
                    h.update(chunk)
            lf.write('%s|%d|%s\n' % (h.hexdigest(), sz, npath(p)))
        except Exception as e:
            lf.write('ERROR|%s|%s\n' % (e, npath(p)))

print('targets=%d dirs=%d' % (len(files), len(dirs)))
print('targets_file=' + npath(targets_file))
print('audit_log=' + npath(audit_log))
