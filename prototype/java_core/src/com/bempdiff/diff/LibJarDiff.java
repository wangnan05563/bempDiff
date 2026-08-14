package com.bempdiff.diff;

import com.bempdiff.decompile.Decompiler;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 差异依赖 JAR 内部源码对比引擎（Req 6）。
 *
 * 需求：自动识别并提取 WAR 包中存在差异的 JAR 包，对其内部包含的 class 文件进行
 * 自动反编译，并逐一对反编译后的源码内容进行比对，输出差异结果的清晰对比报告。
 *
 * 设计要点：
 *  - 识别：DiffResult 中 status ∈ {ADDED, MODIFIED, DELETED} 且路径以 ".jar" 结尾、
 *    且位于 "/lib/" 下的条目（即第三方依赖 JAR，层级 L2）。内部业务 lib（展开为 L1 的）
 *    其 class 已走 collectL1ClassCandidates 路径，不在此重复处理。
 *  - 提取：复用 PackageParser.readEntryBytes 取出整个 lib jar 字节（lib jar 条目本身
 *    是 war 内的顶层条目，src 非嵌套），落临时文件后按嵌套 zip 打开枚举 .class。
 *  - 比对：逐 class 以 sha256 判定 ADDED/REMOVED/MODIFIED/UNCHANGED（MODIFIED jar 才有
 *    三方比对；ADDED/DELETED jar 整体新增/移除）。
 *  - 反编译：仅对 Top-K（按 MODIFIED→ADDED→DELETED 优先级）做 CFR 反编译与源码级 diff，
 *    其余 class 仅在报告中计数，避免对含数千 class 的超大依赖做无意义全量反编译。
 *  - 懒加载：readClassBytes 供 GUI 在用户点击某个未预反编译的 class 时按需反编译。
 */
public final class LibJarDiff {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(LibJarDiff.class.getName());
    private static final long HARD_CAP = 64L * 1024 * 1024;

    private LibJarDiff() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ----------------------------- 结果模型 -----------------------------

    /** 单个内部 class 的对比单元：状态 + （可能为 null 的）反编译源码 diff。 */
    public static final class LibClassUnit {
        public final String innerClass;       // 如 com/foo/Bar.class
        public final DiffStatus status;       // ADDED / MODIFIED / DELETED / UNCHANGED
        public final DecompiledUnit unit;     // Top-K 内已反编译则非空；其余为 null（待懒加载）
        public LibClassUnit(String innerClass, DiffStatus status, DecompiledUnit unit) {
            this.innerClass = innerClass;
            this.status = status;
            this.unit = unit;
        }
    }

    /** 单个差异 JAR 的对比结论。 */
    public static final class DiffJarInfo {
        public final String jarKey;
        public final DiffStatus jarStatus;    // 该 JAR 自身在 war 中的状态
        public final int added, removed, modified, unchanged;
        public final List<LibClassUnit> classes; // 全量（含未反编译的，unit 为 null）
        /** 该 JAR 是否因读取/枚举失败而被跳过（失败隔离，避免拖垮整份报告）。 */
        public final boolean failed;
        public final String error;            // 失败原因（failed 时非空）
        public DiffJarInfo(String jarKey, DiffStatus jarStatus,
                           int added, int removed, int modified, int unchanged,
                           List<LibClassUnit> classes) {
            this(jarKey, jarStatus, added, removed, modified, unchanged, classes, false, null);
        }
        public DiffJarInfo(String jarKey, DiffStatus jarStatus,
                           int added, int removed, int modified, int unchanged,
                           List<LibClassUnit> classes, boolean failed, String error) {
            this.jarKey = jarKey;
            this.jarStatus = jarStatus;
            this.added = added;
            this.removed = removed;
            this.modified = modified;
            this.unchanged = unchanged;
            this.classes = classes;
            this.failed = failed;
            this.error = error;
        }
        /** 已反编译（unit 非空）的 class 数，用于全局 Top-K 预算扣减。 */
        public int getDecompiledCount() {
            int c = 0;
            for (LibClassUnit u : classes) if (u.unit != null) c++;
            return c;
        }
    }

    /** 全部差异 JAR 的对比结论 + 汇总计数。 */
    public static final class Result {
        public final List<DiffJarInfo> jars;
        public final int totalJars, totalAdded, totalRemoved, totalModified, totalUnchanged;
        public Result(List<DiffJarInfo> jars, int totalJars,
                      int totalAdded, int totalRemoved, int totalModified, int totalUnchanged) {
            this.jars = jars;
            this.totalJars = totalJars;
            this.totalAdded = totalAdded;
            this.totalRemoved = totalRemoved;
            this.totalModified = totalModified;
            this.totalUnchanged = totalUnchanged;
        }
        public boolean isEmpty() { return jars.isEmpty(); }
    }

