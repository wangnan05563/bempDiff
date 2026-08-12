package com.bempdiff.parse;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public PackageSnapshot parse(Path dir, ParseConfig cfg) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("不是有效目录（无法解析文件夹）: " + dir);
        }
        Map<String, LogicalEntry> entries = new LinkedHashMap<>();
        Path root = dir.toAbsolutePath().normalize();

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isSymbolicLink(p)) {
                    // 跳过符号链接：避免符号链接环导致无限遍历，且链接目标可能越界
                    LOG.fine("[隔离] 跳过符号链接: " + root.relativize(p));
                    continue;
                }
                if (Files.isDirectory(p)) {
                    continue; // 目录不入表，结构由文件路径派生
                }
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String key;
                try {
                    key = sanitizeKey(root, p);
                } catch (IOException bad) {
                    LOG.warning("[隔离] 跳过可疑路径（已拒绝，不影响其余比对）: " + bad.getMessage());
                    continue;
                }
                try {
                    addFile(p, key, entries);
                } catch (IOException bad) {
                    LOG.warning("[隔离] 跳过不可读文件（已拒绝，不影响其余比对）: " + key
                            + " (" + bad.getMessage() + ")");
                }
            }
        }
        return new PackageSnapshot(dir, PackageType.FOLDER, null, entries);
    }

    private void addFile(Path file, String key, Map<String, LogicalEntry> out) throws IOException {
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
        if (fc == FileClass.CLASS || fc == FileClass.JAR || fc == FileClass.STATIC) return false;
        return true;
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
