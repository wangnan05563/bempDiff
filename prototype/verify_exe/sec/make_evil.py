import zipfile, os
os.makedirs("verify_exe/sec", exist_ok=True)
# 良性 jar
with zipfile.ZipFile("verify_exe/sec/benign.jar", "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("com/foo/A.class", b"\xca\xfe\xba\xbe" + b"\x00" * 20)
    z.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
# 恶意 jar：含 ../ 穿越条目
with zipfile.ZipFile("verify_exe/sec/evil.jar", "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("com/foo/A.class", b"\xca\xfe\xba\xbe" + b"\x00" * 20)
    z.writestr("../../pwned.txt", b"ESCAPED_WRITE_PROOF")
    z.writestr("WEB-INF/classes/../../pwned.class", b"\xca\xfe\xba\xbe" + b"\x00" * 20)
print("malicious jars created")