    // ----------------------------- 入口 -----------------------------

    /** 识别差异依赖 JAR：DiffResult 中 ".jar" 且位于 "/lib/" 下的 ADDED/MODIFIED/DELETED 条目。 */
    public static List<String> identifyDiffJars(DiffResult r) {
        List<String> out = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                if (k.endsWith(".jar") && k.contains("/lib/")) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** 全量分析：遍历所有差异 lib jar，返回 Result。
     *  Top-K 为【全局预算】：跨 jar 递减，优先覆盖排序靠前的 jar（identifyDiffJars 已按
     *  MODIFIED→ADDED→DELETED 稳定排序）；class 计数始终全量准确，仅反编译受全局限额。 */
    public static Result analyze(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                 DiffResult r, Decompiler dec, int topK) {
        List<String> jarKeys = identifyDiffJars(r);
        List<DiffJarInfo> jars = new ArrayList<>();
        int ta = 0, tr = 0, tm = 0, tu = 0;
        int budget = Math.max(topK, 0);
        for (String jk : jarKeys) {
            DiffJarInfo info = analyzeJar(oldSnap, newSnap, jk, dec, budget);
            jars.add(info);
            ta += info.added;
            tr += info.removed;
            tm += info.modified;
            tu += info.unchanged;
            budget -= info.getDecompiledCount(); // 失败 jar 计 0，不影响预算
        }
        return new Result(jars, jars.size(), ta, tr, tm, tu);
    }

    /** 单 JAR 分析：提取、枚举 class、判定逐 class 状态、Top-K 反编译源码 diff。
     *  读取/枚举阶段的异常被隔离：单 jar 失败时返回 failed 标记的 DiffJarInfo，不影响其余 jar 与整份报告。 */
    public static DiffJarInfo analyzeJar(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                         String jarKey, Decompiler dec, int topK) {
        DiffStatus jarStatus = DiffStatus.UNCHANGED;
        byte[] oldJar, newJar;
        Map<String, String> oldSha, newSha;
        try {
            jarStatus = jarStatusOf(oldSnap, newSnap, jarKey);
            oldJar = readJarBytes(oldSnap, jarKey);
            newJar = readJarBytes(newSnap, jarKey);
            oldSha = classShaMap(oldJar);
            newSha = classShaMap(newJar);
        } catch (RuntimeException ex) {
            LOG.warning("[LibJarDiff] 分析 JAR 失败，已跳过: " + jarKey + " (" + ex.getMessage() + ")");
            // 保留 war 级 jarStatus（如 MODIFIED），使概览仍将其计入差异 JAR；章节内标注 [分析失败]
            return new DiffJarInfo(jarKey, jarStatus, 0, 0, 0, 0,
                    java.util.Collections.emptyList(), true, ex.getMessage());
        }

        // 逐 class 状态（TreeMap 保证稳定顺序）
        TreeMap<String, DiffStatus> classStatus = new TreeMap<>();
        TreeSet<String> all = new TreeSet<>();
        all.addAll(oldSha.keySet());
        all.addAll(newSha.keySet());
        for (String inner : all) {
            boolean inOld = oldSha.containsKey(inner);
            boolean inNew = newSha.containsKey(inner);
            if (inOld && inNew) {
                classStatus.put(inner, oldSha.get(inner).equals(newSha.get(inner))
                        ? DiffStatus.UNCHANGED : DiffStatus.MODIFIED);
            } else if (inOld) {
                classStatus.put(inner, DiffStatus.DELETED);
            } else {
                classStatus.put(inner, DiffStatus.ADDED);
            }
        }

        int added = 0, removed = 0, modified = 0, unchanged = 0;
        for (DiffStatus st : classStatus.values()) {
            switch (st) {
                case ADDED: added++; break;
                case DELETED: removed++; break;
                case MODIFIED: modified++; break;
                default: unchanged++;
            }
        }

        // Top-K 反编译（优先级 MODIFIED → ADDED → DELETED；UNCHANGED 跳过）
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        for (DiffStatus want : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String inner : classStatus.keySet()) {
                if (decompiled.size() >= topK) break;
                if (classStatus.get(inner) != want) continue;
                boolean needOld = (want == DiffStatus.MODIFIED || want == DiffStatus.DELETED);
                boolean needNew = (want == DiffStatus.MODIFIED || want == DiffStatus.ADDED);
                byte[] ob = needOld ? readClassBytesFrom(oldJar, inner) : null;
                byte[] nb = needNew ? readClassBytesFrom(newJar, inner) : null;
                decompiled.put(inner, dec.decompileBytes(ob, nb, inner));
            }
        }

        // 组装全量单元（保留排序），已反编译的带 unit，其余 unit=null
        List<LibClassUnit> units = new ArrayList<>(classStatus.size());
        for (String inner : classStatus.keySet()) {
            units.add(new LibClassUnit(inner, classStatus.get(inner), decompiled.get(inner)));
        }
        return new DiffJarInfo(jarKey, jarStatus, added, removed, modified, unchanged, units, false, null);
    }

