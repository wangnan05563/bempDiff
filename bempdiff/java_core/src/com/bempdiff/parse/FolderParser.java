package com.bempdiff.parse;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 文件夹解析引擎（FR：新增的文件夹比较功能）。
 * 与 PackageParser 同为"包/目录 → PackageSnapshot"的适配器，因此可直接复用
 * DiffEngine 的差异算法（key 集合对称差 + sha256 比对），无需改动差异层。
 *
 * 设计要点：
 *  ① 仅把"文件"作为条目，目录不单独入表——目录结构由文件相对路径自然派生，
 *     重命名/移动目录会表现为子文件的 删除+新增，结构变化依然可见（与 UI DiffTree 建树一致）；
 *  ② key = 相对文件夹根的路径，'\\' 归一为 '/'（如 {@code src/com/x/A.java}）；
 *  ③ 分类复用 PackageParser.classify（按扩展名判定 CLASS/JAR/CONFIG/JS/HTML/CSS/STATIC/OTHER）；
 *  ④ 跳过符号链接（避免符号链接环导致无限遍历），不可读文件隔离跳过（不影响其余比对）；
 *  ⑤ 流式计算 sha256（边读边 digest），不驻留整文件字节，规避大文件 OOM。
 */
public final class FolderParser {

    private static final Logger LOG = Logger.getLogger(FolderParser.class.getName());

    /** 文件夹比较场景下，文本类文件内容 diff 的大小上限（字节）。超过则仅做结构级差异识别。 */
    private static final long TEXT_DIFF_CAP = 4L * 1024 * 1024;

    /** 单文件读取防御上限（与 PackageParser.HARD_CAP 同源，防超长文件拖垮比对）。 */
    private static final long HARD_CAP = 64L * 1024 * 1024;

    /**
     * 目录条目的哨兵 sha：两侧同名目录固定相等（内容变化由子文件条目体现，目录自身仅按存在性
     * 判定 新增/删除）。非 null 保证 DiffEngine 的 sha.equals 比较安全。
     */
    private static final String DIR_SHA = "dir";

