package com.bempdiff.ui;

/**
 * 差异视图单行数据模型（Beyond Compare 风格双栏对比）。
 * 每行包含左右两侧的行号、文本内容、以及差异类型。
 * 用于 DiffView 的 ListView 渲染。
 */
public final class DiffRow {

    /** 差异类型。 */
    public enum Type {
        /** 两侧相同（正常行，浅色背景）。 */
        UNCHANGED,
        /** 仅左侧有（删除行，红色背景）。 */
        DELETED,
        /** 仅右侧有（新增行，绿色背景）。 */
        ADDED,
        /** 两侧都有但内容不同（修改行，左右分别着色）。 */
        MODIFIED
    }

    private final Type type;
    private final int leftLineNo;       // 左侧行号（-1 表示空）
    private final int rightLineNo;      // 右侧行号（-1 表示空）
    private final String leftText;      // 左侧文本（null 表示空）
    private final String rightText;     // 右侧文本（null 表示空）

    public DiffRow(Type type, int leftLineNo, int rightLineNo, String leftText, String rightText) {
        this.type = type;
        this.leftLineNo = leftLineNo;
        this.rightLineNo = rightLineNo;
        this.leftText = leftText;
        this.rightText = rightText;
    }

    public Type getType() { return type; }
    public int getLeftLineNo() { return leftLineNo; }
    public int getRightLineNo() { return rightLineNo; }
    public String getLeftText() { return leftText; }
    public String getRightText() { return rightText; }

    /** 便捷工厂：相同行。 */
    public static DiffRow unchanged(int lineNo, String text) {
        return new DiffRow(Type.UNCHANGED, lineNo, lineNo, text, text);
    }

    /** 便捷工厂：删除行（仅左侧）。 */
    public static DiffRow deleted(int lineNo, String text) {
        return new DiffRow(Type.DELETED, lineNo, -1, text, null);
    }

    /** 便捷工厂：新增行（仅右侧）。 */
    public static DiffRow added(int lineNo, String text) {
        return new DiffRow(Type.ADDED, -1, lineNo, null, text);
    }

    @Override
    public String toString() {
        return String.format("DiffRow{%s L%d R%d}", type, leftLineNo, rightLineNo);
    }
}