    /** 懒加载：取某 lib jar 内单个 class 的字节（供 GUI 点击未预反编译的 class 时调用）。 */
    public static byte[] readClassBytes(PackageSnapshot snap, String jarKey, String innerClass) {
        byte[] jar = readJarBytes(snap, jarKey);
        return readClassBytesFrom(jar, innerClass);
    }

    // ----------------------------- 内部工具 -----------------------------

    private static DiffStatus jarStatusOf(PackageSnapshot oldSnap, PackageSnapshot newSnap, String jarKey) {
        LogicalEntry oe = oldSnap != null ? oldSnap.getEntries().get(jarKey) : null;
        LogicalEntry ne = newSnap != null ? newSnap.getEntries().get(jarKey) : null;
        if (oe == null && ne == null) return DiffStatus.UNCHANGED;
        if (oe == null) return DiffStatus.ADDED;
        if (ne == null) return DiffStatus.DELETED;
        return oe.getSha256().equals(ne.getSha256()) ? DiffStatus.UNCHANGED : DiffStatus.MODIFIED;
    }

    private static byte[] readJarBytes(PackageSnapshot snap, String jarKey) {
        if (snap == null) return null;
        LogicalEntry e = snap.getEntries().get(jarKey);
        if (e == null) return null;
        try {
            return new PackageParser().readEntryBytes(snap, e);
        } catch (IOException ex) {
            throw new RuntimeException("读取 lib jar 失败: " + jarKey, ex);
        }
    }

    /** 枚举 jar 内所有 .class 的 sha256（不含目录）。jar 为 null 时返回空表。
     *  枚举失败（如损坏/非 zip 的 lib jar）抛 RuntimeException，由 analyzeJar 统一隔离为 failed 标记。 */
    private static Map<String, String> classShaMap(byte[] jar) {
        Map<String, String> m = new LinkedHashMap<>();
        if (jar == null) return m;
        File tmp = null;
        try {
            tmp = writeTempJar(jar);
            try (ZipFile zf = new ZipFile(tmp)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String n = sanitize(e.getName());
                    if (!n.endsWith(".class")) continue;
                    m.put(n, sha256(readAll(zf, e)));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("枚举 lib jar 内部 class 失败: " + ex.getMessage(), ex);
        } finally {
            deleteTemp(tmp);
        }
        return m;
    }

    /** 从 jar 字节读取单个 class 的字节；jar 或条目不存在返回 null。 */
    private static byte[] readClassBytesFrom(byte[] jar, String inner) {
        if (jar == null) return null;
        File tmp = null;
        try {
            tmp = writeTempJar(jar);
            try (ZipFile zf = new ZipFile(tmp)) {
                ZipEntry e = zf.getEntry(inner);
                if (e == null) return null;
                return readAll(zf, e);
            }
        } catch (IOException ex) {
            LOG.warning("[LibJarDiff] 读取 lib jar 内部 class 失败: " + inner + " (" + ex.getMessage() + ")");
            return null;
        } finally {
            deleteTemp(tmp);
        }
    }

    private static File writeTempJar(byte[] jar) throws IOException {
        // 注：临时 jar 在 finally 中已显式删除，无需 deleteOnExit（避免长生命周期 GUI 累积路径引用）。
        File f = File.createTempFile("bempdiff-libjar-", ".jar");
        Files.write(f.toPath(), jar);
        return f;
    }

    private static void deleteTemp(File f) {
        if (f == null) return;
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            LOG.warning("[LibJarDiff] 临时 jar 清理失败: " + f.getAbsolutePath());
        }
    }

    private static String sanitize(String raw) {
        return raw == null ? "" : raw.replace('\\', '/');
    }

    private static byte[] readAll(ZipFile zf, ZipEntry e) throws IOException {
        long declared = e.getSize();
        if (declared > HARD_CAP) {
            throw new IOException("条目声明过大（疑似 zip bomb）: " + e.getName() + " (" + declared + " B)");
        }
        try (InputStream in = zf.getInputStream(e)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                    declared > 0 && declared < HARD_CAP ? (int) declared : 8192);
            byte[] buf = new byte[8192];
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > HARD_CAP) {
                    throw new IOException("条目读取超限（疑似 zip bomb）: " + e.getName());
                }
                bos.write(buf, 0, r);
            }
            return bos.toByteArray();
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
