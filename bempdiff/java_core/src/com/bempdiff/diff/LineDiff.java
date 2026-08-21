package com.bempdiff.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 轻量行级 diff（LCS），输出带 +/-/空格 前缀的合并视图（单测友好，无第三方依赖）。
 * 与 Decompiler.simpleDiff 算法一致，但独立成公共工具，供前端文本 diff 复用，
 * 避免改动 class 反编译路径。
 */
public final class LineDiff {

    /**
     * LCS 动态规划单元格上限：超过则退化为「前缀/后缀公共行 + 中段整体替换」的线性 diff。
     * 防超大文件（如压缩 JS 美化后数万行）把 n×m int 表撑爆（20000×20000 ≈ 1.6GB → OOM，
     * 实测会静默走 fail 分支导致 AI 预估为 0、内容 diff 缺失）。
     */
    static final long MAX_LCS_CELLS = 5_000_000L;

    private LineDiff() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static List<String> lines(String s) {
        if (s == null) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\n", -1)));
    }

    /** 统一差异文本（默认规则，等价于旧行为）。 */
    public static String unified(List<String> a, List<String> b) {
        return unified(a, b, DiffRules.DEFAULT);
    }

    /** 统一差异文本（默认规则，等价于旧行为）。 */
    public static String unified(String a, String b) {
        return unified(lines(a), lines(b), DiffRules.DEFAULT);
    }

    /** 统一差异文本（P0-②：带忽略规则，字符串入参便捷方法）。 */
    public static String unified(String a, String b, DiffRules rules) {
        return unified(lines(a), lines(b), rules);
    }

    /** 统一差异文本（P0-②）：用忽略规则对行做归一化后匹配，输出仍为原始行内容。 */
    public static String unified(List<String> a, List<String> b, DiffRules rules) {
        int n = a.size();
        int m = b.size();
        // 超大输入：退化为线性 diff（前缀/后缀公共 + 中段整体替换），避免 LCS O(n×m) 内存爆炸
        if ((long) (n + 1) * (m + 1) > MAX_LCS_CELLS) {
            return linearDiff(a, b, rules);
        }
        // 归一化行仅用于匹配（决定两行是否视为相同）
        List<String> ak = normalizeLines(a, rules);
        List<String> bk = normalizeLines(b, rules);
        int[][] dp = computeLCS(ak, bk, n, m);
        return render(a, b, ak, bk, dp);
    }

    /**
     * 线性 diff（O(n+m)）：公共前缀行 + 中段整体替换 + 公共后缀行。
     * 超大文件可读性可接受（改动集中在文件头部/尾部时仍能精确呈现；中段整改时给出全量替换视图），
     * 且内存/耗时均有界——比 LCS 全表更稳健，是超大输入的兜底路径。
     */
    private static String linearDiff(List<String> a, List<String> b, DiffRules rules) {
        List<String> ak = normalizeLines(a, rules);
        List<String> bk = normalizeLines(b, rules);
        int n = a.size();
        int m = b.size();
        int i = 0; // 公共前缀行数
        while (i < n && i < m && ak.get(i).equals(bk.get(i))) i++;
        int j = 0; // 公共后缀行数（不越过前缀）
        while (j < n - i && j < m - i && ak.get(n - 1 - j).equals(bk.get(m - 1 - j))) j++;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < i; k++) sb.append("  ").append(a.get(k)).append("\n");
        for (int k = i; k < n - j; k++) sb.append("- ").append(a.get(k)).append("\n");
        for (int k = i; k < m - j; k++) sb.append("+ ").append(b.get(k)).append("\n");
        for (int k = m - j; k < m; k++) sb.append("  ").append(b.get(k)).append("\n");
        return sb.toString();
    }

    /** 按忽略规则归一化每一行（返回归一化副本，不影响原始行）。 */
    private static List<String> normalizeLines(List<String> src, DiffRules rules) {
        List<String> out = new ArrayList<>(src.size());
        for (String s : src) out.add(rules.normalize(s));
        return out;
    }

    /** 计算 LCS 动态规划表 dp[i][j]（用于回溯差异路径）。 */
    private static int[][] computeLCS(List<String> ak, List<String> bk, int n, int m) {
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                dp[i][j] = ak.get(i).equals(bk.get(j)) ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
        return dp;
    }

    /** 依据 LCS 表回溯，生成带 +/-/空格 前缀的合并视图（输出为原始行内容）。 */
    private static String render(List<String> a, List<String> b,
                                 List<String> ak, List<String> bk, int[][] dp) {
        StringBuilder sb = new StringBuilder();
        int n = a.size();
        int m = b.size();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (ak.get(i).equals(bk.get(j))) {
                sb.append("  ").append(a.get(i)).append("\n"); // 输出原始行
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                sb.append("- ").append(a.get(i)).append("\n");
                i++;
            } else {
                sb.append("+ ").append(b.get(j)).append("\n");
                j++;
            }
        }
        while (i < n) { sb.append("- ").append(a.get(i)).append("\n"); i++; }
        while (j < m) { sb.append("+ ").append(b.get(j)).append("\n"); j++; }
        return sb.toString();
    }
}