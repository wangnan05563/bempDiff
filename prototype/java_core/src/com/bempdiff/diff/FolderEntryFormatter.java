package com.bempdiff.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件夹对比树 / 报告的展示层公共格式化器。
 *
 * <p>集中维护 marker、子树标注、名称提取、属性摘要、行级 diff 切分，
 * 供 CLI（{@code Main}）与 Markdown 报告（{@code FolderReport}）复用，
 * 消除两处重复的真相源，避免图例 / 标注规则改动时双份漂移。
 */
public final class FolderEntryFormatter {

    private FolderEntryFormatter() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 状态标记；目录自身 SAME 但子树含差异时覆盖为 [*]。 */
    public static String marker(FolderDiff.FolderEntry e) {
        String m;
        switch (e.status) {
            case LEFT_ONLY: m = "[<]"; break;
            case RIGHT_ONLY: m = "[>]"; break;
            case MODIFIED: m = "[*]"; break;
            case TYPE_MISMATCH: m = "[!]"; break;
            default: m = "[=]";
        }
        if (subtreeDiff(e)) m = "[*]";
        return m;
    }

    /** 目录自身 SAME 但子树含差异。 */
    public static boolean subtreeDiff(FolderDiff.FolderEntry e) {
        return e.type == FolderDiff.EntryType.DIR
                && e.status == FolderDiff.FolderDiffStatus.SAME
                && FolderDiff.subtreeHasDiff(e);
    }

    /** 取 relPath 末段作为展示名。 */
    public static String name(FolderDiff.FolderEntry e) {
        return e.relPath.substring(e.relPath.lastIndexOf('/') + 1);
    }

    /** MODIFIED 条目的属性 / 内容变更摘要。 */
    public static String attrSummary(FolderDiff.FolderEntry e) {
        List<String> parts = new ArrayList<>();
        if (e.attrChanges.contains(FolderDiff.AttrChange.SIZE))
            parts.add("大小 " + FolderDiff.fmtSize(e.sizeLeft) + "→" + FolderDiff.fmtSize(e.sizeRight));
        if (e.attrChanges.contains(FolderDiff.AttrChange.MTIME))
            parts.add("修改时间 " + FolderDiff.fmtMtime(e.mtimeLeft) + "→" + FolderDiff.fmtMtime(e.mtimeRight));
        if (e.attrChanges.contains(FolderDiff.AttrChange.CONTENT)) parts.add("内容不同");
        if (e.attrChanges.contains(FolderDiff.AttrChange.TYPE)) parts.add("类型冲突(文件/目录)");
        return "[" + String.join(", ", parts) + "]";
    }

    /**
     * 行级 diff 拆行（供展示层逐行输出）。
     * 返回 null 表示无可用内容（null / 空 / 仅尾部空白），调用方据此跳过；
     * 已 {@code stripTrailing()} 去除整块尾部空白并按 {@code \n} 切分。
     */
    public static List<String> lineDiffLines(FolderDiff.FolderEntry e) {
        if (e.lineDiff == null || e.lineDiff.isEmpty()) return null;
        String ld = e.lineDiff.stripTrailing();
        if (ld.isEmpty()) return null;
        // 按 \n 或 \r\n 切分，避免 Windows 原生 CRLF 在中间行尾遗留 \r（进入控制台/```diff 块变脏）
        return Arrays.asList(ld.split("\r?\n"));
    }
}
