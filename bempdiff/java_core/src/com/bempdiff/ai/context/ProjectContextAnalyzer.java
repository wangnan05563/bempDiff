package com.bempdiff.ai.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 项目级上下文扫描器（AI 分析增强 · 需求 1/2）。
 * 离线目录扫描：识别构建系统、模块、依赖、入口、配置与约定，合成架构叙述，
 * 产出 {@link ProjectContext}。纯启发式（正则/文件名），无网络、无执行、可单测。
 *
 * <p>安全：{@link #MAX_FILES}/{@link #MAX_DEPTH} 限制遍历规模，防止超大目录拖垮 JVM
 * （与 PackageParser 的 zip-bomb 防护、HttpAiAnalyzer 的响应体上限同一哲学）。</p>
 */
public final class ProjectContextAnalyzer {

    public static final int MAX_FILES = 4000;
    public static final int MAX_DEPTH = 12;
    private static final long MAX_READ_BYTES = 2L * 1024 * 1024; // 单文件读取上限 2MB

    private ProjectContextAnalyzer() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static ProjectContext analyze(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return new ProjectContext("", "none", List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), "未提供有效工程目录");
        }
        List<Path> files = listFiles(root);
        String buildSystem = detectBuildSystem(root);
        List<String> modules = findModules(root, buildSystem);
        List<String> dependencies = extractDependencies(root, buildSystem);
        List<String> entryPoints = findEntryPoints(root, files);
        List<String> configFiles = findConfigFiles(root, files);
        List<String> techStack = inferTechStack(buildSystem, dependencies);
        List<String> conventions = inferConventions(files);
        String summary = synthesizeSummary(buildSystem, modules, dependencies, entryPoints, conventions);
        return new ProjectContext(root.toString(), buildSystem, modules, dependencies,
                entryPoints, configFiles, techStack, conventions, summary);
    }

    // ---- 目录遍历（受限） ----

    private static List<Path> listFiles(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (files.size() >= MAX_FILES) return FileVisitResult.TERMINATE;
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (files.size() >= MAX_FILES) return FileVisitResult.TERMINATE;
                            files.add(file);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {
            // 部分子目录无权限：忽略，继续
        }
        return files;
    }

    // ---- 构建系统识别 ----

    private static String detectBuildSystem(Path root) {
        if (exists(root, "pom.xml")) return "Maven";
        if (exists(root, "build.gradle") || exists(root, "build.gradle.kts")) return "Gradle";
        if (exists(root, "package.json")) return "npm";
        if (exists(root, "go.mod")) return "Go";
        if (exists(root, "requirements.txt") || exists(root, "pyproject.toml")) return "Python";
        if (exists(root, "Cargo.toml")) return "Rust";
        return "none";
    }

    private static boolean exists(Path root, String name) {
        Path p = root.resolve(name);
        return Files.isRegularFile(p);
    }

    // ---- 模块识别 ----

    private static List<String> findModules(Path root, String bs) {
        Set<String> mods = new LinkedHashSet<>();
        if ("Maven".equals(bs)) {
            String txt = readIfSmall(root.resolve("pom.xml"));
            if (txt != null) {
                Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(txt);
                while (m.find()) mods.add(m.group(1).trim());
            }
        } else if ("Gradle".equals(bs)) {
            String txt = readIfSmall(root.resolve("settings.gradle"));
            if (txt == null) txt = readIfSmall(root.resolve("settings.gradle.kts"));
            if (txt != null) {
                Matcher m = Pattern.compile("include\\s*\\(?\\s*['\"]([^'\"]+)['\"]")
                        .matcher(txt);
                while (m.find()) mods.add(m.group(1).trim().replace(":", "/"));
            }
        }
        // 兜底：含独立构建文件的子目录视为模块
        if (mods.isEmpty()) {
            for (String fn : List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json")) {
                Path child = root.resolve(fn);
                if (Files.isRegularFile(child) && child.getParent() != null
                        && !child.getParent().equals(root)) {
                    mods.add(root.relativize(child.getParent()).toString().replace('\\', '/'));
                }
            }
        }
        return capped(new ArrayList<>(mods), 40);
    }

    // ---- 依赖抽取 ----

    private static List<String> extractDependencies(Path root, String bs) {
        Set<String> deps = new LinkedHashSet<>();
        if ("Maven".equals(bs)) {
            String txt = readIfSmall(root.resolve("pom.xml"));
            if (txt != null) {
                Matcher m = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(txt);
                while (m.find()) deps.add(m.group(1).trim());
            }
        } else if ("Gradle".equals(bs)) {
            for (String fn : List.of("build.gradle", "build.gradle.kts")) {
                String txt = readIfSmall(root.resolve(fn));
                if (txt == null) continue;
                Matcher m = Pattern.compile(
                        "(?:implementation|api|compileOnly|testImplementation|runtimeOnly)\\s*\\(?\\s*['\"]([^'\"]+)['\"]")
                        .matcher(txt);
                while (m.find()) deps.add(m.group(1).trim());
            }
        } else if ("npm".equals(bs)) {
            String txt = readIfSmall(root.resolve("package.json"));
            if (txt != null) {
                Matcher m = Pattern.compile("\"([a-zA-Z0-9@/_\\-.]+)\"\\s*:\\s*\"[^}]\"")
                        .matcher(txt);
                while (m.find()) deps.add(m.group(1).trim());
            }
        }
        return capped(new ArrayList<>(deps), 60);
    }

    // ---- 入口/主类 ----

    private static List<String> findEntryPoints(Path root, List<Path> files) {
        Set<String> eps = new LinkedHashSet<>();
        for (Path f : files) {
            String name = f.getFileName().toString();
            if (name.endsWith(".java")) {
                // 入口类名约定（后缀），避免 "mapper/wrapper" 等含 "app" 子串误命中
                String low = name.toLowerCase();
                boolean nameHint = low.endsWith("application.java") || low.endsWith("app.java")
                        || low.endsWith("main.java") || low.endsWith("bootstrap.java")
                        || low.endsWith("starter.java") || low.endsWith("launcher.java");
                if (nameHint) {
                    String txt = readIfSmall(f);
                    if (txt != null && txt.contains("public static void main")) {
                        eps.add(rel(root, f));
                    }
                }
            } else if (name.equals("index.html") || name.equals("main.js")
                    || name.equals("main.ts") || name.endsWith("App.jsx")
                    || name.endsWith("App.vue")) {
                eps.add(rel(root, f));
            }
        }
        return capped(new ArrayList<>(eps), 20);
    }

    // ---- 配置文件 ----

    private static List<String> findConfigFiles(Path root, List<Path> files) {
        Set<String> cf = new LinkedHashSet<>();
        for (Path f : files) {
            String name = f.getFileName().toString().toLowerCase();
            boolean match = name.equals("application.yml")
                    || name.equals("application.properties")
                    || name.equals("application.yaml")
                    || name.equals("config.json")
                    || name.equals(".env.example")
                    || name.endsWith(".conf")
                    || (name.startsWith("application-")
                        && (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")));
            if (match) cf.add(rel(root, f));
        }
        return capped(new ArrayList<>(cf), 20);
    }

    // ---- 技术栈推断 ----

    private static List<String> inferTechStack(String bs, List<String> deps) {
        Set<String> ts = new LinkedHashSet<>();
        ts.add(bs);
        for (String d : deps) {
            String low = d.toLowerCase();
            if (low.contains("spring")) ts.add("Spring");
            else if (low.contains("mybatis")) ts.add("MyBatis");
            else if (low.contains("rocketmq") || low.contains("kafka")) ts.add("消息队列");
            else if (low.contains("redis")) ts.add("Redis");
            else if (low.contains("react") || low.contains("vue")) ts.add("前端框架");
            else if (low.contains("hibernate")) ts.add("Hibernate");
        }
        return new ArrayList<>(ts);
    }

    // ---- 约定推断（包根 + 命名） ----

    private static List<String> inferConventions(List<Path> files) {
        Set<String> conv = new LinkedHashSet<>();
        // 包根：所有 src/main/java 下 Java 源文件所在包目录（文件名之前的目录）的公共前缀
        List<String[]> pkgDirs = new ArrayList<>();
        for (Path f : files) {
            String s = f.toString().replace('\\', '/');
            int idx = s.indexOf("/src/main/java/");
            if (idx < 0) continue;
            String after = s.substring(idx + "/src/main/java/".length());
            int lastSlash = after.lastIndexOf('/');
            // 去掉文件名，取其所在包的目录路径（如 com/example/foo）
            String dir = (lastSlash < 0) ? "" : after.substring(0, lastSlash);
            if (!dir.isEmpty()) {
                pkgDirs.add(dir.split("/"));
            }
        }
        if (!pkgDirs.isEmpty()) {
            // 计算所有包目录的公共前缀层级（真正的「包根」）
            String[] first = pkgDirs.get(0);
            int common = first.length;
            for (String[] p : pkgDirs) {
                int lim = Math.min(common, p.length);
                int i = 0;
                while (i < lim && p[i].equals(first[i])) i++;
                common = i;
            }
            // 最深包层级（用于判断项目包结构深度）
            int maxDepth = 0;
            for (String[] p : pkgDirs) {
                maxDepth = Math.max(maxDepth, p.length);
            }
            StringBuilder rootSb = new StringBuilder();
            for (int i = 0; i < common; i++) {
                if (i > 0) rootSb.append('.');
                rootSb.append(first[i]);
            }
            String rootStr = rootSb.length() == 0 ? "(默认包/无层级)" : rootSb.toString();
            conv.add("Java 包根：" + rootStr + "（公共 " + common + " 级 / 最深 " + maxDepth + " 级）");
        }
        // 命名约定频次
        long mapper = 0, service = 0, controller = 0;
        for (Path f : files) {
            String n = f.getFileName().toString();
            if (n.endsWith("Mapper.java") || n.endsWith("Dao.java")) mapper++;
            else if (n.endsWith("Service.java")) service++;
            else if (n.endsWith("Controller.java") || n.endsWith("Resource.java")) controller++;
        }
        if (mapper > 0) conv.add("持久层命名 *Mapper/*Dao（" + mapper + " 个）");
        if (service > 0) conv.add("服务层命名 *Service（" + service + " 个）");
        if (controller > 0) conv.add("接口/控制层命名 *Controller/*Resource（" + controller + " 个）");
        return capped(new ArrayList<>(conv), 10);
    }

    // ---- 架构叙述合成 ----

    private static String synthesizeSummary(String bs, List<String> modules, List<String> deps,
                                            List<String> eps, List<String> conv) {
        if ("none".equals(bs) && (modules == null || modules.isEmpty())) {
            return "未识别到标准构建系统，仅基于目录结构推断；架构分层信息有限，请谨慎参考。";
        }
        StringBuilder sb = new StringBuilder();
        if (modules != null && !modules.isEmpty()) {
            sb.append("多模块工程（").append(bs).append("），模块含 ")
              .append(String.join("、", modules)).append("；");
        } else {
            sb.append("单模块工程（").append(bs).append("）；");
        }
        if (eps != null && !eps.isEmpty()) {
            sb.append("入口/主类：").append(String.join("、", eps)).append("；");
        }
        if (conv != null && !conv.isEmpty()) {
            sb.append("约定：").append(String.join("；", conv)).append("。");
        }
        return sb.toString();
    }

    // ---- 工具 ----

    private static String rel(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static List<String> capped(List<String> in, int max) {
        return in.size() > max ? in.subList(0, max) : in;
    }

    private static String readIfSmall(Path p) {
        if (p == null || !Files.isRegularFile(p)) return null;
        try {
            if (Files.size(p) > MAX_READ_BYTES) return null;
            return new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
