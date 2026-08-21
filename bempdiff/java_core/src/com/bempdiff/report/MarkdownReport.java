package com.bempdiff.report;

import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.FileRisk;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.diff.DiffDigest;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.LibJarDiff;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 报告导出（T12，FR7）。对应 prototype: write_markdown_report。
 * 默认包含：差异统计 + 全量清单 + Top-K 完整源码 diff（Java 类 + 前端 JS/HTML/CSS）+ 破坏性变更清单（删除类）。
 * 说明：本移植版先实现"无 AI 章节"的基础报告（AI 章节待 core.ai 接入后补）。
 */
public final class MarkdownReport {

    private static final String DIFF_OPEN = "```diff\n";
    private static final String DIFF_CLOSE = "\n```\n\n";
    private static final String BESIDES = "> 另有 ";

    private final int topK;

    public MarkdownReport(int topK) {
        this.topK = topK;
    }

    /** 本次 AI 分析聚焦项（可读标签，如「破坏性变更专项」）；null 表示默认整体分析。 */
    private String aiFocus = null;

    /** 设置报告 AI 章节的分析聚焦标注（由 BempServer.runAiAnalysis 按 category/prompt 派生）。 */
    public MarkdownReport setAiFocus(String focus) {
        this.aiFocus = focus;
        return this;
    }

