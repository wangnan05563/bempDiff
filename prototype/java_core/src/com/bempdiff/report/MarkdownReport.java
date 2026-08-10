package com.bempdiff.report;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.PackageSnapshot;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * 报告导出（T12，FR7）。对应 prototype: write_markdown_report。
 * 默认包含：差异统计 + 全量清单 + Top-K（默认 15，按风险排序）完整源码 diff + 破坏性变更清单（删除类）。
 * 说明：本移植版先实现"无 AI 章节"的基础报告（AI 章节待 core.ai 接入后补）。
 */
public final class MarkdownReport {

    private final int topK;

    public MarkdownReport(int topK) {
        this.topK = topK;
    }

    public String render(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                          DiffResult r, DiffStats stats,
                          Map<String, DecompiledUnit> decompiled) {
        StringBuilder sb = new StringBuilder();
        renderHeader(sb, oldSnap, newSnap);
        renderStats(sb, stats);
        renderFileTree(sb, r);
        renderDecompiledDiff(sb, decompiled);
        renderBreakingChanges(sb, r);
        renderFooter(sb, oldSnap, newSnap);
        return sb.toString();
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
        sb.append("## 三、反编译源码级差异（Top-").append(topK).append(" 修改/新增/删除类）\n");
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

    private void renderBreakingChanges(StringBuilder sb, DiffResult r) {
        sb.append("## 四、破坏性变更清单（删除类）\n");
        if (r.get(DiffStatus.DELETED).isEmpty()) {
            sb.append("- 无删除类。\n");
        } else {
            for (String k : r.get(DiffStatus.DELETED)) {
                sb.append("- `").append(k).append("`（潜在对外 API / 行为移除，需重点回归）\n");
            }
        }
        sb.append("\n");
    }

    private void renderFooter(StringBuilder sb, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        sb.append("## 五、审计摘要（基础）\n");
        sb.append("- 比对时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("- 老包标识：").append(nullToNA(oldSnap.getVersion())).append("\n");
        sb.append("- 新包标识：").append(nullToNA(newSnap.getVersion())).append("\n");
        sb.append("- AI 分析：未接入（core.ai 待补），本报告不含 AI 章节。\n");
    }

    public void writeToFile(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                            DiffResult r, DiffStats stats,
                            Map<String, DecompiledUnit> decompiled, Path outFile) throws IOException {
        String md = render(oldSnap, newSnap, r, stats, decompiled);
        Path parent = outFile.getParent();
        if (parent != null) Files.createDirectories(parent);  // 防御：输出目录可能不存在
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
