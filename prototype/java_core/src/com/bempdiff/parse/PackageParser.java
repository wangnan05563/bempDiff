package com.bempdiff.parse;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 包解析引擎（T04/T05/T06，FR1/FR2）。对应 prototype: diff_engine.py
 *  - detect_package_type / path_mapping / classify / extract_version / build_logical_entries
 *
 * 设计要点（与详细设计 §5.1 一致）：
 *  ① 命名空间避免同名类冲突：L1 内嵌 class 的 key = "WEB-INF/lib/<jar>/<inner>"；
 *  ② 内部 lib 展开为 L1，第三方 lib 仅 jar 级记为 L2（默认折叠）；
 *  ③ 普通 jar 在 expandAll 时所有 class 视为 L1；
 *  ④ 流式：仅缓存 sha256+size+src，不缓存整条目字节（大 war 防 OOM）。
 */
public final class PackageParser {

    private static final Logger LOG = Logger.getLogger(PackageParser.class.getName());
    private static final String CLASS_EXT = ".class";

    /** 硬上限：单条目字节上限，防御 zip bomb（声明 size 与实际读取均受此约束）。 */
    private static final long HARD_CAP = 64L * 1024 * 1024;

    /** 承载 detectPrefixes 返回的类路径与前缀信息。 */
    private static final class Prefixes {
        final String classesPrefix;
        final String libPrefix;
        Prefixes(String classesPrefix, String libPrefix) {
            this.classesPrefix = classesPrefix;
            this.libPrefix = libPrefix;
        }
    }

