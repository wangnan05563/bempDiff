package com.bempdiff.ui;

import com.bempdiff.diff.LineDiff;
import java.util.ArrayList;
import java.util.List;

/**
 * 将两段文本的行级差异转换为 Beyond Compare 风格的 {@link DiffRow} 列表。
 *
 * <p>基于 {@link LineDiff} 的 LCS 算法，输出结构化的"左右对齐行"而非合并视图。
 * 每个DiffRow 明确标注左侧行号、右侧行号、两侧内容、以及差异类型。
 */
public final class DiffRowsBuilder {

    private DiffRowsBuilder() { /* 工具类 */ }

    /**
     * 将 oldText / newText 的行级差异转为双栏对齐的 DiffRow 列表。
     *
     * @param oldText 左侧（老版本）全文
     * @param newText 右侧（新版本）全文
     * @return 对齐后的行列表，按从上到下的视觉顺序排列
     */
    public static List<DiffRow> build(String oldText, String newText) {
        List<String> oldLines = LineDiff.lines(oldText);
        List<String> newLines = LineDiff.lines(newText);
        return buildFromLines(oldLines, newLines);
    }

    /**
     * 核心方法：基于 LCS 的双栏对齐算法。
     *
     * <p>与 {@link LineDiff#unified} 的区别：
     * <ul>
     *   <li>unified 输出单栏 +/- 前缀文本</li>
     *   <li>本方法输出结构化 DiffRow 列表，每行包含左侧行号+内容、右侧行号+内容、类型</li>
     * </ul>
     *
     * <p>算法：先算 LCS DP 表，再回溯生成对齐行。回溯时：
     * <ul>
     *   <li>a[i]==b[j] → UNCHANGED 行（两侧都有）</li>
     *   <li>a[i]!=b[j] 且 dp[i+1][j] > dp[i][j+1] → DELETED 行（仅左侧）</li>
     *   <li>否则 → ADDED 行（仅右侧）</li>
     * </ul>
     */
    static List<DiffRow> buildFromLines(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();

        // 边界情况：任一侧为空
        if (n == 0 && m == 0) return new ArrayList<>();
        if (n == 0) {
            List<DiffRow> rows = new ArrayList<>(m);
            for (int j = 0; j < m; j++) rows.add(DiffRow.added(j, b.get(j)));
            return rows;
        }
        if (m == 0) {
            List<DiffRow> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) rows.add(DiffRow.deleted(i, a.get(i)));
            return rows;
        }

        // === Phase 1: LCS DP 表 ===
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // === Phase 2: 回溯生成对齐行（反向收集后反转） ===
        // 使用 ArrayList 预估容量避免扩容
        List<DiffRow> temp = new ArrayList<>(n + m);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                // 相同行
                temp.add(new DiffRow(DiffRow.Type.UNCHANGED, i, j, a.get(i), b.get(j)));
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                // 仅左侧有（删除）
                temp.add(DiffRow.deleted(i, a.get(i)));
                i++;
            } else {
                // 仅右侧有（新增）
                temp.add(DiffRow.added(j, b.get(j)));
                j++;
            }
        }
        // 左侧剩余（全删除）
        while (i < n) {
            temp.add(DiffRow.deleted(i, a.get(i)));
            i++;
        }
        // 右侧剩余（全新增）
        while (j < m) {
            temp.add(DiffRow.added(j, b.get(j)));
            j++;
        }

        // 标记 MODIFIED 类型：相邻的 DELETED+ADDED 对可提升为 MODIFIED
        // （优化视觉效果：修改块用不同颜色区分于纯增删）
        return markModifiedBlocks(temp);
    }

    /**
     * 后处理：将连续的 [DELETED, ADDED] 对标记为 MODIFIED。
     *
     * <p>规则：如果一个删除行紧接一个新增行（无相同行间隔），则视为"一行被替换为另一行"，
     * 将两者类型都改为 MODIFIED。这使 UI 能用不同的颜色（如浅黄）区分"修改"和"纯增删"。
     */
    private static List<DiffRow> markModifiedBlocks(List<DiffRow> rows) {
        // 简单启发式：相邻的 DEL+ADD 配对改为 MODIFIED
        // 不做复杂的块匹配（如 Myers diff），保持轻量
        for (int k = 0; k < rows.size() - 1; k++) {
            DiffRow cur = rows.get(k);
            DiffRow next = rows.get(k + 1);
            if (cur.getType() == DiffRow.Type.DELETED && next.getType() == DiffRow.Type.ADDED) {
                // 提升为 MODIFIED
                rows.set(k, new DiffRow(DiffRow.Type.MODIFIED,
                        cur.getLeftLineNo(), next.getRightLineNo(),
                        cur.getLeftText(), next.getRightText()));
                rows.remove(k + 1);  // 合并为一行
                // 不递增 k，因为下一行已被消费
            }
        }
        return rows;
    }
}
