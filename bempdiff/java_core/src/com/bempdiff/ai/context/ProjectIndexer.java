package com.bempdiff.ai.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上下文目录递归识别器（需求 1/4）：在仓库根下递归发现全部独立项目并逐个理解。
 *
 * <p><b>项目边界判定规则</b>（由浅到深，纯文件系统启发式，不执行构建）：
 * <ol>
 *   <li><b>Maven 聚合工程</b>：packaging=pom 且含 &lt;modules&gt; 的目录 = 项目根，
 *       其声明的子模块归入该项目（不独立成项目）；</li>
 *   <li><b>孤立 Maven 模块</b>：叶子 pom（无 modules）若<b>未被任何祖先聚合 pom 引用</b>
 *       → 独立项目；被引用 → 从属模块；</li>
 *   <li><b>其它构建系统</b>：package.json（npm）、go.mod、Cargo.toml、
 *       requirements.txt/pyproject.toml 所在目录 = 独立项目；</li>
 *   <li><b>部署产物/非标准目录</b>：无任何构建文件的目录沿用
 *       {@link ProjectContextAnalyzer} 降级识别（WAR/JAR 解包、字节码/源码非标准布局）；</li>
 *   <li><b>兜底</b>：整根未识别出任何项目 → 仓库根本身作为单个项目（与旧行为一致）。</li>
 * </ol>
 * <b>剪枝</b>：跳过 target/node_modules/.git/@tmp 等产物与噪音目录，防止超大仓库
 * （BEMP5.0DEV 实测 13.8 万文件）拖垮扫描。</p>
 */
public final class ProjectIndexer {

    /** 构建文件扫描最大深度（聚合 pom 嵌套上限）。 */
    public static final int MAX_SCAN_DEPTH = 8;
    /** 收集到的构建文件数量上限（防止海量 node_modules 式噪音撑爆内存）。 */
    public static final int MAX_BUILD_FILES = 5000;
    /** 项目发现阶段遍历访问的文件数量上限（BEMP5.0DEV 实测 13.8 万文件，须放宽到 25 万）。 */
    public static final int MAX_TRAVERSAL_FILES = 250_000;
    /** 单项目指纹扫描文件数上限。 */
    public static final int MAX_FINGERPRINT_FILES = 20000;
    /** 指纹/文件统计遍历深度：须盖住 Maven 标准布局 src/main/java/com/... 深层包（12+）。 */
    public static final int MAX_FINGERPRINT_DEPTH = 14;

    private static final String MVN_FILE = "pom.xml";
    private static final String PKG_JSON = "package.json";
    private static final String GO_MOD = "go.mod";
    private static final String CARGO_TOML = "Cargo.toml";
    private static final String REQ_TXT = "requirements.txt";
    private static final String PYPROJECT = "pyproject.toml";