    public PackageType detectType(Path file) throws IOException {
        try (ZipFile zf = new ZipFile(file.toFile())) {
            boolean hasBootClasses = false;
            boolean hasBootLib = false;
            boolean hasWebClasses = false;
            boolean hasWebLib = false;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("BOOT-INF/classes/")) hasBootClasses = true;
                if (n.startsWith("BOOT-INF/lib/")) hasBootLib = true;
                if (n.startsWith("WEB-INF/classes/")) hasWebClasses = true;
                if (n.startsWith("WEB-INF/lib/")) hasWebLib = true;
            }
            if (hasBootClasses || hasBootLib) return PackageType.FAT_JAR;
            if (hasWebClasses || hasWebLib) return PackageType.WAR;
            return PackageType.JAR;
        }
    }

    private Prefixes detectPrefixes(PackageType type) {
        switch (type) {
            case WAR:     return new Prefixes("WEB-INF/classes/", "WEB-INF/lib/");
            case FAT_JAR: return new Prefixes("BOOT-INF/classes/", "BOOT-INF/lib/");
            default:      return new Prefixes(null, null);
        }
    }

    private void processLibEntries(ZipFile zf, String libPrefix, ParseConfig cfg,
                                   Map<String, LogicalEntry> entries) throws IOException {
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (!e.isDirectory() && n.startsWith(libPrefix) && n.endsWith(".jar")) {
                try {
                    handleLibJar(zf, n, e, cfg, entries);
                } catch (IOException bad) {
                    // 隔离：单个可疑 lib 条目被拒（如路径穿越），跳过其余照常比对
                    LOG.warning("[隔离] 跳过可疑 lib 条目（已拒绝，不影响其余比对）: " + bad.getMessage());
                }
            }
        }
    }

    private void processNonLibEntries(ZipFile zf, String libPrefix, String classesPrefix,
                                      PackageType type, boolean expandAll, ParseConfig cfg,
                                      Map<String, LogicalEntry> entries) throws IOException {
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (!isValidZipEntry(e, libPrefix)) {
                continue;
            }
            try {
                addFromZip(zf, e, n, layerForEntry(n, classesPrefix, type, expandAll, cfg), entries);
            } catch (IOException bad) {
                LOG.warning("[隔离] 跳过可疑条目（已拒绝，不影响其余比对）: " + bad.getMessage());
            }
        }
    }

    /** 校验 zip entry 是否应被处理（跳过目录和已在 processLibEntries 中处理的 lib jar）。 */
    private static boolean isValidZipEntry(ZipEntry e, String libPrefix) {
        String n = e.getName();
        return !e.isDirectory() && !(libPrefix != null && n.startsWith(libPrefix) && n.endsWith(".jar"));
    }

    /** 根据条目名和配置确定 Layer 层级，不依赖 ZipFile/ZipEntry/entries。 */
    private static Layer layerForEntry(String n, String classesPrefix,
                                        PackageType type, boolean expandAll, ParseConfig cfg) {
        boolean isJarClass = type == PackageType.JAR && n.endsWith(CLASS_EXT);
        boolean isPrefixed = classesPrefix != null && n.startsWith(classesPrefix);
        if (isJarClass) {
            // 普通 jar：命中内部前缀、或 CLI --expand-all、或配置 expandAllForPlainJar 均视为 L1 展开
            return (expandAll || cfg.isExpandAllForPlainJar() || startsWithAny(n, cfg.getInternalPrefixes()))
                    ? Layer.L1 : Layer.L0;
        } else if (isPrefixed) {
            return Layer.L1;
        }
        return Layer.L0;
    }

    public PackageSnapshot parse(Path file, ParseConfig cfg, boolean expandAll) throws IOException {
        PackageType type = detectType(file);
        try (ZipFile zf = new ZipFile(file.toFile())) {
            Map<String, LogicalEntry> entries = new LinkedHashMap<>();
            Prefixes prefixes = detectPrefixes(type);

            // 第一遍：lib/*.jar 单独处理（内嵌 jar 需先判定是否内部业务码）
            if (prefixes.libPrefix != null) {
                processLibEntries(zf, prefixes.libPrefix, cfg, entries);
            }

            // 第二遍：非 lib 条目
            processNonLibEntries(zf, prefixes.libPrefix, prefixes.classesPrefix,
                    type, expandAll, cfg, entries);

            String version = extractVersion(zf);
            return new PackageSnapshot(file, type, version, entries);
        }
    }

    private void handleLibJar(ZipFile zf, String jarPath, ZipEntry jarEntry,
                              ParseConfig cfg, Map<String, LogicalEntry> out) throws IOException {
        jarPath = sanitizeKey(jarPath);
        if (jarEntry.getSize() > cfg.getMaxEntryBytes()) {
            // 超大 lib：仅记为 L2 单条目
            addFromZip(zf, jarEntry, jarPath, Layer.L2, out);
            return;
        }
        // P1-2：流式把嵌套 jar 落到临时文件（不经整 byte[] 缓冲），直接打开扫描/展开。
        File tmpJar = copyEntryToTemp(zf, jarEntry);
        boolean isInternal = false;
        boolean corrupted = false;
        try (ZipFile jz = new ZipFile(tmpJar)) {
            isInternal = anyClassStartsWith(jz, cfg.getInternalPrefixes());
        } catch (IOException badJar) {
            corrupted = true;
        }
        if (corrupted) {
            // 损坏/非 zip：仅记为 L2（对临时文件流式算 hash，不驻留 byte[]）
            long len = tmpJar.length();
            String h = sha256File(tmpJar);
            out.put(jarPath, new LogicalEntry(jarPath, Layer.L2, FileClass.JAR,
                    len, h, new EntrySource(jarPath, null)));
            deleteTempFile(tmpJar);
            return;
        }
        if (isInternal && cfg.isExpandInternalLib()) {
            try (ZipFile jz = new ZipFile(tmpJar)) {
                Enumeration<? extends ZipEntry> jen = jz.entries();
                while (jen.hasMoreElements()) {
                    ZipEntry je = jen.nextElement();
                    if (!je.isDirectory()) {
                        String inner = sanitizeKey(je.getName());
                        String key = jarPath + "/" + inner;
                        // P1-2：流式算内部类 hash，避免整条目 byte[] 驻留
                        String h = sha256Stream(jz, je);
                        out.put(key, new LogicalEntry(key, Layer.L1, classify(inner),
                                je.getSize(), h, new EntrySource(jarPath, inner)));
                    }
                }
            } finally {
                deleteTempFile(tmpJar);
            }
        } else {
            // 第三方依赖（或配置为不展开）：仅 jar 级 L2（对临时文件流式算 hash）
            long len = tmpJar.length();
            String h = sha256File(tmpJar);
            out.put(jarPath, new LogicalEntry(jarPath, Layer.L2, FileClass.JAR,
                    len, h, new EntrySource(jarPath, null)));
            deleteTempFile(tmpJar);
        }
    }

    /** P1-2：把 zip 条目流式落到临时文件（不驻留整 byte[]），供嵌套 jar 打开扫描。 */
    private File copyEntryToTemp(ZipFile zf, ZipEntry e) throws IOException {
        long declared = e.getSize();
        if (declared > HARD_CAP) {
            throw new IOException("条目声明过大（疑似 zip bomb）: " + e.getName() + " (" + declared + " B)");
        }
        File f = File.createTempFile("bempdiff-nested-", ".jar");
        f.deleteOnExit();
        try (InputStream in = zf.getInputStream(e);
             FileOutputStream fos = new FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > HARD_CAP) {
                    throw new IOException("条目读取超限（疑似 zip bomb）: " + e.getName());
                }
                fos.write(buf, 0, r);
            }
        }
        return f;
    }

    /** P1-2：对临时文件流式计算 sha256（替代读入 byte[] 再算，降低内存峰值）。 */
    private static String sha256File(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return sha256Stream(in);
        }
    }

    /** 返回临时 jar 文件（用于嵌套 zip 打开）。量产版可改用内存映射，原型/移植用临时文件。 */
    private File createTempJar(byte[] data) throws IOException {
        File f = File.createTempFile("bempdiff-nested-", ".jar");
        f.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
        return f;
    }

    private void deleteTempFile(File f) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            LOG.warning("临时文件清理失败: " + f.getAbsolutePath());
        }
    }

    private boolean anyClassStartsWith(ZipFile jz, List<String> prefixes) {
        Enumeration<? extends ZipEntry> en = jz.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.endsWith(CLASS_EXT) && startsWithAny(n, prefixes)) return true;
        }
        return false;
    }

    private void addFromZip(ZipFile zf, ZipEntry e, String key, Layer layer,
                            Map<String, LogicalEntry> out) throws IOException {
        key = sanitizeKey(key);
        // P1-2：流式计算 sha256（边读边 digest），不把整条目 byte[] 驻留内存，降低大包解析内存峰值。
        long size = e.getSize();
        String h = sha256Stream(zf, e);
        out.put(key, new LogicalEntry(key, layer, classify(key), size, h, new EntrySource(key, null)));
    }

    /** 从 EntrySource 取回 class 字节（prototype: _extract_bytes）。 */
    public byte[] readEntryBytes(PackageSnapshot snap, LogicalEntry entry) throws IOException {
        EntrySource src = entry.getSrc();
        Path file = snap.getFile();
        try (ZipFile zf = new ZipFile(file.toFile())) {
            if (!src.isNested()) {
                ZipEntry ze = zf.getEntry(src.getOuterEntry());
                if (ze == null) throw new IOException("missing entry: " + src.getOuterEntry());
                return readAll(zf, ze);
            } else {
                ZipEntry libEntry = zf.getEntry(src.getOuterEntry());
                if (libEntry == null) throw new IOException("missing lib: " + src.getOuterEntry());
                byte[] libBytes = readAll(zf, libEntry);
                File tmpJar = createTempJar(libBytes);
                try (ZipFile jz = new ZipFile(tmpJar)) {
                    ZipEntry inner = jz.getEntry(src.getInnerEntry());
                    if (inner == null) throw new IOException("missing inner: " + src.getInnerEntry());
                    return readAll(jz, inner);
                } finally {
                    deleteTempFile(tmpJar);
                }
            }
        }
    }

    public String extractVersion(PackageSnapshot snap) {
        try (ZipFile zf = new ZipFile(snap.getFile().toFile())) {
            return extractVersion(zf);
        } catch (IOException e) {
            LOG.fine("无法读取包文件提取版本: " + e.getMessage());
            return null;
        }
    }

    private String extractVersion(ZipFile zf) {
        String version = extractVersionFromManifest(zf);
        if (version != null) return version;
        return extractVersionFromPom(zf);
    }

    private String extractVersionFromManifest(ZipFile zf) {
        try {
            ZipEntry mf = zf.getEntry("META-INF/MANIFEST.MF");
            if (mf == null) {
                return null;
            }
            String txt = new String(readAll(zf, mf), StandardCharsets.UTF_8);
            for (String line : txt.split("\n")) {
                if (line.startsWith("Implementation-Version:")) {
                    return line.split(":", 2)[1].trim();
                }
            }
        } catch (IOException e) {
            LOG.fine("无法读取 MANIFEST.MF: " + e.getMessage());
        }
        return null;
    }

    private String extractVersionFromPom(ZipFile zf) {
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            String n = en.nextElement().getName();
            if (n.startsWith("META-INF/maven/") && n.endsWith("pom.properties")) {
                try {
                    String txt = new String(readAll(zf, zf.getEntry(n)), StandardCharsets.UTF_8);
                    for (String line : txt.split("\n")) {
                        if (line.startsWith("version=")) {
                            return line.split("=", 2)[1].trim();
                        }
                    }
                } catch (IOException e) {
                    LOG.fine("无法读取 pom.properties: " + e.getMessage());
                }
            }
        }
        return null;
    }

    // ----------------------------- 工具 -----------------------------

    public static FileClass classify(String name) {
        if (name.endsWith(CLASS_EXT)) return FileClass.CLASS;
        if (name.endsWith(".jar")) return FileClass.JAR;
        if (name.endsWith(".xml") || name.endsWith(".properties") || name.endsWith(".yml")
                || name.endsWith(".yaml") || name.endsWith(".json") || name.endsWith(".conf")
                || name.endsWith(".cfg")) return FileClass.CONFIG;
        if (name.endsWith(".jsp") || name.endsWith(".html") || name.endsWith(".js")
                || name.endsWith(".css") || name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".woff")
                || name.endsWith(".woff2") || name.endsWith(".ttf") || name.endsWith(".eot"))
            return FileClass.STATIC;
        return FileClass.OTHER;
    }

    private static boolean startsWithAny(String s, List<String> prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }

    private static byte[] readAll(ZipFile zf, ZipEntry e) throws IOException {
        long declared = e.getSize();
        if (declared > HARD_CAP) {
            throw new IOException("条目声明过大（疑似 zip bomb）: " + e.getName() + " (" + declared + " B)");
        }
        try (InputStream in = zf.getInputStream(e)) {
            int hint = (declared > 0 && declared < HARD_CAP) ? (int) declared : 8192;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(hint);
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

    /** 条目名隔离：归一化分隔符，拒绝绝对路径与 ".." 穿越段（防御 Zip Slip）。返回归一化 key。 */
    private static String sanitizeKey(String raw) throws IOException {
        if (raw == null || raw.isEmpty()) throw new IOException("非法条目名（空）: " + raw);
        String n = raw.replace('\\', '/');
        if (n.startsWith("/")) throw new IOException("拒绝绝对路径条目: " + raw);
        if (n.indexOf(':') >= 0) throw new IOException("拒绝含盘符的条目（疑似绝对路径）: " + raw);
        if (n.contains("..")) throw new IOException("拒绝路径穿越条目（Zip Slip）: " + raw);
        return n;
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

    /**
     * P1-2：流式计算 zip 条目 sha256（边读边 digest），不把整条目 byte[] 驻留内存。
     * 含 zip-bomb 防御（声明 size 与累计读取双约束）。
     */
    private static String sha256Stream(ZipFile zf, ZipEntry e) throws IOException {
        long declared = e.getSize();
        if (declared > HARD_CAP) {
            throw new IOException("条目声明过大（疑似 zip bomb）: " + e.getName() + " (" + declared + " B)");
        }
        try (InputStream in = zf.getInputStream(e)) {
            return sha256Stream(in, declared);
        }
    }

    /** P1-2：通用流式 digest（zip 条目或临时文件）。declared<0 表示不预检 size。 */
    private static String sha256Stream(InputStream in) throws IOException {
        return sha256Stream(in, -1);
    }

    private static String sha256Stream(InputStream in, long declared) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        byte[] buf = new byte[8192];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if ((declared > 0 && declared <= HARD_CAP && total > declared + 1_048_576L)
                    || (declared <= 0 && total > HARD_CAP)) {
                // 声明异常或累计超限（zip bomb）：中止
                throw new IOException("条目读取超限（疑似 zip bomb）");
            }
            md.update(buf, 0, r);
        }
        byte[] h = md.digest();
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}