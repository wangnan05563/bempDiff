# -*- coding: utf-8 -*-
"""SQLite 全文检索 —— 只读打开，在所有表/列里 LIKE 匹配关键词。

典型用途：在 Codex / Trae 等工具的 sqlite 历史库中定位「某段配置/文本最初是谁写进去的」，
即从一堆会话记录里挖出权威原文（本项目曾靠它还原被损坏的 skill 配置）。

用法：
    python sqlite_grep.py --db D:/x/thread_history.sqlite --needle dependency_groups
    python sqlite_grep.py --db D:/x/logs.sqlite@logs --db D:/x/hist.sqlite@items,turns \\
        --needle foo --needle bar
    python sqlite_grep.py --db D:/x/a.sqlite --needle foo --show 3   # 附带 3 条片段样本

--db 语法：<路径>[@表1,表2]；省略 @表 时自动枚举库中所有表。
只读模式（uri mode=ro），绝不修改源库。
"""
import argparse
import sqlite3
import sys


def parse_db(spec):
    if "@" in spec:
        path, tables = spec.rsplit("@", 1)
        return path, [t.strip() for t in tables.split(",") if t.strip()]
    return spec, None


def list_tables(conn):
    return [r[0] for r in conn.execute(
        "select name from sqlite_master where type='table' order by name")]


def text_like(value):
    """把任意列值转成可 LIKE 的字符串（bytes 走 latin-1 兜底，避免 UnicodeDecodeError）。"""
    if value is None:
        return ""
    if isinstance(value, (bytes, bytearray)):
        for enc in ("utf-8", "gbk", "latin-1"):
            try:
                return value.decode(enc)
            except Exception:
                continue
        return repr(value)
    return str(value)


def main():
    ap = argparse.ArgumentParser(description="SQLite 全文检索（只读）")
    ap.add_argument("--db", action="append", required=True,
                    help="库路径[@表1,表2]，可重复")
    ap.add_argument("--needle", action="append", required=True,
                    help="关键词，可重复（子串匹配）")
    ap.add_argument("--show", type=int, default=0,
                    help="每个命中附带打印 N 条含关键词的片段样本（默认 0）")
    ap.add_argument("--snippet", type=int, default=200, help="样本片段截断长度")
    ap.add_argument("--ignore-case", action="store_true", help="忽略大小写")
    args = ap.parse_args()

    total_hits = 0
    needle_lower = [n.lower() for n in args.needle]

    for spec in args.db:
        path, tables = parse_db(spec)
        print("=" * 74)
        print(path)
        uri = "file:%s?mode=ro" % path.replace("\\", "/")
        try:
            conn = sqlite3.connect(uri, uri=True)
        except Exception as ex:
            print("  连接失败: %s" % ex, file=sys.stderr)
            continue

        try:
            if tables is None:
                tables = list_tables(conn)
                print("  自动枚举表: %s" % (", ".join(tables) or "(无)"))
            for tb in tables:
                try:
                    cols = [r[1] for r in conn.execute('PRAGMA table_info("%s")' % tb)]
                except Exception as ex:
                    print("  %s: %s" % (tb, ex))
                    continue
                if not cols:
                    continue
                print("  --- %s  列: %s" % (tb, ", ".join(cols)))
                for col in cols:
                    for needle in args.needle:
                        op = "like" if args.ignore_case else "glob"
                        try:
                            if args.ignore_case:
                                sql = ('select count(*) from "%s" '
                                       'where lower(cast("%s" as text)) like ?' % (tb, col))
                                n = conn.execute(sql, ("%" + needle.lower() + "%",)).fetchone()[0]
                            else:
                                sql = ('select count(*) from "%s" '
                                       'where cast("%s" as text) like ?' % (tb, col))
                                n = conn.execute(sql, ("%" + needle + "%",)).fetchone()[0]
                        except Exception:
                            continue
                        if not n:
                            continue
                        total_hits += n
                        print("      %-24s LIKE %-26s -> %d 行" % (col, needle, n))
                        if args.show > 0:
                            sel = ('select cast("%s" as text) from "%s" '
                                   'where cast("%s" as text) like ? limit ?' % (col, tb, col))
                            try:
                                for (v,) in conn.execute(sel, ("%" + needle + "%", args.show)):
                                    t = text_like(v)
                                    idx = t.find(needle)
                                    s = max(0, idx - args.snippet // 2)
                                    frag = t[s:s + args.snippet].replace("\n", "\\n")
                                    print("          ...%s..." % frag)
                            except Exception as ex:
                                print("          (取样失败: %s)" % ex)
        finally:
            conn.close()

    print("=" * 74)
    print("命中合计: %d" % total_hits)
    return 0 if total_hits else 1


if __name__ == "__main__":
    sys.exit(main())
