package com.bempdiff.diff;

import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 文件夹对比引擎（独立实现，不依赖 PackageSnapshot/DiffEngine）。
 *
 * <p>与包比对不同，文件夹比对需要精确区分「内容不同」与「仅属性（大小/修改时间）不同」，
 * 因此这里自建轻量模型 {@link FolderEntry}/{@link FolderDiffResult}，逐项比较：
 * <ul>
 *   <li>名称（相对路径）、类型（文件/目录）</li>
 *   <li>大小（size）、修改时间（mtime，毫秒）</li>
 *   <li>内容（流式 SHA-256，不驻留整文件字节，规避大文件 OOM）</li>
 * </ul>
 *
 * <p>状态语义（左右分别为 A/B 两个目录）：
 * <ul>
 *   <li>{@code LEFT_ONLY}    —— 仅左侧(A)存在</li>
 *   <li>{@code RIGHT_ONLY}   —— 仅右侧(B)存在</li>
 *   <li>{@code MODIFIED}     —— 两侧均存在，但内容或属性（大小/修改时间/类型）不同</li>
 *   <li>{@code SAME}         —— 两侧完全一致</li>
 *   <li>{@code TYPE_MISMATCH}—— 同路径一侧是文件、另一侧是目录</li>
 * </ul>
 *
 * <p>性能与健壮性：
 * <ul>
 *   <li>并行计算各文件的 size/mtime/hash（固定线程池 + 并行流），遍历本身用 NIO {@code Files.walk}；</li>
 *   <li>跳过符号链接（防环），单个文件读取/哈希异常被隔离记录，不影响其余比对；</li>
 *   <li>超大文件（超过 hardCap）仅记元数据、不哈希；文本文件内容 diff 受 textDiffCap 限制；</li>
 *   <li>支持 {@code --max-depth} 限制遍历深度，应对极深目录。</li>
 * </ul>
 */
public final class FolderDiff {

    private static final Logger LOG = Logger.getLogger(FolderDiff.class.getName());

    /** 单文件读取/哈希防御上限：超过则仅记元数据（不哈希），避免单次比对耗时过长。 */
    private static final long HARD_CAP = 64L * 1024 * 1024;
    /** 文本文件内容 diff 大小上限（字节）。超过则仅做结构级差异识别。 */
    private static final long TEXT_DIFF_CAP = 4L * 1024 * 1024;

    /** 文本类文件扩展名白名单（用于决定是否做行级内容 diff）。 */
    private static final Set<String> TEXT_EXT = Set.of(
            "txt", "md", "markdown", "java", "js", "jsx", "ts", "tsx", "mjs", "cjs",
            "html", "htm", "css", "scss", "less", "xml", "json", "yaml", "yml",
            "properties", "conf", "config", "ini", "cfg", "toml", "csv", "log",
            "py", "rb", "php", "go", "rs", "c", "cpp", "cc", "h", "hpp", "hxx",
            "sh", "bash", "zsh", "bat", "cmd", "ps1", "sql", "gradle", "vue",
            "jsp", "asp", "aspx", "r", "pl", "lua", "scala", "kt", "swift", "dart",
            "tex", "rst", "gitignore", "editorconfig", "lock", "text");

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private FolderDiff() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 比对选项。 */
    public static final class Options {
        /** 最大遍历深度；&lt;=0 表示不限制。 */
        private int maxDepth = -1;
        /** 文本文件内容 diff 大小上限（字节）。 */
        private long textDiffCap = TEXT_DIFF_CAP;
        /** 是否对修改的文本文件计算行级 diff（false 时仅标注 MODIFIED）。 */
        private boolean computeLineDiff = true;
        /** 并行度；&lt;=0 使用 CPU 核心数。 */
        private int parallelism = 0;

