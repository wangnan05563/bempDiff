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
import java.util.concurrent.atomic.AtomicInteger;
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

    /** 自动递归解包的最大深度（防 zip-bomb 式深嵌套把调用栈/临时文件拖垮）。 */
    public static final int MAX_RECURSION_DEPTH = 12;
    /** 自动递归解包的节点总数上限（防超大批量把响应撑爆）。 */
    public static final int MAX_RECURSIVE_NODES = 20_000;

    private ArchiveTree() {}

    /**
     * 展开归档：返回内部条目列表，并跨旧/新两侧计算逐文件 ADDED/DELETED/MODIFIED/UNCHANGED。
     * key 可为顶层归档 key，或复合键 outer!/innerArchive（支持递归展开嵌套归档）。
     * 目录以结构标记节点呈现（isDir=true，不可再展开；zip 条目扁平，目录内容由同层文件条目体现）。
     */
    public static List<Map<String, Object>> computeChildren(PackageSnapshot oldSnap, PackageSnapshot newSnap, String key) throws IOException {
        List<String> parts = splitCompound(key);
        Path oldArch = extractInnerArchive(oldSnap, parts, true);
        Path newArch = extractInnerArchive(newSnap, parts, false);
        Map<String, long[]> oldMap = listArchive(oldArch);
        Map<String, long[]> newMap = listArchive(newArch);

        // 目录结构对比：由文件路径前缀 + zip 目录条目派生目录集合（空目录/移动目录也可见）。
        Map<String, Boolean> oldDirs = deriveDirs(oldMap.keySet());
        Map<String, Boolean> newDirs = deriveDirs(newMap.keySet());

        List<String> names = new ArrayList<>(oldMap.keySet());
        for (String n : newMap.keySet()) if (!names.contains(n)) names.add(n);
        for (String d : oldDirs.keySet()) if (!names.contains(d)) names.add(d);
        for (String d : newDirs.keySet()) if (!names.contains(d)) names.add(d);
        names.sort((a, b) -> {
            int sa = statusRank(a, oldMap, newMap, oldDirs, newDirs);
            int sb = statusRank(b, oldMap, newMap, oldDirs, newDirs);
            if (sa != sb) return Integer.compare(sa, sb);
            return a.compareTo(b);
        });

        List<Map<String, Object>> children = new ArrayList<>();
        for (String name : names) {
            boolean isDir = name.endsWith("/");
            boolean inOld = isDir ? oldDirs.containsKey(name) : oldMap.containsKey(name);
            boolean inNew = isDir ? newDirs.containsKey(name) : newMap.containsKey(name);
            String status;
            if (inOld && inNew) {
                if (isDir) {
                    // 目录只比对存在性（新增/删除/未变），内容变化由内部文件条目体现
                    status = "UNCHANGED";
                } else {
                    long[] a = oldMap.get(name), b = newMap.get(name);
                    status = (a[0] == b[0] && a[1] == b[1]) ? "UNCHANGED" : "MODIFIED";
                }
            } else if (inOld) status = "DELETED";
            else status = "ADDED";
            FileClass fc = isDir ? FileClass.OTHER : PackageParser.classify(name);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("key", key + "!/" + name);
            node.put("status", status);
            node.put("fileClass", fc.name());
            node.put("size", isDir ? 0L : (inNew ? newMap.get(name)[0] : oldMap.get(name)[0]));
            node.put("name", name);
            node.put("isDir", isDir);
            // 目录/普通文件不可展开；嵌套归档（zip/jar/war 等）可继续递归展开
            node.put("expandable", !isDir && isArchiveType(fc));
            children.add(node);
        }
        return children;
    }

    /**
     * 自动递归解包：把归档（含任意深度嵌套归档）一次性展开为完整的嵌套差异树。
     * 每层节点与 {@link #computeChildren} 同构（key/status/fileClass/name/size/expandable），
     * 嵌套归档节点额外带 children（其内部条目清单），目录节点为结构标记（不可再展开）。
     * 返回形如 {key: &lt;topKey&gt;, children: [节点...]} 的树；受 MAX_RECURSION_DEPTH /
     * MAX_RECURSIVE_NODES 双重护栏约束。
     */
    public static Map<String, Object> recursiveUnpack(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                                      String key) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("key", key);
        root.put("children", buildRecursive(oldSnap, newSnap, key, 0, new AtomicInteger(0)));
        return root;
    }

    private static List<Map<String, Object>> buildRecursive(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                                            String key, int depth, AtomicInteger counter) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (depth >= MAX_RECURSION_DEPTH) return out; // 触顶：不再下钻（节点保留，仍可手动展开）
        List<Map<String, Object>> flat = computeChildren(oldSnap, newSnap, key);
        for (Map<String, Object> child : flat) {
            if (counter.incrementAndGet() > MAX_RECURSIVE_NODES) return out; // 超节点上限：截断
            if (Boolean.TRUE.equals(child.get("expandable")) && !Boolean.TRUE.equals(child.get("isDir"))) {
                child.put("children", buildRecursive(oldSnap, newSnap, (String) child.get("key"), depth + 1, counter));
            }
            out.add(child);
        }
        return out;
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
            // S1 修复：归档清单对比已生成 diffText，临时抽取文件可删（不再被引用）。
            deleteTemp(tmpOld);
            deleteTemp(tmpNew);
        } else if (fc == FileClass.OFFICE) {
            // Office 文档（docx/xlsx/pptx）：解析内容为文本后行级 diff（需求：文档内容对比）
            u = new OfficeTextDiff().diffBytes(oldBytes, newBytes, innerPath, fc, rules);
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

    static int statusRank(String name, Map<String, long[]> oldMap, Map<String, long[]> newMap,
                          Map<String, Boolean> oldDirs, Map<String, Boolean> newDirs) {
        boolean isDir = name.endsWith("/");
        boolean inOld = isDir ? oldDirs.containsKey(name) : oldMap.containsKey(name);
        boolean inNew = isDir ? newDirs.containsKey(name) : newMap.containsKey(name);
        if (inOld && inNew) {
            if (isDir) return 3; // 目录仅存在性比对：未变
            long[] a = oldMap.get(name), b = newMap.get(name);
            return (a[0] == b[0] && a[1] == b[1]) ? 3 : 0; // UNCHANGED=3, MODIFIED=0
        }
        return inOld ? 1 : 2; // DELETED=1, ADDED=2
    }

    /** 由条目名集合派生目录集合：目录名以 "/" 结尾（含 zip 显式目录条目与文件路径前缀）。 */
    static Map<String, Boolean> deriveDirs(Iterable<String> names) {
        Map<String, Boolean> dirs = new TreeMap<>();
        for (String n : names) {
            if (n.endsWith("/")) { // zip 显式目录条目（空目录）
                dirs.put(n, Boolean.TRUE);
                continue;
            }
            int idx = n.indexOf('/');
            while (idx >= 0) {
                dirs.put(n.substring(0, idx + 1), Boolean.TRUE);
                idx = n.indexOf('/', idx + 1);
            }
        }
        return dirs;
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

    /** 把顶层归档 key 解析为磁盘 Path（真实磁盘归档文件→直接打开；包内条目/嵌套归档→临时抽取）。 */
    static Path resolveTopArchive(PackageSnapshot snap, String topKey) throws IOException {
        if (snap == null) return null;
        LogicalEntry e = snap.getEntries().get(topKey);
        if (e == null) return null;
        EntrySource src = e.getSrc();
        if (src != null && !src.isNested()) {
            String outer = src.getOuterEntry();
            if (outer != null) {
                Path candidate = Paths.get(outer);
                // 文件夹模式（或真实压缩包文件）：outerEntry 就是磁盘上的归档文件 → 直接打开。
                // 包对比模式（直接比对两个 .zip/.war）：顶层条目 key 是外层包内的条目名，
                // outerEntry 也是该条目名（如 "lib/bundle.zip"），并非独立磁盘文件，
                // 必须从 snap.getFile()（外层包）抽取该条目后再打开——否则 ZipFile 打开失败
                // （此前实测：NoSuchFileException: lib\bundle.zip，嵌套归档无法解包）。
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }
        // 包内条目（含嵌套归档）：从 snap.getFile() 读取字节落临时文件
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

    /** 枚举归档条目为 name → {size, crc}（CRC 用于判断同内容修改）。目录条目也纳入（空目录/结构变化可见）。 */
    static Map<String, long[]> listArchive(Path archive) throws IOException {
        Map<String, long[]> m = new TreeMap<>();
        if (archive == null) return m;
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int count = 0;
            while (en.hasMoreElements()) {
                if (count++ > 200_000) { m.put("... (已截断)", new long[]{0, 0}); break; }
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) {
                    m.put(n.endsWith("/") ? n : n + "/", new long[]{0, 0});
                    continue;
                }
                m.put(n, new long[]{ e.getSize() < 0 ? 0 : e.getSize(), e.getCrc() });
            }
        } catch (IOException ioe) {
            throw new IOException("无法读取归档条目: " + ioe.getMessage(), ioe);
        }
        return m;
    }

    static Path writeTemp(byte[] b) throws IOException {
        Path tmp = Files.createTempFile("bempdiff-entry-", ".bin");
        Files.write(tmp, b);
        // S1 修复：临时抽取文件用后即删，避免运行期 %TEMP% 持续累积（磁盘泄漏）。
        // 注册 JVM 退出时清理；调用方在 diff 完成后也应尽快用完落盘的文件（此处统一兜底）。
        try { tmp.toFile().deleteOnExit(); } catch (Exception ignored) {}
        return tmp;
    }

    /** S1 修复：安全删除临时文件（失败静默，不干扰主流程）。 */
    static void deleteTemp(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
