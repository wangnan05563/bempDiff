import os, glob


def can_gbk(ch):
    try:
        ch.encode("gbk")
        return True
    except UnicodeEncodeError:
        return False


d = r"D:\code\otherProjects\18_comparePakage\tooling\scripts"
for p in sorted(glob.glob(os.path.join(d, "*.bat"))):
    b = open(p, "rb").read()
    print("===", os.path.basename(p), "===")
    print("first3:", b[:3].hex(), "BOM" if b[:3] == b"\xef\xbb\xbf" else "no-BOM")
    s = b.decode("utf-8", errors="replace")
    try:
        s.encode("gbk")
        print("gbk-encodable: YES")
    except UnicodeEncodeError:
        bad = sorted({ch for ch in s if ch not in "\n\r\t " and not can_gbk(ch)})
        print("gbk-encodable: NO, offending:", bad)