        public static Options defaults() {
            return new Options();
        }

        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
        public long getTextDiffCap() { return textDiffCap; }
        public void setTextDiffCap(long textDiffCap) { this.textDiffCap = textDiffCap; }
        public boolean isComputeLineDiff() { return computeLineDiff; }
        public void setComputeLineDiff(boolean computeLineDiff) { this.computeLineDiff = computeLineDiff; }
        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    }

    /** 条目类型。 */
    public enum EntryType { FILE, DIR }

    /** 差异状态。 */
    public enum FolderDiffStatus { LEFT_ONLY, RIGHT_ONLY, MODIFIED, SAME, TYPE_MISMATCH }

    /** 属性/内容变更维度（MODIFIED 项下细分）。 */
    public enum AttrChange { SIZE, MTIME, CONTENT, TYPE }

    /** 单侧条目信息（文件或目录）。 */
    private static final class SideInfo {
        final boolean exists;
        final EntryType type;
        final long size;       // 不存在/目录时为 0
        final long mtime;      // 不存在时为 0
        final String hash;     // 目录或超大文件为 null
        final String absPath;  // 绝对路径（用于 UI diff 视图）

        SideInfo(boolean exists, EntryType type, long size, long mtime, String hash, String absPath) {
            this.exists = exists;
            this.type = type;
            this.size = size;
            this.mtime = mtime;
            this.hash = hash;
            this.absPath = absPath;
        }

        static final SideInfo ABSENT = new SideInfo(false, EntryType.FILE, 0, 0, null, null);
    }

    /** 单条对比结果（文件或目录），含子树（若为目录）。 */
    public static final class FolderEntry {
        public final String relPath;
        public final EntryType type;
        public final Long sizeLeft;
        public final Long sizeRight;
        public final Long mtimeLeft;
        public final Long mtimeRight;
        public final String hashLeft;
        public final String hashRight;
        public final String leftPath;   // 绝对路径；不存在则为 null
        public final String rightPath;
        public final FolderDiffStatus status;
        public final Set<AttrChange> attrChanges;
        public final String lineDiff;   // 文本文件内容 diff（仅 MODIFIED 且内容不同且可文本化时非空）
        public final List<FolderEntry> children;
        public final boolean error;
        public final String errorMsg;

        FolderEntry(String relPath, EntryType type, Long sizeLeft, Long sizeRight, Long mtimeLeft, // NOSONAR(S107) 不可变数据容器，字段只读且无行为，用 builder 属过度设计
                    Long mtimeRight, String hashLeft, String hashRight, String leftPath, String rightPath,
                    FolderDiffStatus status, Set<AttrChange> attrChanges, String lineDiff,
                    List<FolderEntry> children, boolean error, String errorMsg) {
            this.relPath = relPath;
            this.type = type;
            this.sizeLeft = sizeLeft;
            this.sizeRight = sizeRight;
            this.mtimeLeft = mtimeLeft;
            this.mtimeRight = mtimeRight;
            this.hashLeft = hashLeft;
            this.hashRight = hashRight;
            this.leftPath = leftPath;
            this.rightPath = rightPath;
            this.status = status;
            this.attrChanges = attrChanges;
            this.lineDiff = lineDiff;
            this.children = (children != null) ? children : new ArrayList<>();
            this.error = error;
            this.errorMsg = errorMsg;
        }

        /** 是否两侧都存在但内容不同（文本可 diff）。 */
        public boolean isContentModified() {
            return status == FolderDiffStatus.MODIFIED && attrChanges.contains(AttrChange.CONTENT);
        }

        /** 是否仅属性（大小/修改时间/类型）不同、内容相同。 */
        public boolean isAttrOnlyModified() {
            return status == FolderDiffStatus.MODIFIED
                    && !attrChanges.contains(AttrChange.CONTENT)
                    && !attrChanges.isEmpty();
        }
    }

    /** 汇总统计。 */
    public static final class Summary {
        private int leftOnly;
        private int rightOnly;
        private int modified;
        private int same;
        private int typeMismatch;
        private int contentChanged;   // MODIFIED 中内容不同的数量
        private int attrOnlyChanged;  // MODIFIED 中仅属性不同的数量
        private int scannedFiles;
        private int scannedDirs;
        private int errors;

