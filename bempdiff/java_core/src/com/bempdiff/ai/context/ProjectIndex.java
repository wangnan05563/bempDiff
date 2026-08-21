package com.bempdiff.ai.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仓库级多项目上下文索引（递归识别增强 · 需求 3/4）。
 * 由 {@link ProjectIndexer} 对「上下文目录」做递归扫描产出：一个仓库根（如 D:\code\QJ\BEMP5.0DEV）
 * 下可能同时存在多个独立项目（Maven 聚合工程 / 子仓库 / 前端工程 / 部署产物等），
 * 每个项目都有一份独立的 {@link ProjectContext} 理解结果与指纹。
 *
 * <p>职责：
 * <ul>
 *   <li>聚合多项目的理解结果，{@link #toPromptSection(int)} 紧凑渲染为 AI prompt 上下文章节；</li>
 *   <li>{@link #locate(String)} 按差异文件路径反查其所属项目（stageB 逐文件精准注入）；</li>
 *   <li>{@link #toJsonMap()}/{@link ProjectIndex#fromJson(Map)} 支持缓存落盘/回读。</li>
 * </ul></p>
 */
public final class ProjectIndex {

    private final String rootPath;
    private final long scannedAt;
    private final List<ProjectEntry> projects;

    public ProjectIndex(String rootPath, long scannedAt, List<ProjectEntry> projects) {
        this.rootPath = rootPath;
        this.scannedAt = scannedAt;
        this.projects = (projects == null) ? List.of() : List.copyOf(projects);
    }

    public String getRootPath() { return rootPath; }
    public long getScannedAt() { return scannedAt; }
    public List<ProjectEntry> getProjects() { return projects; }
    public int size() { return projects.size(); }

    public boolean isEmpty() {
        return rootPath == null || rootPath.isEmpty() || projects.isEmpty();
    }

    // ---- 渲染（供 AI prompt 注入） ----

    /**
     * 渲染为「## 项目级上下文」文本块，多项目分节，总长不超过 maxChars（成本控制）。
     * 每项目输出精简字段：路径/构建系统/模块/依赖/入口/配置/技术栈/约定/简述。
     */
    public String toPromptSection(int maxChars) {
        if (isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目级上下文（递归扫描，共 ").append(projects.size())
          .append(" 个项目，根：").append(rootPath).append("）\n");
        int idx = 0;
        for (ProjectEntry e : projects) {
            ProjectContext c = e.getCtx();
            if (c == null) continue;
            idx++;
            sb.append("\n### 项目 ").append(idx).append('/').append(projects.size())
              .append("：").append(c.getBuildSystem()).append("（").append(e.getRelPath()).append("）\n");
            sb.append("- 模块：").append(join(c.getModules())).append("\n");
            sb.append("- 核心依赖：").append(join(c.getDependencies())).append("\n");
            sb.append("- 入口/主类：").append(join(c.getEntryPoints())).append("\n");
            sb.append("- 配置文件：").append(join(c.getConfigFiles())).append("\n");
            sb.append("- 技术栈：").append(join(c.getTechStack())).append("\n");
            if (c.getConventions() != null && !c.getConventions().isEmpty()) {
                sb.append("- 约定：").append(join(c.getConventions())).append("\n");
            }
            if (c.getSummary() != null && !c.getSummary().isEmpty()) {
                sb.append("- 简述：").append(c.getSummary()).append("\n");
            }
            if (sb.length() > maxChars) {
                sb.setLength(maxChars);
                sb.append("\n…（上下文超长截断，剩余项目省略）");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 按差异文件 key 反查所属项目（最长 relPath 前缀匹配）：
     * key 如 "banks/ext-fxbank/fxbank-adapter-api/src/main/java/..." → 命中 relPath="banks/ext-fxbank"。
     * 未命中返回 null（调用方回退到仓库级整体上下文）。
     */
    public ProjectContext locate(String fileKey) {
        if (fileKey == null) return null;
        String k = fileKey.replace('\\', '/');
        ProjectContext best = null;
        int bestLen = -1;
        for (ProjectEntry e : projects) {
            String rel = e.getRelPath();
            if (rel == null || rel.isEmpty() || ".".equals(rel)) {
                // 仓库根本身即项目（单项目兜底）：任意 key 均属该项目，但不参与最长前缀竞争（优先级最低）
                if (best == null) best = e.getCtx();
                continue;
            }
            String p = rel.replace('\\', '/');
            if (k.startsWith(p + "/") && p.length() > bestLen) {
                bestLen = p.length();
                best = e.getCtx();
            }
        }
        return best;
    }

    private static String join(List<String> l) {
        if (l == null || l.isEmpty()) return "（无）";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    // ---- 缓存序列化 ----

    @SuppressWarnings("unchecked")
    public static ProjectIndex fromJson(Map<String, Object> m) {
        String root = str(m.get("rootPath"));
        long scanned = (m.get("scannedAt") instanceof Number)
                ? ((Number) m.get("scannedAt")).longValue() : 0L;
        List<ProjectEntry> entries = new ArrayList<>();
        Object arr = m.get("projects");
        if (arr instanceof List) {
            for (Object o : (List<Object>) arr) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> em = (Map<String, Object>) o;
                String rel = str(em.get("relPath"));
                String fp = str(em.get("fingerprint"));
                int jc = (em.get("javaFileCount") instanceof Number)
                        ? ((Number) em.get("javaFileCount")).intValue() : 0;
                Object ctm = em.get("ctx");
                ProjectContext ctx = (ctm instanceof Map)
                        ? ProjectContext.fromJsonMap((Map<String, Object>) ctm) : null;
                if (ctx != null) entries.add(new ProjectEntry(rel, fp, jc, ctx));
            }
        }
        return new ProjectIndex(root, scanned, entries);
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rootPath", rootPath);
        m.put("scannedAt", scannedAt);
        List<Map<String, Object>> arr = new ArrayList<>();
        for (ProjectEntry e : projects) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("relPath", e.getRelPath());
            em.put("fingerprint", e.getFingerprint());
            em.put("javaFileCount", e.getJavaFileCount());
            em.put("ctx", e.getCtx().toJsonMap());
            arr.add(em);
        }
        m.put("projects", arr);
        return m;
    }

    private static String str(Object o) {
        return (o == null) ? "" : String.valueOf(o);
    }

    /** 单个被识别项目的条目：相对路径 + 指纹 + 项目级理解。 */
    public static final class ProjectEntry {
        private final String relPath;
        private final String fingerprint;
        private final int javaFileCount;
        private final ProjectContext ctx;

        /** 兼容旧构造（无 javaFileCount 统计）。 */
        public ProjectEntry(String relPath, String fingerprint, ProjectContext ctx) {
            this(relPath, fingerprint, 0, ctx);
        }

        public ProjectEntry(String relPath, String fingerprint, int javaFileCount, ProjectContext ctx) {
            this.relPath = relPath;
            this.fingerprint = fingerprint;
            this.javaFileCount = javaFileCount;
            this.ctx = ctx;
        }

        public String getRelPath() { return relPath; }
        public String getFingerprint() { return fingerprint; }
        public int getJavaFileCount() { return javaFileCount; }
        public ProjectContext getCtx() { return ctx; }
    }
}
