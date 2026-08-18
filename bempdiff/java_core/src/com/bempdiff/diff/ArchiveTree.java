package com.bempdiff.diff;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.server.CompareOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 归档递归展开与内部条目内容 diff 的纯逻辑层。
 *
 * <p>原先这段链路嵌在 {@code BempServer.handleEntry/handleEntryChildren} 里（private 方法，
 * 难以脱离 HTTP 层做单元测试）。抽出为 public static 后，既能让 HTTP 处理器保持「薄」，
 * 也便于 {@code ArchiveChildrenTest} 直接构造真实的 PackageSnapshot 做确定性断言。</p>
 *
 * <p>复合键约定：{@code outerKey!/innerPath}，例如 {@code app.zip!/WEB-INF/classes/A.class}；
 * 嵌套归档继续以 {@code !/} 下钻，如 {@code app.zip!/lib/nested.jar!/A.class}。</p>
 */
public final class ArchiveTree {

    /** 单条目读取硬上限（防 zip bomb / 巨型条目拖垮 JVM）。 */
    public static final long ENTRY_READ_CAP = 64L * 1024 * 1024;

    private ArchiveTree() {}

    /**
     * 展开归档：返回内部条目列表，并跨旧/新两侧计算逐文件 ADDED/DELETED/MODIFIED/UNCHANGED。
     * key 可为顶层归档 key，或复合键 outer!/innerArchive（支持递归展开嵌套归档）。
     */
    public static List<Map<String, Object>> computeChildren(PackageSnapshot oldSnap, PackageSnapshot newSnap, String key) throws IOException {
        List<String> parts = splitCompound(key);
        Path oldArch = extractInnerArchive(oldSnap, parts, true);
        Path newArch = extractInnerArchive(newSnap, parts, false);
        Map<String, long[]> oldMap = listArchive(oldArch);
        Map<String, long[]> newMap = listArchive(newArch);

        List<String> names = new ArrayList<>(oldMap.keySet());
        for (String n : newMap.keySet()) if (!names.contains(n)) names.add(n);
        names.sort((a, b) -> {
            int sa = statusRank(a, oldMap, newMap), sb = statusRank(b, oldMap, newMap);
            if (sa != sb) return Integer.compare(sa, sb);
            return a.compareTo(b);
        });

        List<Map<String, Object>> children = new ArrayList<>();
        for (String name : names) {
            boolean inOld = oldMap.containsKey(name);
            boolean inNew = newMap.containsKey(name);
            String status;
            if (inOld && inNew) {
                long[] a = oldMap.get(name), b = newMap.get(name);
                status = (a[0] == b[0] && a[1] == b[1]) ? "UNCHANGED" : "MODIFIED";
            } else if (inOld) status = "DELETED";
            else status = "ADDED";
            FileClass fc = PackageParser.classify(name);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("key", key + "!/" + name);
            node.put("status", status);
            node.put("fileClass", fc.name());
            node.put("size", inNew ? newMap.get(name)[0] : oldMap.get(name)[0]);
            node.put("name", name);
            node.put("expandable", isArchiveType(fc));
            children.add(node);
        }
        return children;
    }

    /**
     * 内部条目内容 diff：按扩展名分派 反编译(class) / 嵌套归档清单 / 文本对比 / 不支持(fail)。
     * 顶层归档（无 innerPath）由调用方直接走 ArchiveDiff，不在此处理。
     */
    public static DecompiledUnit computeInnerEntry(PackageSnapshot oldSnap, PackageSnapshot newSnap, CompareOptions opts, String key, Path cfrJar, String javaBin) throws IOException {
        List<String> parts = splitCompound(key);
        if (parts.size() < 2) {
            return DecompiledUnit.fail(key, "非内部条目 key（缺少 !/ 复合路径）");
        }
        String innerPath = parts.get(parts.size() - 1);
        FileClass fc = PackageParser.classify(innerPath);
        byte[] oldBytes = readInnerEntryBytes(oldSnap, parts, true);
        byte[] newBytes = readInnerEntryBytes(newSnap, parts, false);
        DiffRules rules = opts.toDiffRules();
        DecompiledUnit u;
        if (fc == FileClass.CLASS) {
            u = new Decompiler(cfrJar, javaBin).decompileBytes(oldBytes, newBytes, innerPath, rules);
        } else if (fc == FileClass.ARCHIVE || fc == FileClass.JAR) {
            Path tmpOld = (oldBytes != null) ? writeTemp(oldBytes) : null;
            Path tmpNew = (newBytes != null) ? writeTemp(newBytes) : null;
            u = new ArchiveDiff().diff(innerPath, tmpOld, tmpNew);
        } else if (fc.isTextDiffable()) {
            u = new FrontendTextDiff().diffBytes(oldBytes, newBytes, innerPath, fc, rules);
        } else {
            u = DecompiledUnit.fail(key, "内部条目为二进制（" + fc.name() + "），不支持内容 diff");
        }
        if (oldBytes == null && newBytes == null) {
            u = DecompiledUnit.fail(key, "归档两侧均缺此内部条目");
        }
        return u;
    }