    public PackageSnapshot parse(Path dir, ParseConfig cfg) throws IOException { // NOSONAR - cfg 为统一 parse 接口签名保留（文件夹场景暂无层级配置）
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("不是有效目录（无法解析文件夹）: " + dir);
        }
        setActiveIgnores(cfg.getIgnoreExtensions()); // 比对级忽略扩展名：收集阶段统一启用
        Map<String, LogicalEntry> entries = new LinkedHashMap<>();
        java.util.Set<String> dirs = new java.util.LinkedHashSet<>();
        Path root = dir.toAbsolutePath().normalize();

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                ingestPath(root, p, entries, dirs);
            }
        }
        // 目录条目：仅收录"含文件"的目录（空目录无意义，且避免 DiffEngine 把空目录对称差误判为差异）。
        // key 以 '/' 结尾，FileClass.FOLDER，供差异树右键「文件夹对象」操作（设为基准/删除/重命名等）。
        for (String dk : dirs) {
            if (hasChildEntry(entries, dk)) {
                Path d = root.resolve(dk.replace('/', java.io.File.separatorChar));
                entries.put(dk, new LogicalEntry(dk, Layer.L0, FileClass.FOLDER, 0, DIR_SHA,
                        new EntrySource(d.toString(), null)));
            }
        }
        return new PackageSnapshot(dir, PackageType.FOLDER, null, entries);
    }

    /** 判断是否存在 key 以目录前缀（如 "sub/"）开头的文件条目。 */
    private static boolean hasChildEntry(Map<String, LogicalEntry> entries, String dirKey) {
        for (String k : entries.keySet()) {
            if (k.startsWith(dirKey)) return true;
        }
        return false;
    }

    /** 处理单个扫描到的路径：文件入表、目录登记（空目录不入表）、符号链接/不可读隔离跳过。 */
    private void ingestPath(Path root, Path p, Map<String, LogicalEntry> entries, java.util.Set<String> dirs) {
        if (Files.isSymbolicLink(p)) {
            // 跳过符号链接：避免符号链接环导致无限遍历，且链接目标可能越界
            LOG.log(Level.FINE, "[隔离] 跳过符号链接: {0}", root.relativize(p));
            return;
        }
        if (Files.isDirectory(p)) {
            if (!root.equals(p.toAbsolutePath().normalize())) {
                try {
                    String dk = sanitizeKey(root, p) + "/";
                    dirs.add(dk);
                } catch (IOException bad) {
                    LOG.log(Level.WARNING, "[隔离] 跳过可疑目录路径（已拒绝，不影响其余比对）: {0}", bad.getMessage());
                }
            }
            return;
        }
        if (!Files.isRegularFile(p)) {
            return;
        }
        String key;
        try {
            key = sanitizeKey(root, p);
        } catch (IOException bad) {
            LOG.log(Level.WARNING, "[隔离] 跳过可疑路径（已拒绝，不影响其余比对）: {0}", bad.getMessage());
            return;
        }
        try {
            addFile(p, key, entries);
        } catch (IOException bad) {
            LOG.log(Level.WARNING, "[隔离] 跳过不可读文件（已拒绝，不影响其余比对）: {0}（{1}）",
                    new Object[]{key, bad.getMessage()});
        }
    }

    // 比对级忽略扩展名：由 parse(..) 时暂存，null 表示未启用
    private java.util.List<String> activeIgnores = null;
    private void setActiveIgnores(java.util.List<String> v) { this.activeIgnores = v; }
    private boolean ignoredKey(String key) {
        return com.bempdiff.config.ParseConfig.ignoredExt(key, activeIgnores);
    }

    private void addFile(Path file, String key, Map<String, LogicalEntry> out) throws IOException {
        if (ignoredKey(key)) return; // 比对级忽略扩展名：命中则该文件不入表，不参与差异比对
        long size = Files.size(file);
        if (size > HARD_CAP) {
            // 超长文件：仅记元数据（不计算 sha，避免单次比对耗时过长），标记为 OTHER 兜底
            out.put(key, new LogicalEntry(key, Layer.L0, FileClass.OTHER,
                    size, "", new EntrySource(file.toString(), null)));
            return;
        }
        String h = PackageParser.sha256(file);
        FileClass fc = PackageParser.classify(key);
        out.put(key, new LogicalEntry(key, Layer.L0, fc,
                size, h, new EntrySource(file.toString(), null)));
    }

    /** 计算相对根目录的归一化 key，并拒绝越界（Zip-Slip 同类防护：拒绝 ".." 与绝对路径）。 */
    private static String sanitizeKey(Path root, Path file) throws IOException {
        Path rel = root.relativize(file.toAbsolutePath().normalize());
        String n = rel.toString().replace('\\', '/');
        if (n.isEmpty()) throw new IOException("非法条目名（空）");
        if (n.startsWith("/")) throw new IOException("拒绝绝对路径条目: " + n);
        if (n.contains("..")) throw new IOException("拒绝路径穿越条目: " + n);
        return n;
    }

    /** 判断某条目是否适合做文本级内容 diff（文件夹模式双击查看时使用）。 */
    public static boolean isTextDiffable(LogicalEntry e) {
        if (e == null) return false;
        if (e.getSize() > TEXT_DIFF_CAP) return false;
        FileClass fc = e.getFileClass();
        // 二进制/字节码类文件仅做结构级差异识别，不做内容 diff
        return fc != FileClass.CLASS && fc != FileClass.JAR && fc != FileClass.STATIC;
    }

    /** 读取文件内容为字符串（容错解码：utf-8 → gbk → latin-1 兜底，与 Decompiler.decode 同源）。 */
    public static String readText(Path file) throws IOException {
        byte[] bs = Files.readAllBytes(file);
        for (String enc : new String[]{"UTF-8", "gbk", "latin-1"}) {
            try {
                return new String(bs, enc);
            } catch (Exception ignored) {
                // 尝试下一种编码
            }
        }
        return new String(bs);
    }

    /** 从 EntrySource 还原真实文件路径（文件夹模式条目 outerEntry 即绝对路径）。 */
    public static Path realPath(LogicalEntry e) {
        if (e == null || e.getSrc() == null || e.getSrc().getOuterEntry() == null) return null;
        return java.nio.file.Paths.get(e.getSrc().getOuterEntry());
    }
}