    /** 目录名剪枝名单：产物/噪音目录不参与项目发现与指纹统计。 */
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", ".idea", ".vscode", ".trae", ".venv", ".workbuddy",
            "target", "output", "dist", "build", "logs", "tmp", "temp", "screenshots",
            "reports", "test-reports", "testreports", "test-cases", "testcases", "archives",
            "console-logs", "defects", "delivery", "session_states", "test-data", "tools",
            "served", "aotutests-devtools", "aotutests-playwright");

    private static final Pattern POM_MODULES = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern POM_PACKAGING = Pattern.compile(
            "<packaging>\\s*([^<\\s]+)\\s*</packaging>");

    private ProjectIndexer() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 递归识别 root 下全部项目并逐个做项目级理解。
     * 识别结果按 relPath 字典序稳定输出；单项目兜底时 relPath="."。
     */
    public static ProjectIndex scan(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return new ProjectIndex(root == null ? "" : root.toString(), System.currentTimeMillis(), List.of());
        }
        // 1) 收集构建文件（一次受限遍历）
        BuildCollector bc = collectBuildFiles(root);
        // 2) 解析 Maven 聚合关系，判定项目根
        List<Path> projectRoots = resolveProjectRoots(root, bc);
        // 3) 每个项目做项目级理解 + 指纹
        List<ProjectIndex.ProjectEntry> entries = new ArrayList<>();
        for (Path pr : projectRoots) {
            ProjectContext ctx = ProjectContextAnalyzer.analyze(pr);
            Fingerprint fp = fingerprintOf(pr);
            String rel = root.relativize(pr).toString().replace('\\', '/');
            if (rel.isEmpty()) rel = ".";
            entries.add(new ProjectIndex.ProjectEntry(rel, fp.hash, fp.javaFileCount, ctx));
        }
        return new ProjectIndex(root.toString(), System.currentTimeMillis(), entries);
    }

    // ---- 构建文件收集（受限遍历，剪枝产物目录） ----

    private static final class BuildCollector {
        final List<Path> poms = new ArrayList<>();
        final List<Path> npmJsons = new ArrayList<>();
        final List<Path> otherBuildFiles = new ArrayList<>(); // go.mod/Cargo.toml/...
        int visitedFiles = 0;
    }

    private static BuildCollector collectBuildFiles(Path root) {
        BuildCollector bc = new BuildCollector();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_SCAN_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            // 根目录必须进入遍历；仅对子目录应用剪枝，否则一次都走不到任何构建文件
                            if (!dir.equals(root) && shouldSkipDir(bc, dir.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (bc.visitedFiles >= MAX_TRAVERSAL_FILES) return FileVisitResult.TERMINATE;
                            bc.visitedFiles++;
                            classifyBuildFile(bc, file);
                            // 构建文件收集到上限即终止（防 node_modules 式海量噪音）
                            if (bc.poms.size() + bc.npmJsons.size() + bc.otherBuildFiles.size() >= MAX_BUILD_FILES) {
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {
            // 部分子目录无权限：忽略，继续
        }
        return bc;
    }

    /** 目录是否应剪枝（产物/噪音目录，或遍历已达上限）。根目录自身恒不剪枝。 */
    private static boolean shouldSkipDir(BuildCollector bc, String dirName) {
        if (SKIP_DIRS.contains(dirName) || dirName.contains("@tmp") || dirName.startsWith(".")) {
            return true;
        }
        // 遍历文件总量达上限 → 剪掉后续子树（防 13.8 万级仓库拖垮扫描）
        return bc.visitedFiles >= MAX_TRAVERSAL_FILES;
    }

    /** 按文件名把构建文件归类到对应收集桶（Maven/前端/其它构建系统）。 */
    private static void classifyBuildFile(BuildCollector bc, Path file) {
        String n = file.getFileName().toString();
        if (MVN_FILE.equals(n)) bc.poms.add(file);
        else if (PKG_JSON.equals(n)) bc.npmJsons.add(file);
        else if (GO_MOD.equals(n) || CARGO_TOML.equals(n)
                || REQ_TXT.equals(n) || PYPROJECT.equals(n)) bc.otherBuildFiles.add(file);
    }

    // ---- Maven 聚合解析与项目根判定 ----

    private static final class PomNode {
        final Path dir;
        final boolean aggregator;   // packaging=pom 且含 <modules>
        final List<String> modules; // 聚合声明的子模块相对目录名

        PomNode(Path dir, boolean aggregator, List<String> modules) {
            this.dir = dir;
            this.aggregator = aggregator;
            this.modules = modules;
        }
    }

    private static List<Path> resolveProjectRoots(Path root, BuildCollector bc) {
        Set<Path> roots = new LinkedHashSet<>();
        // 3.1) Maven：解析聚合关系
        Map<Path, PomNode> pomByDir = new LinkedHashMap<>();
        for (Path pom : bc.poms) {
            pomByDir.put(pom.getParent(), parsePomNode(pom));
        }
        for (PomNode n : pomByDir.values()) {
            boolean aggregatorRoot = false;
            if (n.aggregator) {
                roots.add(n.dir); // 聚合工程 = 项目根
                aggregatorRoot = true;
            }
            if (!aggregatorRoot) {
                // 叶子 pom：仅当未被最近祖先聚合引用时视为独立项目
                PomNode parent = nearestAggregator(n.dir, pomByDir);
                if (parent == null || !parent.modules.contains(n.dir.getFileName().toString())) {
                    roots.add(n.dir); // 孤立叶子 → 独立项目
                }
            }
        }
        // 3.2) 前端 / Go / Rust / Python：目录即项目
        for (Path p : bc.npmJsons) roots.add(p.getParent());
        for (Path p : bc.otherBuildFiles) roots.add(p.getParent());
        // 3.3) 兜底：未识别到任何项目 → 仓库根本身作为单项目
        if (roots.isEmpty()) roots.add(root);
        // 稳定排序输出（relPath 字典序）
        List<Path> sorted = new ArrayList<>(roots);
        sorted.sort(Comparator.comparing(p -> root.relativize(p).toString().replace('\\', '/')));
        return sorted;
    }

    /** 解析单个 pom 是否为聚合工程：packaging=pom 且声明了 &lt;modules&gt; 时为聚合节点。读取失败按非聚合处理。 */
    private static PomNode parsePomNode(Path pom) {
        Path dir = pom.getParent();
        String txt = readSmall(pom);
        if (txt == null) return new PomNode(dir, false, List.of());
        Matcher pm = POM_PACKAGING.matcher(txt);
        boolean isPomPackaging = pm.find() && "pom".equals(pm.group(1).trim());
        Matcher mm = POM_MODULES.matcher(txt);
        List<String> mods = new ArrayList<>();
        while (mm.find()) mods.add(mm.group(1).trim());
        return new PomNode(dir, isPomPackaging && !mods.isEmpty(), mods);
    }

    /** 找 dir 的最近祖先目录中存在的聚合 pom（沿父目录链向上，跳过无关目录）。 */
    private static PomNode nearestAggregator(Path dir, Map<Path, PomNode> pomByDir) {
        Path cur = dir.getParent();
        while (cur != null) {
            PomNode n = pomByDir.get(cur);
            if (n != null) return n;
            cur = cur.getParent();
        }
        return null;
    }

    // ---- 指纹（缓存失效依据） ----

    /** 指纹结果：hash 供缓存失效比对；javaFileCount 供可视化展示每项目源码规模。 */
    public static final class Fingerprint {
        public final String hash;
        public final int javaFileCount;
        Fingerprint(String hash, int javaFileCount) {
            this.hash = hash;
            this.javaFileCount = javaFileCount;
        }
    }

    /** 兼容旧调用：仅取指纹 hash（缓存失效比对用）。 */
    public static String fingerprint(Path projectRoot) {
        return fingerprintOf(projectRoot).hash;
    }

    /**
     * 项目指纹 = sha1(构建文件内容) + "|" + 遍历文件数 + "|" + 源码最大 mtime；
     * 同时统计 .java 源码数。构建文件改动 / 源码增删改都会改变指纹 → 缓存自动失效重算。
     */
    public static Fingerprint fingerprintOf(Path projectRoot) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            updateBuildHash(md, projectRoot);
            int[] count = {0};
            int[] javaCount = {0};
            long[] maxMtime = {0};
            walkFingerprint(projectRoot, count, javaCount, maxMtime);
            md.update(("|" + count[0] + "|" + maxMtime[0]).getBytes(StandardCharsets.UTF_8));
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return new Fingerprint(sb.toString().substring(0, 32), javaCount[0]);
        } catch (NoSuchAlgorithmException e) {
            return new Fingerprint(String.valueOf(System.currentTimeMillis()), 0);
        }
    }

    private static void updateBuildHash(MessageDigest md, Path projectRoot) {
        for (String fn : new String[]{MVN_FILE, PKG_JSON, GO_MOD, CARGO_TOML}) {
            String txt = readSmall(projectRoot.resolve(fn));
            if (txt != null) md.update(fn.getBytes(StandardCharsets.UTF_8));
            if (txt != null) md.update(txt.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void walkFingerprint(Path projectRoot, int[] count, int[] javaCount, long[] maxMtime) {
        try {
            Files.walkFileTree(projectRoot, EnumSet.noneOf(FileVisitOption.class), MAX_FINGERPRINT_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (dir.equals(projectRoot)) return FileVisitResult.CONTINUE;
                            String n = dir.getFileName().toString();
                            if (SKIP_DIRS.contains(n) || n.contains("@tmp") || n.startsWith(".")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (count[0] >= MAX_FINGERPRINT_FILES) return FileVisitResult.TERMINATE;
                            count[0]++;
                            if (file.getFileName().toString().endsWith(".java")) javaCount[0]++;
                            long mt = attrs.lastModifiedTime().toMillis();
                            if (mt > maxMtime[0]) maxMtime[0] = mt;
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {
            // 忽略
        }
    }

    // ---- 工具 ----

    private static String readSmall(Path p) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            if (Files.size(p) > 2 * 1024 * 1024) return null;
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