    // ===================== 下方为抽取/枚举辅助（private static） =====================

    static int statusRank(String name, Map<String, long[]> oldMap, Map<String, long[]> newMap) {
        boolean inOld = oldMap.containsKey(name), inNew = newMap.containsKey(name);
        if (inOld && inNew) {
            long[] a = oldMap.get(name), b = newMap.get(name);
            return (a[0] == b[0] && a[1] == b[1]) ? 3 : 0; // UNCHANGED=3, MODIFIED=0
        }
        return inOld ? 1 : 2; // DELETED=1, ADDED=2
    }

    static boolean isArchiveType(FileClass fc) {
        return fc == FileClass.ARCHIVE || fc == FileClass.JAR;
    }

    /** 把 "a!/b!/c" 按 "!/" 拆成 ["a","b","c"]（兼容顶层无 "!/" 的 key）。 */
    public static List<String> splitCompound(String key) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (true) {
            int idx = key.indexOf("!/", start);
            if (idx < 0) { parts.add(key.substring(start)); break; }
            parts.add(key.substring(start, idx));
            start = idx + 2;
        }
        return parts;
    }

    /** 把顶层归档 key 解析为磁盘 Path（非嵌套→原文件；嵌套→临时抽取）。 */
    static Path resolveTopArchive(PackageSnapshot snap, String topKey) throws IOException {
        if (snap == null) return null;
        LogicalEntry e = snap.getEntries().get(topKey);
        if (e == null) return null;
        EntrySource src = e.getSrc();
        if (src != null && !src.isNested()) {
            String outer = src.getOuterEntry();
            if (outer != null) return Paths.get(outer);
        }
        // 嵌套归档：读字节落临时文件
        byte[] b = new PackageParser().readEntryBytes(snap, e);
        if (b == null) return null;
        return writeTemp(b);
    }

    /** 顺着复合键链 descent，落在最后一个分段所指的"归档"上（用于 children 枚举 / 嵌套归档反编译）。 */
    static Path extractInnerArchive(PackageSnapshot snap, List<String> parts, boolean oldSide) throws IOException {
        Path cur = resolveTopArchive(snap, parts.get(0));
        if (cur == null) return null;
        for (int i = 1; i < parts.size(); i++) {
            Path next = extractEntryToTemp(cur, parts.get(i));
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    /** 顺着复合键链 descent，读取最后一个分段所指条目的字节（用于内部文件反编译/文本 diff）。 */
    static byte[] readInnerEntryBytes(PackageSnapshot snap, List<String> parts, boolean oldSide) throws IOException {
        if (parts.size() < 2) return null;
        Path cur = resolveTopArchive(snap, parts.get(0));
        if (cur == null) return null;
        for (int i = 1; i < parts.size() - 1; i++) {
            Path next = extractEntryToTemp(cur, parts.get(i));
            if (next == null) return null;
            cur = next;
        }
        return readZipEntryBytes(cur, parts.get(parts.size() - 1));
    }

    /** 把归档内某条目抽取到临时文件（供进一步打开/枚举）。条目缺失或过大返回 null。 */
    static Path extractEntryToTemp(Path archive, String entryPath) throws IOException {
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            ZipEntry ze = zf.getEntry(entryPath);
            if (ze == null || ze.isDirectory()) return null;
            if (ze.getSize() > ENTRY_READ_CAP) return null;
            byte[] b = readZipEntryBytes(archive, entryPath);
            if (b == null) return null;
            return writeTemp(b);
        }
    }

    /** 读归档内某条目的全部字节（受 ENTRY_READ_CAP 约束）。 */
    static byte[] readZipEntryBytes(Path archive, String entryPath) throws IOException {
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            ZipEntry ze = zf.getEntry(entryPath);
            if (ze == null || ze.isDirectory()) return null;
            if (ze.getSize() > ENTRY_READ_CAP) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = zf.getInputStream(ze)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > ENTRY_READ_CAP) throw new IOException("条目过大(疑似 zip bomb): " + entryPath);
                    bos.write(buf, 0, n);
                }
            }
            return bos.toByteArray();
        }
    }

    /** 枚举归档条目为 name → {size, crc}（CRC 用于判断同内容修改）。 */
    static Map<String, long[]> listArchive(Path archive) throws IOException {
        Map<String, long[]> m = new TreeMap<>();
        if (archive == null) return m;
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int count = 0;
            while (en.hasMoreElements()) {
                if (count++ > 200_000) { m.put("... (已截断)", new long[]{0, 0}); break; }
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                m.put(e.getName(), new long[]{ e.getSize() < 0 ? 0 : e.getSize(), e.getCrc() });
            }
        } catch (IOException ioe) {
            throw new IOException("无法读取归档条目: " + ioe.getMessage(), ioe);
        }
        return m;
    }

    static Path writeTemp(byte[] b) throws IOException {
        Path tmp = Files.createTempFile("bempdiff-entry-", ".bin");
        Files.write(tmp, b);
        return tmp;
    }
}