    public String getAiFocus() {
        return aiFocus;
    }

    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text) {
        return render(oldSnap, newSnap, r, stats, decompiled, text, (LibJarDiff.Result) null);
    }

    /** 渲染（含差异依赖 JAR 内部源码对比章节）。libJar 为 null 时跳过该章节。 */
    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text, LibJarDiff.Result libJar) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, oldSnap, newSnap);
        renderStats(sb, stats);
        renderTypeBreakdown(sb, r, oldSnap, newSnap);
        renderFileTree(sb, r);
        renderDecompiledDiff(sb, decompiled);
        renderTextDiff(sb, text);
        renderLibJarDiff(sb, libJar);
        renderBreakingChanges(sb, r);
        renderFooter(sb, oldSnap, newSnap, false);
        return sb.toString();
    }

    /**
     * 渲染（含 AI 智能分析章节与项目级上下文增强）。无 AI 数据时 summary/fileAnalyses 传 null，
     * 未启用上下文增强时 ctx 传 null —— 均向后兼容原 6 参 render。
     */
    // NOSONAR: 多参重载为既有公开 API，签名不可变更（见任务约束）。
    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text,
                          StageASummary summary, List<FileAnalysis> fileAnalyses, ProjectContext ctx) {
        return render(oldSnap, newSnap, r, stats, decompiled, text,
                summary, fileAnalyses, ctx, (LibJarDiff.Result) null);
    }

    /** 渲染（含差异依赖 JAR 内部源码对比章节 + AI 智能分析章节）。libJar 为 null 时跳过 JAR 章节。 */
    // NOSONAR: 多参重载为既有公开 API，签名不可变更（见任务约束）。
    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text,
                          StageASummary summary, List<FileAnalysis> fileAnalyses, ProjectContext ctx,
                          LibJarDiff.Result libJar) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, oldSnap, newSnap);
        renderStats(sb, stats);
        renderTypeBreakdown(sb, r, oldSnap, newSnap);
        renderFileTree(sb, r);
        renderDecompiledDiff(sb, decompiled);
        renderTextDiff(sb, text);
        renderLibJarDiff(sb, libJar);
        renderBreakingChanges(sb, r);
        appendAiSection(sb, summary, fileAnalyses, ctx);
        renderFooter(sb, oldSnap, newSnap, summary != null);
        return sb.toString();
    }

    /** 追加 AI 智能分析章节：分析聚焦标注 + 项目级上下文摘要 + 阶段A 概览 + 阶段B 逐文件。 */
    private void appendAiSection(StringBuilder sb, StageASummary summary,
                                 List<FileAnalysis> fileAnalyses, ProjectContext ctx) {
        sb.append("## 七、AI 智能分析（两阶段 / 项目级上下文增强）\n");
        // 修复：显式标注本次所选分析项，避免「不同分析项生成的报告看起来相同」
        if (aiFocus != null && !aiFocus.isEmpty()) {
            sb.append("- **本次分析聚焦**：").append(aiFocus).append("\n\n");
        }
        appendAiContext(sb, ctx);
        appendStageASummary(sb, summary);
        appendStageB(sb, fileAnalyses);
        if ((ctx == null || ctx.isEmpty()) && summary == null) {
            sb.append("- 未启用 AI 分析 / 项目级上下文增强。\n");
        }
        sb.append("\n");
    }

    private void appendAiContext(StringBuilder sb, ProjectContext ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return;
        }
        sb.append("### 项目级上下文（分析依据）\n");
        sb.append("- 构建系统：").append(nullToNA(ctx.getBuildSystem())).append("\n");
        sb.append("- 模块：").append(join(ctx.getModules())).append("\n");
        sb.append("- 核心依赖：").append(join(ctx.getDependencies())).append("\n");
        sb.append("- 配置文件：").append(join(ctx.getConfigFiles())).append("\n");
        if (!isEmpty(ctx.getSummary())) {
            sb.append("- 架构简述：").append(ctx.getSummary()).append("\n");
        }
        sb.append("\n");
    }

    private void appendStageASummary(StringBuilder sb, StageASummary summary) {
        if (summary == null) {
            return;
        }
        sb.append("### 阶段A 概览\n");
        sb.append("- 整体风险：**").append(nullToNA(summary.getOverallRisk())).append("**\n");
        sb.append("- 影响范围：").append(nullToNA(summary.getImpactScope())).append("\n");
        if (!isEmpty(summary.getContextInfluence())) {
            sb.append("- **项目上下文影响**：").append(summary.getContextInfluence()).append("\n");
        }
        if (!isEmptyList(summary.getTestThemes())) {
            sb.append("- 测试主题：").append(String.join("；", summary.getTestThemes())).append("\n");
        }
        if (!isEmptyList(summary.getFileRisks())) {
            sb.append("- 每文件初评风险：\n");
            for (FileRisk fr : summary.getFileRisks()) {
                sb.append("  - `").append(nullToNA(fr.getKey())).append("`：**")
                        .append(nullToNA(fr.getRisk())).append("** — ").append(nullToNA(fr.getOneLineReason())).append("\n");
            }
        }
        sb.append("\n");
    }

    private void appendStageB(StringBuilder sb, List<FileAnalysis> fileAnalyses) {
        if (isEmptyList(fileAnalyses)) {
            return;
        }
        sb.append("### 阶段B 逐文件深读\n");
        for (FileAnalysis fa : fileAnalyses) {
            appendFileAnalysis(sb, fa);
        }
    }

    private void appendFileAnalysis(StringBuilder sb, FileAnalysis fa) {
        sb.append("#### ").append(nullToNA(fa.getKey())).append("\n");
        sb.append("- 风险：**").append(nullToNA(fa.getRisk())).append("**\n");
        sb.append("- 意图：").append(nullToNA(fa.getIntent())).append("\n");
        sb.append("- 影响：").append(nullToNA(fa.getImpact())).append("\n");
        if (!isEmpty(fa.getContextInfluence())) {
            sb.append("- **项目上下文影响**：").append(fa.getContextInfluence()).append("\n");
        }
        if (!isEmptyList(fa.getTestPoints())) {
            sb.append("- 测试要点：\n");
            for (String tp : fa.getTestPoints()) {
                sb.append("  - ").append(tp).append("\n");
            }
        }
        sb.append("\n");
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static boolean isEmptyList(List<?> l) {
        return l == null || l.isEmpty();
    }

    private static String join(List<String> l) {
        if (isEmptyList(l)) {
            return "（无）";
        }
        return String.join("、", l);
    }

    private void renderHeader(StringBuilder sb, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        sb.append("# 差异分析报告（Java 端口 / 真实包比对）\n\n");
        sb.append("- 老包：`").append(oldSnap.getFile().getFileName()).append("`（版本 ")
                .append(nullToNA(oldSnap.getVersion())).append("）\n");
        sb.append("- 新包：`").append(newSnap.getFile().getFileName()).append("`（版本 ")
                .append(nullToNA(newSnap.getVersion())).append("）\n\n");
    }

    private void renderStats(StringBuilder sb, DiffStats stats) {
        sb.append("## 一、差异统计\n");
        sb.append("- 新增 **").append(stats.getAdded()).append("** · 删除 **").append(stats.getDeleted())
                .append("** · 修改 **").append(stats.getModified()).append("** · 未变 ").append(stats.getUnchanged()).append("\n");
        sb.append("- 业务/类级变更(非jar)：").append(stats.getBizChanged())
                .append(" · jar 级变更：").append(stats.getJarChanged()).append("\n\n");
    }

    private void renderFileTree(StringBuilder sb, DiffResult r) {
        sb.append("## 二、差异文件树（全量清单）\n");
        DiffStatus[] order = {DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED};
        for (DiffStatus st : order) {
            for (String k : r.get(st)) {
                sb.append("- `").append(mark(st)).append("` ").append(k).append("\n");
            }
        }
        sb.append("\n");
    }

    private void renderDecompiledDiff(StringBuilder sb, Map<String, DecompiledUnit> decompiled) {
        sb.append("## 三、反编译源码级差异（Top-").append(topK).append(" 修改/新增/删除 Java 类）\n");
        sb.append("> 共 ").append(decompiled.size()).append(" 个类已反编译（本处仅展示前 Top-K 条，"
                + "且每条仅列变更行：附原始/修改后行号与变更类型——修改/新增/删除，不展示整个文件）。\n\n");
        int shown = 0;
        for (Map.Entry<String, DecompiledUnit> e : decompiled.entrySet()) {
            if (shown >= topK) {
                break;
            }
            DecompiledUnit u = e.getValue();
            sb.append("### ").append(e.getKey()).append("\n");
            if (!u.isOk()) {
                sb.append("- 反编译失败：").append(u.getError()).append("\n\n");
            } else {
                sb.append("- 反编译引擎：").append(u.getEngine()).append("\n");
                sb.append(DIFF_OPEN).append(renderCompactDiff(u.getDiffText())).append(DIFF_CLOSE);
                shown++;
            }
        }
        if (decompiled.size() > topK) {
            sb.append(BESIDES).append(decompiled.size() - topK).append(" 个修改类未展开（Top-K 限制）。\n\n");
        }
    }

    /**
     * 差异依赖 JAR 内部源码对比（Req 6）：对 WAR 中存在差异的 lib jar，逐一展开内部 class，
     * 反编译后做源码级 diff，清晰标注每个 class 的新增/删除/修改/未变，并附 Top-K 源码 diff。
     * libJar 为 null 时跳过（向后兼容无 JAR 对比的报告）。
     */
    private void renderLibJarDiff(StringBuilder sb, LibJarDiff.Result libJar) {
        if (libJar == null || libJar.isEmpty()) {
            return;
        }
        sb.append("## 五、差异依赖 JAR 内部源码对比（Top-").append(topK)
          .append(" 内部 class 反编译源码级 diff）\n");
        sb.append("> 共 ").append(libJar.totalJars).append(" 个差异 JAR（新增 ")
          .append(countJarStatus(libJar, DiffStatus.ADDED)).append(" / 修改 ")
          .append(countJarStatus(libJar, DiffStatus.MODIFIED)).append(" / 删除 ")
          .append(countJarStatus(libJar, DiffStatus.DELETED)).append("）；其内部 class 合计：新增 ")
          .append(libJar.totalAdded).append(" · 删除 ").append(libJar.totalRemoved)
          .append(" · 修改 ").append(libJar.totalModified).append(" · 未变 ")
          .append(libJar.totalUnchanged).append("。\n\n");
        for (LibJarDiff.DiffJarInfo jar : libJar.jars) {
            renderJarDetail(sb, jar);
        }
    }

    /** 渲染单个差异 JAR 的详情：标题、内部 class 状态统计与逐 class 的源码对比。 */
    private void renderJarDetail(StringBuilder sb, LibJarDiff.DiffJarInfo jar) {
        if (jar.failed) {
            sb.append("### ").append(jar.jarKey).append("  [分析失败]\n");
            sb.append("- ⚠️ 该 JAR 读取/枚举失败，已跳过（不影响其余 JAR 与报告其余章节）：")
              .append(jar.error == null ? "未知错误" : jar.error).append("\n\n");
            return;
        }
        sb.append("### ").append(jar.jarKey).append("  [")
          .append(statusLabel(jar.jarStatus)).append("]\n");
        sb.append("- 内部 class：新增 **").append(jar.added).append("** · 删除 **").append(jar.removed)
          .append("** · 修改 **").append(jar.modified).append("** · 未变 ").append(jar.unchanged).append("\n\n");
        int shown = 0;
        for (LibJarDiff.LibClassUnit u : jar.classes) {
            shown = renderOneLibClass(sb, u, shown);
        }
        appendJarClassSummary(sb, jar);
    }

    /** 渲染单个 lib 内部 class 的对比条目，返回最新已展开条数（用于 Top-K 截断）。 */
    private int renderOneLibClass(StringBuilder sb, LibJarDiff.LibClassUnit u, int shown) {
        DecompiledUnit du = u.unit;
        if (du == null) {
            // 未提供反编译单元（Top-K 之外）：仅跳过，不做任何展示。
            return shown;
        }
        if (!du.isOk()) {
            sb.append("- `").append(u.innerClass).append("` [").append(statusLabel(u.status))
              .append("] 反编译失败：").append(du.getError()).append("\n");
            return shown;
        }
        if (shown >= topK) {
            return shown;
        }
        sb.append("#### ").append(u.innerClass).append("  [").append(statusLabel(u.status)).append("]\n");
        sb.append("- 反编译引擎：").append(du.getEngine()).append("\n");
        sb.append(DIFF_OPEN).append(renderCompactDiff(du.getDiffText())).append(DIFF_CLOSE);
        return shown + 1;
    }

    /** 收尾计数拆分：未变更 / 反编译失败 / 超出全局 Top-K 三类，避免把未变更误归为 Top-K 限制。 */
    private static void appendJarClassSummary(StringBuilder sb, LibJarDiff.DiffJarInfo jar) {
        long unchanged = 0;
        long failedUnits = 0;
        long decompiledCount = 0;
        for (LibJarDiff.LibClassUnit u : jar.classes) {
            if (u.status == DiffStatus.UNCHANGED) {
                unchanged++;
            }
            if (u.unit != null && !u.unit.isOk()) {
                failedUnits++;
            }
            if (u.unit != null && u.unit.isOk()) {
                decompiledCount++;
            }
        }
        long beyondTopK = jar.classes.size() - unchanged - failedUnits - decompiledCount;
        List<String> parts = new java.util.ArrayList<>();
        if (beyondTopK > 0) parts.add(beyondTopK + " 个超出全局 Top-K 未展开源码");
        if (unchanged > 0) parts.add(unchanged + " 个未变更（已折叠，无需反编译）");
        if (failedUnits > 0) parts.add(failedUnits + " 个反编译失败");
        if (!parts.isEmpty()) {
            sb.append(BESIDES).append(String.join(" · ", parts)).append("；")
              .append("可在 GUI 双击该 JAR 查看全部内部 class 的逐项反编译对比。\n\n");
        }
    }

    /** 统计差异 JAR 的个数（按 jar 自身状态），用于章节开头的总览。 */
    private static long countJarStatus(LibJarDiff.Result libJar, DiffStatus st) {
        long c = 0;
        for (LibJarDiff.DiffJarInfo jar : libJar.jars) {
            if (jar.jarStatus == st) {
                c++;
            }
        }
        return c;
    }

    private static String statusLabel(DiffStatus st) {
        switch (st) {
            case ADDED: return "新增 +";
            case DELETED: return "删除 -";
            case MODIFIED: return "修改 ~";
            default: return "未变 =";
        }
    }

    /** 按文件类型汇总变更分布（新增/删除/修改），清晰标注每种文件类型的变更情况（需求扩展）。 */
    private void renderTypeBreakdown(StringBuilder sb, DiffResult r,
                                     PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        // categoryLabel -> [added, deleted, modified]
        Map<String, long[]> counts = new java.util.TreeMap<>();
        DiffStatus[] order = {DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.MODIFIED};
        for (DiffStatus st : order) {
            for (String k : r.get(st)) {
                LogicalEntry e = oldSnap.getEntries().get(k);
                if (e == null) {
                    e = newSnap.getEntries().get(k);
                }
                FileClass fc = (e != null) ? e.getFileClass() : FileClass.OTHER;
                String cat = fc.categoryLabel();
                long[] c = counts.computeIfAbsent(cat, x -> new long[3]);
                if (st == DiffStatus.ADDED) {
                    c[0]++;
                } else if (st == DiffStatus.DELETED) {
                    c[1]++;
                } else {
                    c[2]++;
                }
            }
        }
        sb.append("### 按文件类型分布\n");
        sb.append("| 文件类型 | 新增 | 删除 | 修改 |\n");
        sb.append("|---|---|---|---|\n");
        for (Map.Entry<String, long[]> en : counts.entrySet()) {
            long[] c = en.getValue();
            sb.append("| ").append(en.getKey()).append(" | ").append(c[0]).append(" | ")
              .append(c[1]).append(" | ").append(c[2]).append(" |\n");
        }
        long totalAdded = r.get(DiffStatus.ADDED).size();
        long totalDeleted = r.get(DiffStatus.DELETED).size();
        long totalModified = r.get(DiffStatus.MODIFIED).size();
        sb.append("| **合计** | **").append(totalAdded).append("** | **").append(totalDeleted)
          .append("** | **").append(totalModified).append("** |\n\n");
    }

    /**
     * 文本类文件内容差异（配置文件 XML/Properties、JSP、前端 JS/HTML/CSS）：压缩/单行资源已
     * 美化或换行归一后做内容级逐行 diff，使改动可读（FR4.4 增强 + 本需求扩展）。
     */
    private void renderTextDiff(StringBuilder sb, Map<String, DecompiledUnit> text) {
        sb.append("## 四、文本类文件内容差异（Top-").append(topK)
          .append(" 修改/新增/删除 配置文件/JSP/JS/HTML/CSS）\n");
        if (isEmptyMap(text)) {
            sb.append("- 无文本类文件（配置文件/JSP/前端源码）变动。\n\n");
            return;
        }
        sb.append("> 共 ").append(text.size()).append(" 个文本类文件已做内容级逐行 diff")
          .append("（本处仅展示前 Top-K 条，且每条仅列变更行：附原始/修改后行号与变更类型——修改/新增/删除，不展示整个文件）。\n\n");
        int shown = 0;
        for (Map.Entry<String, DecompiledUnit> e : text.entrySet()) {
            if (shown >= topK) {
                break;
            }
            DecompiledUnit u = e.getValue();
            sb.append("### ").append(e.getKey()).append("\n");
            if (!u.isOk()) {
                sb.append("- 读取/美化失败：").append(u.getError()).append("\n\n");
            } else {
                sb.append("- 处理引擎：").append(u.getEngine()).append("\n");
                sb.append(DIFF_OPEN).append(renderCompactDiff(u.getDiffText())).append(DIFF_CLOSE);
                shown++;
            }
        }
        if (text.size() > topK) {
            sb.append(BESIDES).append(text.size() - topK).append(" 个文本类文件未展开（Top-K 限制）。\n\n");
        }
    }

    private static boolean isEmptyMap(Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    private void renderBreakingChanges(StringBuilder sb, DiffResult r) {
        sb.append("## 六、破坏性变更清单（删除类/删除前端资源）\n");
        if (r.get(DiffStatus.DELETED).isEmpty()) {
            sb.append("- 无删除类。\n");
        } else {
            for (String k : r.get(DiffStatus.DELETED)) {
                sb.append("- `").append(k).append("`（潜在对外 API / 行为移除，需重点回归）\n");
            }
        }
        sb.append("\n");
    }

    private void renderFooter(StringBuilder sb, PackageSnapshot oldSnap, PackageSnapshot newSnap, boolean aiEnabled) {
        // 有 AI 章节时审计为 八（位于 AI 之后）；无 AI 时为 七（紧接破坏性变更）。
        sb.append(aiEnabled ? "## 八、审计摘要（基础）\n" : "## 七、审计摘要（基础）\n");
        sb.append("- 比对时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("- 老包标识：").append(nullToNA(oldSnap.getVersion())).append("\n");
        sb.append("- 新包标识：").append(nullToNA(newSnap.getVersion())).append("\n");
        sb.append("- AI 分析：").append(aiEnabled ? "已接入（含项目级上下文增强）" : "未接入，本报告不含 AI 章节。").append("\n");
    }

    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text, Path outFile) throws IOException {
        writeToFile(oldSnap, newSnap, r, stats, decompiled, text, (LibJarDiff.Result) null, outFile);
    }

    /** 写报告（含差异依赖 JAR 内部源码对比章节）。 */
    // NOSONAR: 多参重载为既有公开 API，签名不可变更（见任务约束）。
    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text, LibJarDiff.Result libJar,
                            Path outFile) throws IOException {
        String md = render(oldSnap, newSnap, r, stats, decompiled, text, libJar);
        writeMd(outFile, md);
    }

    /** 写报告（含 AI 智能分析章节与项目级上下文增强）。 */
    // NOSONAR: 多参重载为既有公开 API，签名不可变更（见任务约束）。
    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text,
                            StageASummary summary, List<FileAnalysis> fileAnalyses,
                            ProjectContext ctx, Path outFile) throws IOException {
        writeToFile(oldSnap, newSnap, r, stats, decompiled, text,
                summary, fileAnalyses, ctx, (LibJarDiff.Result) null, outFile);
    }

    /** 写报告（含 AI 智能分析章节、项目级上下文增强与差异依赖 JAR 内部源码对比章节）。 */
    // NOSONAR: 多参重载为既有公开 API，签名不可变更（见任务约束）。
    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap, // NOSONAR(S107) - 多参重载为既有公开 API
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text,
                            StageASummary summary, List<FileAnalysis> fileAnalyses,
                            ProjectContext ctx, LibJarDiff.Result libJar, Path outFile) throws IOException {
        String md = render(oldSnap, newSnap, r, stats, decompiled, text, summary, fileAnalyses, ctx, libJar);
        writeMd(outFile, md);
    }

    /** 将报告串联内容写入目标文件，必要时先创建父目录。 */
    private static void writeMd(Path outFile, String md) throws IOException {
        Path parent = outFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent); // 防御：输出目录可能不存在
        }
        try (Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            w.write(md);
        }
    }

    private static String mark(DiffStatus st) {
        switch (st) {
            case ADDED: return "新增 +";
            case DELETED: return "删除 -";
            case MODIFIED: return "修改 ~";
            default: return "未变 =";
        }
    }

    private static String nullToNA(String s) {
        return s == null ? "N/A" : s;
    }

    /**
     * 渲染代码差异为紧凑变更摘要：仅列变更行（不含上下文行），每块附行号区间（老 Lx-y → 新 La-b）
     * 与变更类型（修改/新增/删除），控制报告篇幅。复用 {@link DiffDigest} 的解析与渲染：
     * 仅展示发生变更的代码行而非整个文件，便于用户快速定位与核对。
     */
    private static String renderCompactDiff(String diffText) {
        if (diffText == null || diffText.isEmpty()) return "(无差异内容)";
        return DiffDigest.render(diffText);
    }
}