        public int getLeftOnly() { return leftOnly; }
        public void setLeftOnly(int leftOnly) { this.leftOnly = leftOnly; }
        public int getRightOnly() { return rightOnly; }
        public void setRightOnly(int rightOnly) { this.rightOnly = rightOnly; }
        public int getModified() { return modified; }
        public void setModified(int modified) { this.modified = modified; }
        public int getSame() { return same; }
        public void setSame(int same) { this.same = same; }
        public int getTypeMismatch() { return typeMismatch; }
        public void setTypeMismatch(int typeMismatch) { this.typeMismatch = typeMismatch; }
        public int getContentChanged() { return contentChanged; }
        public void setContentChanged(int contentChanged) { this.contentChanged = contentChanged; }
        public int getAttrOnlyChanged() { return attrOnlyChanged; }
        public void setAttrOnlyChanged(int attrOnlyChanged) { this.attrOnlyChanged = attrOnlyChanged; }
        public int getScannedFiles() { return scannedFiles; }
        public void setScannedFiles(int scannedFiles) { this.scannedFiles = scannedFiles; }
        public int getScannedDirs() { return scannedDirs; }
        public void setScannedDirs(int scannedDirs) { this.scannedDirs = scannedDirs; }
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }

        @Override
        public String toString() {
            return String.format(
                    "仅左侧=%d 仅右侧=%d 两侧不同=%d(内容不同=%d, 仅属性不同=%d) 相同=%d 类型冲突=%d | "
                            + "扫描文件=%d 目录=%d 读取错误=%d",
                    leftOnly, rightOnly, modified, contentChanged, attrOnlyChanged, same, typeMismatch,
                    scannedFiles, scannedDirs, errors);
        }
    }

    /** 完整对比结果。 */
    public static final class FolderDiffResult {
        public final List<FolderEntry> roots;       // 顶层条目
        public final Map<String, FolderEntry> flat;  // 全部条目（含子树），key=relPath
        public final Summary summary;
        public final List<String> errors;            // 隔离的读取错误明细
        public final Path leftRoot;
        public final Path rightRoot;

        FolderDiffResult(List<FolderEntry> roots, Map<String, FolderEntry> flat, Summary summary,
                         List<String> errors, Path leftRoot, Path rightRoot) {
            this.roots = roots;
            this.flat = flat;
            this.summary = summary;
            this.errors = errors;
            this.leftRoot = leftRoot;
            this.rightRoot = rightRoot;
        }
    }

    /**
     * 对比两个目录。
     *
     * @param left  左侧（源）目录 A
     * @param right 右侧（目标）目录 B
     * @param opts  比对选项
     * @return 结构化对比结果
     * @throws IOException 当目录本身无法访问时抛出（单文件错误被隔离，不抛）
     */
    public static FolderDiffResult compare(Path left, Path right, Options opts) throws IOException {
        if (left == null || !Files.isDirectory(left)) {
            throw new IOException("不是有效目录（左侧）: " + left);
        }
        if (right == null || !Files.isDirectory(right)) {
            throw new IOException("不是有效目录（右侧）: " + right);
        }
        Path lRoot = left.toAbsolutePath().normalize();
        Path rRoot = right.toAbsolutePath().normalize();

        Map<String, SideInfo> leftMap = walk(lRoot, opts);
        Map<String, SideInfo> rightMap = walk(rRoot, opts);

        // 相对路径并集
        Set<String> keys = collectKeys(leftMap, rightMap);

        Summary summary = new Summary();
        List<String> errors = new ArrayList<>();
        Map<String, FolderEntry> flat = new LinkedHashMap<>();

        for (String key : keys) {
            SideInfo l = leftMap.getOrDefault(key, SideInfo.ABSENT);
            SideInfo r = rightMap.getOrDefault(key, SideInfo.ABSENT);
            FolderEntry e = buildEntry(key, l, r, opts, summary, errors);
            flat.put(key, e);
        }

        // 构建嵌套树
        List<FolderEntry> roots = buildTree(flat);

        // 目录/文件计数（含两侧）
        countSideInfos(leftMap, summary);
        countSideInfos(rightMap, summary);
        summary.errors = errors.size();

        return new FolderDiffResult(roots, flat, summary, errors, lRoot, rRoot);
    }

    /** 求两侧相对路径的并集（稳定字典序）。 */
    private static Set<String> collectKeys(Map<String, SideInfo> leftMap, Map<String, SideInfo> rightMap) {
        Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(leftMap.keySet());
        keys.addAll(rightMap.keySet());
        return keys;
    }

    /** 累加扫描的目录/文件计数（仅存在侧）。 */
    private static void countSideInfos(Map<String, SideInfo> map, Summary summary) {
        for (SideInfo s : map.values()) {
            if (s.exists) {
                if (s.type == EntryType.DIR) summary.scannedDirs++;
                else summary.scannedFiles++;
            }
        }
    }

    /** 递归遍历目录，返回 relPath → SideInfo。符号链接跳过，单文件错误隔离。 */
    private static Map<String, SideInfo> walk(Path root, Options opts) throws IOException {
        int depth = opts.maxDepth > 0 ? opts.maxDepth : Integer.MAX_VALUE;
        List<Path> collected = collectPaths(root, depth);
        return computeSideInfos(root, collected);
    }

    /** 遍历收集候选条目（跳过根自身、符号链接、不可读及特殊类型条目；异常隔离）。 */
    private static List<Path> collectPaths(Path root, int depth) throws IOException {
        List<Path> collected = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, depth)) {
            walk.forEach(p -> {
                if (root.equals(p)) return; // 跳过根自身
                if (isCollectable(root, p)) collected.add(p);
            });
        }
        return collected;
    }

    /** 判定条目是否应纳入采集（跳过符号链接/不可读/非文件非目录）。 */
    private static boolean isCollectable(Path root, Path p) {
        try {
            if (Files.isSymbolicLink(p)) {
                LOG.log(java.util.logging.Level.FINE, "[隔离] 跳过符号链接: {0}", root.relativize(p));
                return false;
            }
            BasicFileAttributes attrs = readAttrOrNull(p);
            if (attrs == null) return false; // 已记录
            return !attrs.isOther(); // 跳过套接字/管道
        } catch (Exception ex) {
            LOG.warning("[隔离] 跳过条目: " + p + " (" + ex.getMessage() + ")");
            return false;
        }
    }

    /** 读取属性；不可读时记录并返回 null。 */
    private static BasicFileAttributes readAttrOrNull(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ex) {
            LOG.warning("[隔离] 跳过不可读条目: " + p + " (" + ex.getMessage() + ")");
            return null;
        }
    }

    /** 并行计算派生 SideInfo（size/mtime/hash），写入同步 map。 */
    private static Map<String, SideInfo> computeSideInfos(Path root, List<Path> collected) {
        Map<String, SideInfo> map = new LinkedHashMap<>();
        AtomicInteger errCount = new AtomicInteger(0);
        collected.parallelStream().forEach(p -> {
            try {
                String rel = toRel(root, p);
                if (rel == null) return; // 越界防护
                BasicFileAttributes attrs = readAttrOrNull(p);
                if (attrs == null) {
                    errCount.incrementAndGet(); // readAttrOrNull 已记录
                    return;
                }
                boolean isDir = attrs.isDirectory();
                long size = isDir ? 0 : attrs.size();
                long mtime = attrs.lastModifiedTime().toMillis();
                String hash = hashIfSmall(rel, p, size, isDir, errCount);
                synchronized (map) {
                    map.put(rel, new SideInfo(true, isDir ? EntryType.DIR : EntryType.FILE,
                            size, mtime, hash, p.toString()));
                }
            } catch (Exception ex) {
                errCount.incrementAndGet();
                LOG.warning("[隔离] 处理失败: " + p + " (" + ex.getMessage() + ")");
            }
        });
        return map;
    }

    /** 求相对路径并做越界防护；非法时返回 null（不录入）。 */
    private static String toRel(Path root, Path p) {
        String rel = root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..")) return null;
        return rel;
    }

    /** 小文件计算 sha256；目录或超大文件返回 null，哈希失败隔离计数。 */
    private static String hashIfSmall(String rel, Path p, long size, boolean isDir, AtomicInteger errCount) {
        if (isDir || size > HARD_CAP) return null;
        try {
            return PackageParser.sha256(p);
        } catch (IOException ex) {
            errCount.incrementAndGet();
            LOG.warning("[隔离] 哈希失败: " + rel + " (" + ex.getMessage() + ")");
            return null;
        }
    }

    /** 由两侧 SideInfo 构建单条 FolderEntry 并累加汇总。 */
    private static FolderEntry buildEntry(String key, SideInfo l, SideInfo r, Options opts,
                                          Summary summary, List<String> errors) {
        EntryType type = resolveType(l, r);

        Long szL = l.exists ? l.size : null;
        Long szR = r.exists ? r.size : null;
        Long mtL = l.exists ? l.mtime : null;
        Long mtR = r.exists ? r.mtime : null;
        String hL = l.exists ? l.hash : null;
        String hR = r.exists ? r.hash : null;

        StatusAndChanges sc = resolveStatus(l, r, szL, szR, mtL, mtR, hL, hR, summary);
        String lineDiff = computeLineDiff(key, l, r, opts, sc.status, sc.changes, szL, szR, errors);

        return new FolderEntry(key, type, szL, szR, mtL, mtR, hL, hR,
                l.exists ? l.absPath : null, r.exists ? r.absPath : null,
                sc.status, sc.changes, lineDiff, null, false, null);
    }

    /** 类型判定：两侧均存在且类型一致取之；仅一侧取存在侧；类型冲突归为 FILE。 */
    private static EntryType resolveType(SideInfo l, SideInfo r) {
        if (l.exists && r.exists) return (l.type == r.type) ? l.type : EntryType.FILE;
        return l.exists ? l.type : r.type;
    }

    /** 判定顶层差异状态（左右缺失/类型冲突/目录；文件交由属性比较）。 */
    private static StatusAndChanges resolveStatus(SideInfo l, SideInfo r, // NOSONAR(S107) - 渲染参数由上层分别传递，重构参数对象收益低
                                                  Long szL, Long szR, Long mtL, Long mtR, String hL, String hR,
                                                  Summary summary) {
        if (!l.exists && !r.exists) {
            return StatusAndChanges.of(FolderDiffStatus.SAME); // 理论上不会出现（并集来自存在侧）
        }
        if (!l.exists) {
            summary.rightOnly++;
            return StatusAndChanges.of(FolderDiffStatus.RIGHT_ONLY);
        }
        if (!r.exists) {
            summary.leftOnly++;
            return StatusAndChanges.of(FolderDiffStatus.LEFT_ONLY);
        }
        if (l.type != r.type) {
            summary.typeMismatch++;
            return StatusAndChanges.of(FolderDiffStatus.TYPE_MISMATCH, AttrChange.TYPE);
        }
        if (l.type == EntryType.DIR) {
            // 目录：是否"不同"取决于子树是否有差异；此处仅标记 SAME，
            // 子树差异由上层通过 children 体现（树展示时目录节点带汇总标记）。
            summary.same++;
            return StatusAndChanges.of(FolderDiffStatus.SAME);
        }
        return resolveFileStatus(szL, szR, mtL, mtR, hL, hR, summary);
    }

    /** 文件比较：按 size/mtime/content(hash) 判定 SAME 或 MODIFIED。 */
    private static StatusAndChanges resolveFileStatus(Long szL, Long szR, Long mtL, Long mtR,
                                                      String hL, String hR, Summary summary) {
        boolean sizeEq = java.util.Objects.equals(szL, szR);
        boolean mtimeEq = java.util.Objects.equals(mtL, mtR);
        boolean contentEq = java.util.Objects.equals(hL, hR); // 超大文件两侧 hash 均可能为 null -> 视为未知，保守标 MODIFIED
        if (sizeEq && mtimeEq && contentEq) {
            summary.same++;
            return StatusAndChanges.of(FolderDiffStatus.SAME); // 相同项不标记任何属性变更
        }
        EnumSet<AttrChange> changes = EnumSet.noneOf(AttrChange.class);
        if (!sizeEq) changes.add(AttrChange.SIZE);
        if (!mtimeEq) changes.add(AttrChange.MTIME);
        if (!contentEq) {
            changes.add(AttrChange.CONTENT);
            summary.contentChanged++;
        } else {
            summary.attrOnlyChanged++;
        }
        summary.modified++;
        return new StatusAndChanges(FolderDiffStatus.MODIFIED, changes);
    }

    /** 是否应对该文本文件做行级内容 diff。 */
    private static boolean shouldComputeLineDiff(String key, SideInfo l, SideInfo r, Options opts, // NOSONAR(S107) - 渲染参数由上层分别传递，重构参数对象收益低
                                                 FolderDiffStatus status, Set<AttrChange> changes,
                                                 Long szL, Long szR) {
        if (!opts.computeLineDiff || status != FolderDiffStatus.MODIFIED) return false;
        if (!changes.contains(AttrChange.CONTENT)) return false;
        if (!l.exists || !r.exists) return false;
        if (l.type != EntryType.FILE || r.type != EntryType.FILE) return false;
        if (!isTextFile(key)) return false;
        if (szL == null || szR == null) return false;
        return szL <= opts.textDiffCap && szR <= opts.textDiffCap;
    }

    /** 文本文件行级 diff（失败隔离并记录）。 */
    private static String computeLineDiff(String key, SideInfo l, SideInfo r, Options opts, // NOSONAR(S107) - 渲染参数由上层分别传递，重构参数对象收益低
                                          FolderDiffStatus status, Set<AttrChange> changes,
                                          Long szL, Long szR, List<String> errors) {
        if (!shouldComputeLineDiff(key, l, r, opts, status, changes, szL, szR)) {
            return null;
        }
        try {
            String a = readTextQuietly(l.absPath);
            String b = readTextQuietly(r.absPath);
            return LineDiff.unified(a, b);
        } catch (IOException ex) {
            errors.add("diff:" + key + " -> " + ex.getMessage());
            LOG.warning("[隔离] 文本 diff 失败: " + key + " (" + ex.getMessage() + ")");
            return null;
        }
    }

    /** 由扁平 map 构建嵌套树（按路径段分组）。 */
    private static List<FolderEntry> buildTree(Map<String, FolderEntry> flat) {
        Map<String, List<FolderEntry>> childrenIndex = indexByParent(flat);
        attachChildren(flat, childrenIndex);
        List<FolderEntry> roots = collectRoots(flat);
        // 稳定排序：目录在前、再按名称
        roots.sort(FolderDiff::compareEntries);
        sortChildren(flat);
        return roots;
    }

    /** 按父路径索引全部条目。 */
    private static Map<String, List<FolderEntry>> indexByParent(Map<String, FolderEntry> flat) {
        Map<String, List<FolderEntry>> childrenIndex = new LinkedHashMap<>();
        for (FolderEntry e : flat.values()) {
            int cut = e.relPath.lastIndexOf('/');
            String parent = cut < 0 ? "" : e.relPath.substring(0, cut);
            childrenIndex.computeIfAbsent(parent, k -> new ArrayList<>()).add(e);
        }
        return childrenIndex;
    }

    /** 把每个条目的直接子节点挂到其 children（仅含相邻层级，避免把更深的孙代挂上来）。 */
    private static void attachChildren(Map<String, FolderEntry> flat,
                                       Map<String, List<FolderEntry>> childrenIndex) {
        for (FolderEntry e : flat.values()) {
            List<FolderEntry> kids = childrenIndex.get(e.relPath);
            if (kids != null && !kids.isEmpty()) {
                // 仅包含"直接子节点"（relPath 以 e.relPath + "/" 开头且下一段无更深斜杠）
                List<FolderEntry> direct = new ArrayList<>();
                for (FolderEntry k : kids) {
                    if (k == e) continue;
                    String rest = k.relPath.substring(e.relPath.length() + 1);
                    if (rest.indexOf('/') < 0) direct.add(k);
                }
                e.children.addAll(direct);
            }
        }
    }

    /** 收集顶层条目（relPath 无 '/'）。 */
    private static List<FolderEntry> collectRoots(Map<String, FolderEntry> flat) {
        List<FolderEntry> roots = new ArrayList<>();
        for (FolderEntry e : flat.values()) {
            if (e.relPath.indexOf('/') < 0) roots.add(e);
        }
        return roots;
    }

    /** 逐条对 children 稳定排序（目录在前、再按名称）。 */
    private static void sortChildren(Map<String, FolderEntry> flat) {
        for (FolderEntry e : flat.values()) {
            if (e.children != null) e.children.sort(FolderDiff::compareEntries);
        }
    }

    private static int compareEntries(FolderEntry a, FolderEntry b) {
        boolean ad = a.type == EntryType.DIR;
        boolean bd = b.type == EntryType.DIR;
        if (ad != bd) return ad ? -1 : 1;
        return a.relPath.compareTo(b.relPath);
    }

    /**
     * 目录子树是否含任何差异（自身或任一后代非 SAME）。
     * 用于树视图在父目录节点上提示「子树含差异」——目录自身按存在性/类型判等为 SAME，
     * 但其子树可能已有改动，避免父节点在展开前被误读为「无变化」。
     */
    public static boolean subtreeHasDiff(FolderEntry e) {
        if (e.status != FolderDiffStatus.SAME) return true;
        if (e.children != null) {
            for (FolderEntry c : e.children) {
                if (subtreeHasDiff(c)) return true;
            }
        }
        return false;
    }

    private static boolean isTextFile(String relPath) {
        int dot = relPath.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = relPath.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return TEXT_EXT.contains(ext);
    }

    private static String readTextQuietly(String absPath) throws IOException {
        byte[] bs = Files.readAllBytes(java.nio.file.Paths.get(absPath));
        for (String enc : new String[]{"UTF-8", "gbk", "latin-1"}) {
            try {
                return new String(bs, enc);
            } catch (Exception ignored) {
                // 尝试下一种编码
            }
        }
        return new String(bs);
    }

    /** 友好格式化修改时间（毫秒）。 */
    public static String fmtMtime(Long m) {
        if (m == null) return "-";
        return Instant.ofEpochMilli(m).atZone(ZoneId.systemDefault()).format(FMT);
    }

    /** 友好格式化大小（字节 → 人类可读）。 */
    public static String fmtSize(Long s) {
        if (s == null) return "-";
        if (s < 1024) return s + " B";
        double v = s;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format("%.1f %s", v, (i < 0 ? "B" : u[i]));
    }

    /** 状态 + 属性变更的中间载体（供 resolveStatus 系列返回）。 */
    private static final class StatusAndChanges {
        final FolderDiffStatus status;
        final Set<AttrChange> changes;
        StatusAndChanges(FolderDiffStatus status, Set<AttrChange> changes) {
            this.status = status;
            this.changes = changes;
        }
        static StatusAndChanges of(FolderDiffStatus status) {
            return new StatusAndChanges(status, EnumSet.noneOf(AttrChange.class));
        }
        static StatusAndChanges of(FolderDiffStatus status, AttrChange first) {
            return new StatusAndChanges(status, EnumSet.of(first));
        }
    }
}