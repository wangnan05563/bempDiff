package com.bempdiff.diff;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.parse.PackageVersion;
import com.bempdiff.server.CompareOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        return computeChildren(oldSnap, newSnap, key, null);
    }

    public static List<Map<String, Object>> computeChildren(PackageSnapshot oldSnap, PackageSnapshot newSnap, String key,
                                                        java.util.List<String> ignoreExtensions) throws IOException {
        List<String> parts = splitCompound(key);
        Path oldArch = extractInnerArchive(oldSnap, parts);
        Path newArch = extractInnerArchive(newSnap, parts);
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
            Map<String, Object> node = buildChildNode(name, key, oldMap, newMap, oldDirs, newDirs, ignoreExtensions);
            if (!node.isEmpty()) children.add(node);
        }
        return children;
    }

    /** 为单个条目构建子节点；命中忽略扩展名的条目返回空 Map（调用方跳过）。 */
    private static Map<String, Object> buildChildNode(String name, String key, Map<String, long[]> oldMap,
                                                      Map<String, long[]> newMap, Map<String, Boolean> oldDirs,
                                                      Map<String, Boolean> newDirs, java.util.List<String> ignoreExtensions) {
        // 比对级忽略扩展名：嵌套归档内部条目（如 jar!/META-INF/MANIFEST.MF）同样跳过，
        // 否则仅顶层过滤而嵌套内部 .MF 仍会出现（用户反馈的"嵌套不生效"根因）。
        if (!name.endsWith("/") && isIgnoredExt(name, ignoreExtensions)) return Collections.emptyMap();
        boolean isDir = name.endsWith("/");
        boolean inOld = isDir ? oldDirs.containsKey(name) : oldMap.containsKey(name);
        boolean inNew = isDir ? newDirs.containsKey(name) : newMap.containsKey(name);
        String status = entryStatus(isDir, inOld, inNew, oldMap, newMap, name);
        FileClass fc = isDir ? FileClass.OTHER : PackageParser.classify(name);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", key + "!/" + name);
        node.put("status", status);
        node.put("fileClass", fc.name());
        // 目录无尺寸概念（记 0）；文件取所在侧的字节数（单层选择，避免嵌套三元）
        long size;
        if (isDir) size = 0L;
        else size = inNew ? newMap.get(name)[0] : oldMap.get(name)[0];
        node.put("size", size);
        node.put("name", name);
        node.put("isDir", isDir);
        // 目录/普通文件不可展开；嵌套归档（zip/jar/war 等）可继续递归展开
        node.put("expandable", !isDir && isArchiveType(fc));
        return node;
    }

    /** 判定条目在差异树中的状态（新增/删除/未变/修改）；目录只看存在性，文件再比大小。 */
    private static String entryStatus(boolean isDir, boolean inOld, boolean inNew,
                                      Map<String, long[]> oldMap, Map<String, long[]> newMap, String name) {
        if (inOld && inNew) {
            if (isDir) return "UNCHANGED";
            long[] a = oldMap.get(name);
            long[] b = newMap.get(name);
            return (a[0] == b[0] && a[1] == b[1]) ? "UNCHANGED" : "MODIFIED";
        }
        return inOld ? "DELETED" : "ADDED";
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
        return recursiveUnpack(oldSnap, newSnap, key, null);
    }

    public static Map<String, Object> recursiveUnpack(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                                  String key, java.util.List<String> ignoreExtensions) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("key", key);
        root.put("children", buildRecursive(oldSnap, newSnap, key, 0, new AtomicInteger(0), ignoreExtensions));
        return root;
    }

    private static List<Map<String, Object>> buildRecursive(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                                            String key, int depth, AtomicInteger counter,
                                                            java.util.List<String> ignoreExtensions) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        if (depth >= MAX_RECURSION_DEPTH) return out; // 触顶：不再下钻（节点保留，仍可手动展开）
        List<Map<String, Object>> flat = computeChildren(oldSnap, newSnap, key, ignoreExtensions);
        for (Map<String, Object> child : flat) {
            if (counter.incrementAndGet() > MAX_RECURSIVE_NODES) return out; // 超节点上限：截断
            if (Boolean.TRUE.equals(child.get("expandable")) && !Boolean.TRUE.equals(child.get("isDir"))) {
                child.put("children", buildRecursive(oldSnap, newSnap, (String) child.get("key"), depth + 1, counter, ignoreExtensions));
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
        byte[] oldBytes = readInnerEntryBytes(oldSnap, parts).orElse(null);
        byte[] newBytes = readInnerEntryBytes(newSnap, parts).orElse(null);
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
            long[] a = oldMap.get(name);
            long[] b = newMap.get(name);
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

    /** 判断条目名是否命中忽略扩展名集合（统一走 ParseConfig.ignoredExt，口径一致）。 */
    private static boolean isIgnoredExt(String name, java.util.List<String> ignores) {
        return com.bempdiff.config.ParseConfig.ignoredExt(name, ignores);
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
        // S2 修复：顶层归档 key 在该侧缺失、对侧是「同名不同版本」包（如 BEMP...M059.zip vs M061.zip）时，
        // 若该侧恰有唯一的同基名不同版本条目，则用它替代——让两个版本包自动配对对比内部条目，
        // 而非整包判为 DELETED/ADDED。
        if (e == null) {
            String alt = matchSameBaseVersion(snap, topKey);
            if (alt != null) e = snap.getEntries().get(alt);
        }
        if (e == null) return null;
        Path direct = directArchiveFile(e.getSrc());
        if (direct != null) return direct;
        // 包内条目（含嵌套归档）：从 snap.getFile() 读取落临时文件。
        // 非嵌套条目：直接从外层包的 ZipFile 流式复制该条目到临时文件，不驻留整 byte[]——
        // 大 war 内嵌的 zip 若整存内存再写盘，内存与磁盘峰值双高（磁盘空间不足时更易触顶）。
        if (!e.getSrc().isNested()) {
            Path fallback = writeEntryStreaming(snap.getFile(), e.getSrc().getOuterEntry());
            if (fallback != null) return fallback;
        }
        byte[] b = new PackageParser().readEntryBytes(snap, e);
        if (b == null) return null;
        return writeTemp(b);
    }

    /** 文件夹/真实压缩包模式：outerEntry 是磁盘上的归档文件时直接打开（否则 null，由调用方走包内抽取）。
     *  包对比模式的 outerEntry 仅是包内条目名（如 "lib/bundle.zip"），并非独立磁盘文件，须从外层包抽取。 */
    private static Path directArchiveFile(EntrySource src) {
        if (src != null && !src.isNested()) {
            String outer = src.getOuterEntry();
            if (outer != null) {
                Path candidate = Paths.get(outer);
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }
        return null;
    }

    /** 从外层 zip 流式把「条目名」复制到临时文件（返回 null 表示不适用/条目缺失，交由调用方退化为整读）。 */
    private static Path writeEntryStreaming(Path archiveFile, String entryName) throws IOException {
        if (archiveFile == null || entryName == null) return null;
        if (!Files.isRegularFile(archiveFile)) return null;
        try (ZipFile zf = new ZipFile(archiveFile.toFile())) {
            ZipEntry ze = zf.getEntry(entryName);
            if (ze == null) return null;
            return writeTempStream(zf.getInputStream(ze));
        }
    }

    /** 流式写临时文件：从 InputStream 边读边写，不整存 byte[]（降低内存与磁盘峰值）。 */
    private static Path writeTempStream(InputStream in) throws IOException {
        if (in == null) return null;
        Path tmp = Files.createTempFile("bempdiff-entry-", ".bin");
        try (InputStream is = in) {
            Files.copy(is, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            // 用后即删：避免运行期 %TEMP% 持续累积（磁盘泄漏）。
            try { tmp.toFile().deleteOnExit(); } catch (Exception ignored) {
                // deleteOnExit 注册失败可忽略：临时文件仍正常使用，残留由系统清理兜底
            }
        }
        return tmp;
    }

    /** 在快照条目中查找与 key「同名不同版本」的唯一归档条目；多候选返回 null（不冒险误配）。 */
    private static String matchSameBaseVersion(PackageSnapshot snap, String key) {
        String found = null;
        for (String k : snap.getEntries().keySet()) {
            if (k.equals(key)) continue;
            if (PackageVersion.sameBaseDifferentVersion(key, k)) {
                if (found != null) return null; // 多个候选 → 维持原状（DELETED/ADDED）
                found = k;
            }
        }
        return found;
    }

    /** 顺着复合键链 descent，落在最后一个分段所指的"归档"上（用于 children 枚举 / 嵌套归档反编译）。 */
    static Path extractInnerArchive(PackageSnapshot snap, List<String> parts) throws IOException {
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
    public static Optional<byte[]> readInnerEntryBytes(PackageSnapshot snap, List<String> parts) throws IOException {
        if (parts.size() < 2) return Optional.empty();
        Path cur = resolveTopArchive(snap, parts.get(0));
        if (cur == null) return Optional.empty();
        for (int i = 1; i < parts.size() - 1; i++) {
            Path next = extractEntryToTemp(cur, parts.get(i));
            if (next == null) return Optional.empty();
            cur = next;
        }
        return readZipEntryBytes(cur, parts.get(parts.size() - 1));
    }

    /** 把归档内某条目抽取到临时文件（供进一步打开/枚举）。条目缺失或过大返回 null。 */
    static Path extractEntryToTemp(Path archive, String entryPath) throws IOException {
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            ZipEntry ze = resolveEntry(zf, entryPath);
            if (ze == null || ze.isDirectory()) return null;
            if (ze.getSize() > ENTRY_READ_CAP) return null;
            byte[] b = readZipEntryBytes(archive, ze.getName()).orElse(null);
            if (b == null) return null;
            return writeTemp(b);
        }
    }

    /** 读归档内某条目的全部字节（受 ENTRY_READ_CAP 约束）；缺失/目录/过大返回 Optional.empty()。 */
    static Optional<byte[]> readZipEntryBytes(Path archive, String entryPath) throws IOException {
        try (ZipFile zf = new ZipFile(archive.toFile())) {
            ZipEntry ze = resolveEntry(zf, entryPath);
            if (ze == null || ze.isDirectory()) return Optional.empty();
            if (ze.getSize() > ENTRY_READ_CAP) return Optional.empty();
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
            return Optional.of(bos.toByteArray());
        }
    }

    /**
     * 在归档中定位条目：先精确路径匹配，缺失时尝试「同基名不同版本」唯一匹配
     * （S2 修复：嵌套条目如 lib/x-1.0.jar vs x-2.0.jar 也自动配对）；多候选返回 null。
     */
    private static ZipEntry resolveEntry(ZipFile zf, String entryPath) {
        ZipEntry ze = zf.getEntry(entryPath);
        if (ze != null) return ze;
        String alt = null;
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry cand = en.nextElement();
            String cn = cand.getName();
            if (!cand.isDirectory() && !cn.equals(entryPath) && PackageVersion.sameBaseDifferentVersion(entryPath, cn)) {
                    if (alt != null) return null; // 多个候选 → 不匹配
                    alt = cn;
                }
        }
        return alt == null ? null : zf.getEntry(alt);
    }

    /** 枚举归档条目为 name → {size, crc}（CRC 用于判断同内容修改）。目录条目也纳入（空目录/结构变化可见）。
     *  <p>附加：部分归档工具（ant / 部分 Spring Boot 打包器等）把目录条目写成
     *  「name 不以 / 结尾 + size=0 + 设了目录外部属性」的形式，Java 的
     *  {@link ZipEntry#isDirectory()} 只看 name 末尾 '/'，导致此类 0 字节目录被
     *  误识别为 0 字节文件，参与差异统计（用户截图：嵌套 jar 内 log4j2 显示
     *  OTHER / 0B / 未变，错误纳入未变计数）。此处做一次启发式升级：
     *  「name 不以 / 结尾 & size=0 & m 中至少一个其他条目以 name+"/" 开头」
     *  → 视为目录条目。误伤面极窄：常规文件系统中同名 0 字节空文件与同名子文件
     *  不可能同时存在，heuristic 倾向于更常见的"打包器漏写尾斜杠"场景。</p> */
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
                } else {
                    m.put(n, new long[]{ e.getSize() < 0 ? 0 : e.getSize(), e.getCrc() });
                }
            }
            // 伪目录条目升级：见方法 javadoc。
            if (!m.isEmpty()) {
                Set<String> dirPrefixes = new HashSet<>();
                for (String key : m.keySet()) {
                    if (key.startsWith("... (")) continue;
                    int idx = 0;
                    while ((idx = key.indexOf('/', idx)) >= 0) {
                        dirPrefixes.add(key.substring(0, idx + 1));
                        idx++;
                    }
                }
                List<String> toUpgrade = new ArrayList<>();
                for (Map.Entry<String, long[]> me : m.entrySet()) {
                    String n = me.getKey();
                    if (n.endsWith("/") || n.startsWith("... (")) continue;
                    if (me.getValue()[0] != 0) continue;
                    if (dirPrefixes.contains(n + "/")) toUpgrade.add(n);
                }
                for (String n : toUpgrade) {
                    m.remove(n);
                    m.put(n + "/", new long[]{0, 0});
                }
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
        try { tmp.toFile().deleteOnExit(); } catch (Exception ignored) {
            // deleteOnExit 注册失败可忽略：临时文件仍正常使用，残留由系统清理兜底
        }
        return tmp;
    }

    /** S1 修复：安全删除临时文件（失败静默，不干扰主流程）。 */
    static void deleteTemp(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {
            // 删除失败静默：不留垃圾文件的目标是尽力而为，失败不干扰主流程
        }
    }
}
