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

    private LineDiff() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static List<String> lines(String s) {
        if (s == null) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\n", -1)));
    }

    /** 统一差异文本：修改类完整 diff；新增/删除由调用方加提示头。 */
    public static String unified(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                dp[i][j] = a.get(i).equals(b.get(j)) ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
        StringBuilder sb = new StringBuilder();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                sb.append("  ").append(a.get(i)).append("\n");
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                sb.append("- ").append(a.get(i++)).append("\n");
            } else {
                sb.append("+ ").append(b.get(j++)).append("\n");
            }
        }
        while (i < n) sb.append("- ").append(a.get(i++)).append("\n");
        while (j < m) sb.append("+ ").append(b.get(j++)).append("\n");
        return sb.toString();
    }

    public static String unified(String a, String b) {
        return unified(lines(a), lines(b));
    }
}
