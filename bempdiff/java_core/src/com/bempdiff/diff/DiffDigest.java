package com.bempdiff.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * unified diff 的「变更摘要」解析与渲染（需求：AI 报告中仅列变更行，标注行号与类型）。
 *
 * <p>目的：AI 阶段B prompt 不再塞整份 diffText（上下文行 + 完整文件会造成篇幅过大、token 浪费），
 * 改为仅输出发生变更的行，每处带：</p>
 * <ul>
 *   <li>类型标注：修改(MODIFIED) / 新增(ADDED) / 删除(DELETED)——相邻删除+新增合并为「修改」；</li>
 *   <li>行号区间：老侧行号(起始-结束) 与 新侧行号(起始-结束)，便于模型与用户定位；</li>
 *   <li>变更行内容（去 +/- 前缀，单行截断，块内行数有界）。</li>
 * </ul>
 *
 * <p>渲染格式（```diff 块内，-/+ 前缀保留以复用 diff 语法高亮）：</p>
 * <pre>
 * - [修改] 老 L12-15 → 新 L12-14：
 * -   旧行内容
 * +   新行内容
 * - [新增] 新 L38：
 * +   新增行内容
 * - [删除] 老 L20-21：
 * -   被删行内容
 * </pre>
 *
 * <p>有界性：单块最多 {@link #LINES_PER_CHANGE} 行，全局最多 {@link #CHANGES_MAX} 块，
 * 行内容单行截断 {@link #LINE_CHAR_CAP} 字符；超大 diff 的摘要仍远小于
 * {@code PromptBuilders.STAGE_B_DIFF_CHAR_CAP}，成本预估与真实请求同步有界。</p>
 */
public final class DiffDigest {

    /** 单个变更块最多渲染的行数（含老行 + 新行；超出省略并标注剩余行数）。 */
    public static final int LINES_PER_CHANGE = 40;
    /** 全局最多渲染的变更块数（超出省略并标注总块数）。 */
    public static final int CHANGES_MAX = 120;
    /** 单行变更内容截断长度（字符）。 */
    public static final int LINE_CHAR_CAP = 120;

    /** 新增类型标注字面量（多处复用的状态码，统一定为常量避免散落字符串）。 */
    private static final String TYPE_ADDED = "ADDED";

    /** hunk 头正则：@@ -oldStart[,oldCount] +newStart[,newCount] @@ */
    private static final Pattern HUNK = Pattern.compile(
            "@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@");

    private DiffDigest() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 单个变更块。 */
    public static final class Change {
        /** MODIFIED / ADDED / DELETED。 */
        public final String type;
        public final int oldStart;
        public final int oldEnd;   // 老侧行号区间；无老行时为 0
        public final int newStart;
        public final int newEnd;   // 新侧行号区间；无新行时为 0
        public final List<String> oldLines = new ArrayList<>();
        public final List<String> newLines = new ArrayList<>();

        public Change(String type, int oldStart, int oldEnd, int newStart, int newEnd) {
            this.type = type;
            this.oldStart = oldStart;
            this.oldEnd = oldEnd;
            this.newStart = newStart;
            this.newEnd = newEnd;
        }

        /** 变更行总数（老行 + 新行）。 */
        public int totalLines() { return oldLines.size() + newLines.size(); }
    }

    /**
     * 解析 unified diff 文本为变更块列表。
     * 支持标准 {@code @@ -a,b +c,d @@} hunk 头；缺头（如手造假 diff）时按出现顺序从第 1 行连续计数。
     * 上下文行/文件头/换行符说明均跳过（仅保留变更行与行号）。
     */
    public static List<Change> parse(String diff) {
        List<Change> out = new ArrayList<>();
        if (diff == null || diff.isEmpty()) return out;
        String[] lines = diff.split("\n", -1);
        int oldLine = 1;
        int newLine = 1;
        List<Integer> oldNums = new ArrayList<>();
        List<String> oldText = new ArrayList<>();
        List<Integer> newNums = new ArrayList<>();
        List<String> newText = new ArrayList<>();
        for (String raw : lines) {
            if (raw.startsWith("@@ ")) {
                flush(out, oldNums, oldText, newNums, newText);
                int[] h = parseHunk(raw);
                oldLine = h[0];
                newLine = h[2];
                continue;
            }
            if (!raw.startsWith("--- ") && !raw.startsWith("+++ ") && !raw.startsWith("\\ No newline")) {
                if (raw.startsWith("-")) {
                    oldNums.add(oldLine++);
                    oldText.add(raw.substring(1));
                } else if (raw.startsWith("+")) {
                    newNums.add(newLine++);
                    newText.add(raw.substring(1));
                } else {
                    // 上下文行：触发块结算，行号两侧各 +1
                    flush(out, oldNums, oldText, newNums, newText);
                    oldLine++;
                    newLine++;
                }
            }
        }
        flush(out, oldNums, oldText, newNums, newText);
        return out;
    }

    /** 渲染为紧凑变更摘要（带行号与类型标注，供 AI prompt 与人工核对）。 */
    public static String render(String diff) {
        List<Change> changes = parse(diff);
        if (changes.isEmpty()) return "(无内容级差异行)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Change c : changes) {
            if (shown >= CHANGES_MAX) {
                sb.append("... (变更块过多，已省略：共 ").append(changes.size())
                  .append(" 块，仅显示前 ").append(CHANGES_MAX).append(" 块)\n");
                break;
            }
            shown++;
            sb.append("- [").append(typeLabel(c.type)).append("] ").append(location(c)).append("：\n");
            // M2 修复：old/new 分侧独立计数（各最多 LINES_PER_CHANGE 行）。原实现合并计数，
            // 当 old 恰满 40 行时 new 行一行都不显示（如删 40 增 40 → 新增内容全丢），AI prompt 信息不全。
            int no = 0;
            for (String ol : c.oldLines) {
                if (no >= LINES_PER_CHANGE) {
                    sb.append("    … 老侧其余 ").append(c.oldLines.size() - no).append(" 行省略\n");
                    break;
                }
                sb.append("-   ").append(cap(ol)).append("\n");
                no++;
            }
            int nn = 0;
            for (String nl : c.newLines) {
                if (nn >= LINES_PER_CHANGE) {
                    sb.append("    … 新侧其余 ").append(c.newLines.size() - nn).append(" 行省略\n");
                    break;
                }
                sb.append("+   ").append(cap(nl)).append("\n");
                nn++;
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ----------------------------- 内部 -----------------------------

    private static void flush(List<Change> out, List<Integer> oldNums, List<String> oldText,
                              List<Integer> newNums, List<String> newText) {
        if (oldNums.isEmpty() && newNums.isEmpty()) return;
        String type;
        if (!oldNums.isEmpty() && !newNums.isEmpty()) {
            type = "MODIFIED";
        } else if (oldNums.isEmpty()) {
            type = TYPE_ADDED;
        } else {
            type = "DELETED";
        }
        Change c = new Change(type,
                first(oldNums), last(oldNums), first(newNums), last(newNums));
        c.oldLines.addAll(oldText);
        c.newLines.addAll(newText);
        oldNums.clear(); oldText.clear();
        newNums.clear(); newText.clear();
        out.add(c);
    }

    /** 解析 hunk 头，返回 [oldStart, oldCount, newStart, newCount]（count 缺省为 1）。 */
    static int[] parseHunk(String line) {
        Matcher m = HUNK.matcher(line);
        if (!m.find()) return new int[]{1, 1, 1, 1};
        try {
            int oldStart = Integer.parseInt(m.group(1));
            int oldCount = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
            int newStart = Integer.parseInt(m.group(3));
            int newCount = m.group(4) != null ? Integer.parseInt(m.group(4)) : 1;
            return new int[]{oldStart, oldCount, newStart, newCount};
        } catch (NumberFormatException e) {
            // M3 修复：畸形行号（超 int / 非数字）时回退默认，避免冒泡到 render → AI 阶段B 整体崩溃。
            return new int[]{1, 1, 1, 1};
        }
    }

    private static String typeLabel(String t) {
        switch (t) {
            case TYPE_ADDED: return "新增";
            case "DELETED": return "删除";
            default: return "修改";
        }
    }

    /** 行号区间标注：MODIFIED→「老 Lx-y → 新 La-b」；ADDED→「新 Lx」；DELETED→「老 Lx-y」。 */
    private static String location(Change c) {
        String oldLoc = lineRange(c.oldStart, c.oldEnd);
        String newLoc = lineRange(c.newStart, c.newEnd);
        if ("MODIFIED".equals(c.type)) {
            return (oldLoc == null ? "" : "老 " + oldLoc) + (oldLoc != null && newLoc != null ? " → " : "")
                    + (newLoc == null ? "" : "新 " + newLoc);
        }
        if (TYPE_ADDED.equals(c.type)) return "新 " + newLoc;
        return "老 " + oldLoc;
    }

    /** 行号区间字面量：单行 → "Lx"，区间 → "Lx-y"，起始 ≤0（无行）返回 null。 */
    private static String lineRange(int start, int end) {
        if (start <= 0) return null;
        return (start == end) ? "L" + start : "L" + start + "-" + end;
    }

    private static int first(List<Integer> nums) { return nums.isEmpty() ? 0 : nums.get(0); }

    private static int last(List<Integer> nums) { return nums.isEmpty() ? 0 : nums.get(nums.size() - 1); }

    private static String cap(String s) {
        if (s == null) return "";
        if (s.length() <= LINE_CHAR_CAP) return s;
        // L5 修复：按 Unicode 码点截断，避免切断 UTF-16 代理对产生乱码字符。
        int count = 0;
        int i = 0;
        while (i < s.length() && count < LINE_CHAR_CAP) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            count++;
        }
        return s.substring(0, i) + "…";
    }
}
