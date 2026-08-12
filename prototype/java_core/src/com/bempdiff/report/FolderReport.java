package com.bempdiff.report;

import com.bempdiff.diff.FolderDiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹对比 Markdown 报告渲染器。
 * 结构：标题 → 一、差异汇总 → 二、差异树（展开，标注状态/属性变更）→ 三、修改文件内容差异（行级 diff）。
 */
public final class FolderReport {

    private FolderReport() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static String render(FolderDiff.FolderDiffResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 文件夹对比报告\n\n");
        sb.append("- 左侧目录: `").append(r.leftRoot).append("`\n");
        sb.append("- 右侧目录: `").append(r.rightRoot).append("`\n\n");

        sb.append("## 一、差异汇总\n\n");
        sb.append("| 维度 | 数量 |\n");
        sb.append("|---|---|\n");
        sb.append("| 仅左侧存在 (LEFT_ONLY) | ").append(r.summary.leftOnly).append(" |\n");
        sb.append("| 仅右侧存在 (RIGHT_ONLY) | ").append(r.summary.rightOnly).append(" |\n");
        sb.append("| 两侧不同 (MODIFIED) | ").append(r.summary.modified).append(" |\n");
        sb.append("| 　├ 内容不同 | ").append(r.summary.contentChanged).append(" |\n");
        sb.append("| 　└ 仅属性不同 | ").append(r.summary.attrOnlyChanged).append(" |\n");
        sb.append("| 完全相同 (SAME) | ").append(r.summary.same).append(" |\n");
        sb.append("| 类型冲突 (TYPE_MISMATCH) | ").append(r.summary.typeMismatch).append(" |\n");
        sb.append("| 扫描文件 / 目录 | ").append(r.summary.scannedFiles).append(" / ")
                .append(r.summary.scannedDirs).append(" |\n");
        sb.append("| 读取错误（已隔离） | ").append(r.summary.errors).append(" |\n\n");

        sb.append("## 二、差异树（展开视图）\n\n");
        sb.append("图例：`[<]` 仅左侧  `[>]` 仅右侧  `[*]` 两侧不同  `[!]` 类型冲突  `[=]` 相同\n\n");
        appendTree(sb, r.roots, "");

        sb.append("\n## 三、修改文件内容差异（行级）\n\n");
        boolean any = false;
        for (FolderDiff.FolderEntry e : r.flat.values()) {
            if (e.lineDiff != null && !e.lineDiff.isEmpty()) {
                any = true;
                sb.append("### ").append(e.relPath).append("\n\n");
                String ld = e.lineDiff;
                if (ld.endsWith("\n")) ld = ld.substring(0, ld.length() - 1);
                sb.append("```diff\n").append(ld).append("```\n\n");
            }
        }
        if (!any) {
            sb.append("_无文本文件内容差异（或文本文件内容未变，仅属性变化）。_\n");
        }

        if (!r.errors.isEmpty()) {
            sb.append("\n## 四、读取错误明细（已隔离，不影响其余比对）\n\n");
            for (String err : r.errors) {
                sb.append("- ").append(err).append("\n");
            }
        }
        return sb.toString();
    }

    private static void appendTree(StringBuilder sb, List<FolderDiff.FolderEntry> nodes, String indent) {
        for (FolderDiff.FolderEntry e : nodes) {
            String marker;
            switch (e.status) {
                case LEFT_ONLY: marker = "[<]"; break;
                case RIGHT_ONLY: marker = "[>]"; break;
                case MODIFIED: marker = "[*]"; break;
                case TYPE_MISMATCH: marker = "[!]"; break;
                default: marker = "[=]";
            }
            boolean subtreeDiff = e.type == FolderDiff.EntryType.DIR
                    && e.status == FolderDiff.FolderDiffStatus.SAME && FolderDiff.subtreeHasDiff(e);
            if (subtreeDiff) marker = "[*]";
            String name = e.relPath.substring(e.relPath.lastIndexOf('/') + 1);
            sb.append(indent).append("- ").append(marker).append(' ').append(name);
            if (e.type == FolderDiff.EntryType.DIR) sb.append('/');
            if (e.status == FolderDiff.FolderDiffStatus.MODIFIED) {
                sb.append(' ').append(attrSummary(e));
            } else if (subtreeDiff) {
                sb.append(" (子树含差异)");
            }
            sb.append('\n');
            if (e.children != null && !e.children.isEmpty()) {
                appendTree(sb, e.children, indent + "  ");
            }
        }
    }

    private static String attrSummary(FolderDiff.FolderEntry e) {
        List<String> parts = new ArrayList<>();
        if (e.attrChanges.contains(FolderDiff.AttrChange.SIZE)) {
            parts.add("大小 " + FolderDiff.fmtSize(e.sizeLeft) + "→" + FolderDiff.fmtSize(e.sizeRight));
        }
        if (e.attrChanges.contains(FolderDiff.AttrChange.MTIME)) {
            parts.add("修改时间 " + FolderDiff.fmtMtime(e.mtimeLeft) + "→" + FolderDiff.fmtMtime(e.mtimeRight));
        }
        if (e.attrChanges.contains(FolderDiff.AttrChange.CONTENT)) parts.add("内容不同");
        if (e.attrChanges.contains(FolderDiff.AttrChange.TYPE)) parts.add("类型冲突(文件/目录)");
        return "[" + String.join(", ", parts) + "]";
    }

    public static void writeToFile(FolderDiff.FolderDiffResult r, Path out) throws IOException {
        Files.writeString(out, render(r));
    }

    /** 便捷入口：给定两个目录直接生成报告字符串（供 GUI 预览复用）。 */
    public static String render(Path left, Path right) throws IOException {
        return render(FolderDiff.compare(left, right, FolderDiff.Options.defaults()));
    }
}
