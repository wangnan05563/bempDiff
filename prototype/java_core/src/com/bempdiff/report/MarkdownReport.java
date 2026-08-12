package com.bempdiff.report;

import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.FileRisk;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.diff.DiffResult;
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

    private final int topK;

    public MarkdownReport(int topK) {
        this.topK = topK;
    }

    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, oldSnap, newSnap);
        renderStats(sb, stats);
        renderTypeBreakdown(sb, r, oldSnap, newSnap);
        renderFileTree(sb, r);
        renderDecompiledDiff(sb, decompiled);
        renderTextDiff(sb, text);
        renderBreakingChanges(sb, r);
        renderFooter(sb, oldSnap, newSnap, false);
        return sb.toString();
    }

    /**
     * 渲染（含 AI 智能分析章节与项目级上下文增强）。无 AI 数据时 summary/fileAnalyses 传 null，
     * 未启用上下文增强时 ctx 传 null —— 均向后兼容原 6 参 render。
     */
    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled,
                          Map<String, DecompiledUnit> text,
                          StageASummary summary, List<FileAnalysis> fileAnalyses, ProjectContext ctx) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, oldSnap, newSnap);
        renderStats(sb, stats);
        renderTypeBreakdown(sb, r, oldSnap, newSnap);
        renderFileTree(sb, r);
        renderDecompiledDiff(sb, decompiled);
        renderTextDiff(sb, text);
        renderBreakingChanges(sb, r);
        appendAiSection(sb, summary, fileAnalyses, ctx);
        renderFooter(sb, oldSnap, newSnap, summary != null);
        return sb.toString();
    }

    /** 追加 AI 智能分析章节：项目级上下文摘要 + 阶段A 概览（含上下文影响）+ 阶段B 逐文件（含上下文影响）。 */
    private void appendAiSection(StringBuilder sb, StageASummary summary,
                                 List<FileAnalysis> fileAnalyses, ProjectContext ctx) {
        sb.append("## 七、AI 智能分析（两阶段 / 项目级上下文增强）\n");
        if (ctx != null && !ctx.isEmpty()) {
            sb.append("### 项目级上下文（分析依据）\n");
            sb.append("- 构建系统：").append(nullToNA(ctx.getBuildSystem())).append("\n");
            sb.append("- 模块：").append(join(ctx.getModules())).append("\n");
            sb.append("- 核心依赖：").append(join(ctx.getDependencies())).append("\n");
            sb.append("- 配置文件：").append(join(ctx.getConfigFiles())).append("\n");
            if (ctx.getSummary() != null && !ctx.getSummary().isEmpty()) {
                sb.append("- 架构简述：").append(ctx.getSummary()).append("\n");
            }
            sb.append("\n");
        }
        if (summary != null) {
            sb.append("### 阶段A 概览\n");
            sb.append("- 整体风险：**").append(nullToNA(summary.getOverallRisk())).append("**\n");
            sb.append("- 影响范围：").append(nullToNA(summary.getImpactScope())).append("\n");
            if (summary.getContextInfluence() != null && !summary.getContextInfluence().isEmpty()) {
                sb.append("- **项目上下文影响**：").append(summary.getContextInfluence()).append("\n");
            }
            if (summary.getTestThemes() != null && !summary.getTestThemes().isEmpty()) {
                sb.append("- 测试主题：").append(String.join("；", summary.getTestThemes())).append("\n");
            }
            if (summary.getFileRisks() != null && !summary.getFileRisks().isEmpty()) {
                sb.append("- 每文件初评风险：\n");
                for (FileRisk fr : summary.getFileRisks()) {
                    sb.append("  - `").append(nullToNA(fr.getKey())).append("`：**")
                            .append(nullToNA(fr.getRisk())).append("** — ").append(nullToNA(fr.getOneLineReason())).append("\n");
                }
            }
            sb.append("\n");
        }
        if (fileAnalyses != null && !fileAnalyses.isEmpty()) {
            sb.append("### 阶段B 逐文件深读\n");
            for (FileAnalysis fa : fileAnalyses) {
                sb.append("#### ").append(nullToNA(fa.getKey())).append("\n");
                sb.append("- 风险：**").append(nullToNA(fa.getRisk())).append("**\n");
                sb.append("- 意图：").append(nullToNA(fa.getIntent())).append("\n");
                sb.append("- 影响：").append(nullToNA(fa.getImpact())).append("\n");
                if (fa.getContextInfluence() != null && !fa.getContextInfluence().isEmpty()) {
                    sb.append("- **项目上下文影响**：").append(fa.getContextInfluence()).append("\n");
                }
                if (fa.getTestPoints() != null && !fa.getTestPoints().isEmpty()) {
                    sb.append("- 测试要点：\n");
                    for (String tp : fa.getTestPoints()) {
                        sb.append("  - ").append(tp).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        if ((ctx == null || ctx.isEmpty()) && summary == null) {
            sb.append("- 未启用 AI 分析 / 项目级上下文增强。\n");
        }
        sb.append("\n");
    }

    private static String join(List<String> l) {
        if (l == null || l.isEmpty()) return "（无）";
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
        sb.append("> 共 ").append(decompiled.size()).append(" 个类已反编译（本处仅展示前 Top-K 条完整 diff）。\n\n");
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
                sb.append("```diff\n").append(u.getDiffText() == null ? "" : u.getDiffText()).append("\n```\n\n");
                shown++;
            }
        }
        if (decompiled.size() > topK) {
            sb.append("> 另有 ").append(decompiled.size() - topK).append(" 个修改类未展开（Top-K 限制）。\n\n");
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
                if (e == null) e = newSnap.getEntries().get(k);
                FileClass fc = (e != null) ? e.getFileClass() : FileClass.OTHER;
                String cat = fc.categoryLabel();
                long[] c = counts.computeIfAbsent(cat, x -> new long[3]);
                if (st == DiffStatus.ADDED) c[0]++;
                else if (st == DiffStatus.DELETED) c[1]++;
                else c[2]++;
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

    /** 文本类文件内容差异（配置文件 XML/Properties、JSP、前端 JS/HTML/CSS）：压缩/单行资源已
     *  美化或换行归一后做内容级逐行 diff，使改动可读（FR4.4 增强 + 本需求扩展）。 */
    private void renderTextDiff(StringBuilder sb, Map<String, DecompiledUnit> text) {
        sb.append("## 四、文本类文件内容差异（Top-").append(topK)
          .append(" 修改/新增/删除 配置文件/JSP/JS/HTML/CSS）\n");
        if (text == null || text.isEmpty()) {
            sb.append("- 无文本类文件（配置文件/JSP/前端源码）变动。\n\n");
            return;
        }
        sb.append("> 共 ").append(text.size()).append(" 个文本类文件已做内容级逐行 diff")
          .append("（本处仅展示前 Top-K 条完整 diff）。\n\n");
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
                sb.append("```diff\n").append(u.getDiffText() == null ? "" : u.getDiffText()).append("\n```\n\n");
                shown++;
            }
        }
        if (text.size() > topK) {
            sb.append("> 另有 ").append(text.size() - topK).append(" 个文本类文件未展开（Top-K 限制）。\n\n");
        }
    }

    private void renderBreakingChanges(StringBuilder sb, DiffResult r) {
        sb.append("## 五、破坏性变更清单（删除类/删除前端资源）\n");
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
        sb.append("## 六、审计摘要（基础）\n");
        sb.append("- 比对时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("- 老包标识：").append(nullToNA(oldSnap.getVersion())).append("\n");
        sb.append("- 新包标识：").append(nullToNA(newSnap.getVersion())).append("\n");
        sb.append("- AI 分析：").append(aiEnabled ? "已接入（含项目级上下文增强）" : "未接入，本报告不含 AI 章节。").append("\n");
    }

    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text, Path outFile) throws IOException {
        String md = render(oldSnap, newSnap, r, stats, decompiled, text);
        Path parent = outFile.getParent();
        if (parent != null) Files.createDirectories(parent);  // 防御：输出目录可能不存在
        try (Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            w.write(md);
        }
    }

    /** 写报告（含 AI 智能分析章节与项目级上下文增强）。 */
    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled,
                            Map<String, DecompiledUnit> text,
                            StageASummary summary, List<FileAnalysis> fileAnalyses,
                            ProjectContext ctx, Path outFile) throws IOException {
        String md = render(oldSnap, newSnap, r, stats, decompiled, text, summary, fileAnalyses, ctx);
        Path parent = outFile.getParent();
        if (parent != null) Files.createDirectories(parent);
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
}
