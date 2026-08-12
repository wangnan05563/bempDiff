package com.bempdiff.ui;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Beyond Compare 风格双栏差异对比视图。
 *
 * <p>布局结构（从外到内）：
 * <pre>
 *   BorderPane
 *   ├── Top    : HBox [左侧文件路径Label | 右侧文件路径Label]
 *   └── Center : SplitPane
 *               ├── 左侧面板 : HBox [行号 gutter | 代码 ListView]
 *               └── 右侧面板 : HBox [行号 gutter | 代码 ListView]
 * </pre>
 *
 * <p>特性：
 * <ul>
 *   <li>每行按 DiffRow.Type 着色：删除红底、新增绿底、修改左右分别着色、相同浅灰</li>
 *   <li>等宽字体 + 行号 gutter</li>
 *   <li>左右 ListView 滚动同步</li>
 *   <li>文件路径标题栏显示对比双方路径</li>
 * </ul>
 */
public final class DiffView {

    private DiffView() { /* 工具类 */ }

    /** 颜色常量（参考 Beyond Compare 配色方案）。 */
    static final Color COLOR_DELETED_BG = Color.rgb(255, 220, 220);     // 浅红 - 删除行
    static final Color COLOR_ADDED_BG   = Color.rgb(220, 255, 220);     // 浅绿 - 新增行
    static final Color COLOR_UNCHANGED_BG = Color.rgb(252, 252, 252);   // 近白 - 相同行
    static final Color COLOR_DELETED_FG = Color.rgb(180, 30, 30);       // 深红 - 删除文字
    static final Color COLOR_ADDED_FG   = Color.rgb(30, 130, 30);       // 深绿 - 新增文字
    static final Color COLOR_UNCHANGED_FG = Color.rgb(60, 60, 60);      // 深灰 - 正常文字
    static final Color COLOR_GUTTER_BG   = Color.rgb(245, 245, 245);    // 行号区背景
    static final Color COLOR_GUTTER_FG   = Color.rgb(140, 140, 140);    // 行号文字
    static final Color COLOR_HEADER_BG   = Color.rgb(240, 240, 240);    // 标题栏背景
    static final String MONO_FONT = "Consolas, 'Courier New', monospace";

    /**
     * 创建完整的差异对比视图（含标题栏 + 双栏代码 + 自动同步滚动）。
     *
     * @param leftPath  左侧文件路径（显示在标题栏）
     * @param rightPath 右侧文件路径（显示在标题栏）
     * @param rows      差异行数据列表（由 DiffRowsBuilder 生成）
     * @return 完整的 BorderPane 可直接放入 Tab 或其他容器
     */
    public static BorderPane create(String leftPath, String rightPath, List<DiffRow> rows) {
        BorderPane root = new BorderPane();

        // ---- 中央：双栏 SplitPane ----
        SplitPane split = new SplitPane();
        split.setDividerPositions(0.5);

        HBox leftPanel = buildSidePanel(rows, true);
        HBox rightPanel = buildSidePanel(rows, false);
        split.getItems().addAll(leftPanel, rightPanel);

        // ---- 顶部：差异导航工具栏 + 文件路径标题栏 ----
        ListView<DiffRow> leftList = extractListView(leftPanel);
        ListView<DiffRow> rightList = extractListView(rightPanel);
        HBox navBar = buildNavBar(rows, leftList, rightList);
        HBox header = buildHeader(leftPath, rightPath);
        VBox top = new VBox(navBar, header);
        root.setTop(top);

        root.setCenter(split);

        // ---- 自动绑定左右 ListView 同步滚动 ----
        bindScrollSyncAuto(leftPanel, rightPanel);

        // ---- 键盘快捷键：F3 下一处差异 / Shift+F3 上一处差异 / Ctrl+↓ Ctrl+↑ ----
        bindNavKeys(root, navBar);

        return root;
    }

    /**
     * 绑定差异导航键盘快捷键（需在 scene 就绪后挂到 scene 上才可靠触发）。
     *
     * <ul>
     *   <li>F3            → 下一处差异（等同「下一行」按钮）</li>
     *   <li>Shift+F3      → 上一处差异（等同「上一行」按钮）</li>
     *   <li>Ctrl+↓ / Ctrl+↑ → 下一处 / 上一处差异</li>
     * </ul>
     */
    private static void bindNavKeys(BorderPane root, HBox navBar) {
        if (navBar.getChildren().size() < 2) return;
        Button prevBtn = null;
        Button nextBtn = null;
        for (Node n : navBar.getChildren()) {
            if (n instanceof Button) {
                if (prevBtn == null) prevBtn = (Button) n;   // 第一个按钮：上一行
                else if (nextBtn == null) nextBtn = (Button) n; // 第二个按钮：下一行
            }
        }
        if (prevBtn == null || nextBtn == null) return;
        final Button prev = prevBtn, next = nextBtn;
        root.sceneProperty().addListener((obs, oldS, newS) -> {
            if (newS == null) return;
            newS.setOnKeyPressed(ke -> {
                if (ke.getCode() == KeyCode.F3) {
                    (ke.isShiftDown() ? prev : next).fire();
                    ke.consume();
                } else if (ke.getCode() == KeyCode.DOWN && ke.isControlDown()) {
                    next.fire();
                    ke.consume();
                } else if (ke.getCode() == KeyCode.UP && ke.isControlDown()) {
                    prev.fire();
                    ke.consume();
                }
            });
        });
    }

    /**
     * 构建差异导航工具栏：「上一行」「下一行」按钮 + 差异计数。
     *
     * <p>收集所有「差异行」索引（type != UNCHANGED），点击按钮在两栏 ListView 间
     * 跳转到上一个/下一个差异行（scrollTo + select 同步高亮），便于在多处差异间
     * 快速定位。计数标签显示「差异 X/Y」，无差异时显示「无差异」。
     */
    private static HBox buildNavBar(List<DiffRow> rows, ListView<DiffRow> leftList, ListView<DiffRow> rightList) {
        // 收集所有差异行索引
        List<Integer> diffIdx = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getType() != DiffRow.Type.UNCHANGED) diffIdx.add(i);
        }
        final int[] ptr = {-1}; // 当前所处差异在 diffIdx 中的位置；-1 表示尚未定位

        Label counter = new Label(diffIdx.isEmpty() ? "无差异" : "差异 0/" + diffIdx.size());
        counter.setStyle("-fx-font-size: 12px; -fx-text-fill: #555; -fx-padding: 4 8;");

        Button prev = new Button("◀ 上一行");
        Button next = new Button("下一行 ▶");
        for (Button b : new Button[]{prev, next}) {
            b.getStyleClass().addAll("btn", "btn-default");
            b.setStyle(b.getStyle() + " -fx-font-size: 12px; -fx-padding: 3 10;");
        }

        Runnable updateCounter = () -> counter.setText(
                diffIdx.isEmpty() ? "无差异" : "差异 " + (ptr[0] + 1) + "/" + diffIdx.size());

        final Runnable[] scrollToCur = new Runnable[1];
        scrollToCur[0] = () -> {
            if (ptr[0] < 0 || ptr[0] >= diffIdx.size()) return;
            int idx = diffIdx.get(ptr[0]);
            if (leftList != null) {
                leftList.getSelectionModel().clearAndSelect(idx);
                leftList.scrollTo(idx);
            }
            if (rightList != null) {
                rightList.getSelectionModel().clearAndSelect(idx);
                rightList.scrollTo(idx);
            }
            updateCounter.run();
        };

        prev.setOnAction(e -> {
            if (diffIdx.isEmpty()) return;
            ptr[0] = (ptr[0] <= 0) ? 0 : ptr[0] - 1; // 已到第一个则停住
            scrollToCur[0].run();
        });
        next.setOnAction(e -> {
            if (diffIdx.isEmpty()) return;
            ptr[0] = (ptr[0] >= diffIdx.size() - 1) ? diffIdx.size() - 1 : ptr[0] + 1; // 已到最后一个则停住
            scrollToCur[0].run();
        });

        HBox nav = new HBox(8, prev, next, counter);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(4, 8, 4, 8));
        nav.setStyle("-fx-background-color: #" + toHex(COLOR_HEADER_BG)
                + "; -fx-border-color: #ddd; -fx-border-width: 0 0 1 0;");
        return nav;
    }

    /** 构建顶部标题栏（左右文件路径）。 */
    private static HBox buildHeader(String leftPath, String rightPath) {
        Label leftLabel = new Label(truncatePath(leftPath));
        leftLabel.setStyle(buildHeaderStyle(true));
        leftLabel.setPadding(new Insets(4, 8, 4, 8));
        leftLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftLabel, Priority.ALWAYS);

        Label rightLabel = new Label(truncatePath(rightPath));
        rightLabel.setStyle(buildHeaderStyle(false));
        rightLabel.setPadding(new Insets(4, 8, 4, 8));
        rightLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(rightLabel, Priority.ALWAYS);

        // 中间分隔线
        Separator sep = new Separator();
        sep.setPrefWidth(1);

        HBox header = new HBox(leftLabel, sep, rightLabel);
        header.setStyle("-fx-background-color: #" + toHex(COLOR_HEADER_BG) + ";");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** 构建单侧面板（行号 gutter + 代码 ListView）。
     *
     * <p>重要：每个 ListView 必须持有独立的 ObservableList 副本。
     * JavaFX ListView 的虚拟化机制（cell 回收/复用）在多个 ListView 共享
     * 同一 items 列表时会产生冲突，导致滚动时一侧白板或渲染崩溃。
     */
    private static HBox buildSidePanel(List<DiffRow> rows, boolean isLeft) {
        // 独立副本：避免两个 ListView 共享同一 ObservableList 导致虚拟化冲突
        ListView<DiffRow> codeList = new ListView<>();
        codeList.setItems(javafx.collections.FXCollections.observableArrayList(new ArrayList<>(rows)));
        codeList.setCellFactory(list -> new DiffCell(isLeft));

        // 等宽字体、无边框、紧凑行高、明确背景色（防止透明穿透）
        codeList.setStyle("-fx-font-family: " + MONO_FONT + "; "
                + "-fx-font-size: 13px; "
                + "-fx-border-width: 0; "
                + "-fx-background-insets: 0; "
                + "-fx-padding: 0; "
                + "-fx-background-color: #" + toHex(COLOR_UNCHANGED_BG) + ";");

        // 固定行高以对齐两侧
        codeList.setFixedCellSize(22);

        HBox.setHgrow(codeList, Priority.ALWAYS);

        HBox panel = new HBox(codeList);
        panel.setFillHeight(true);
        return panel;
    }

    /**
     * 从 HBox 面板中提取 ListView（buildSidePanel 把 ListView 作为第一个子节点）。
     */
    private static ListView<DiffRow> extractListView(HBox panel) {
        for (Node child : panel.getChildren()) {
            if (child instanceof ListView) {
                @SuppressWarnings("unchecked")
                ListView<DiffRow> lv = (ListView<DiffRow>) child;
                return lv;
            }
        }
        return null;
    }

    /**
     * 自动绑定左右面板的同步滚动（内部调用，无需外部手动触发）。
     *
     * <p>关键时序：ListView 的 ScrollBar 在其 skin（VirtualFlow）创建后才存在。
     * 因此本方法<b>事件驱动</b>地监听两侧 ListView 的 {@code skinProperty}：
     * 一旦 skin 就绪即查找 ScrollBar 并绑定，不必靠固定延迟去"猜"布局何时完成。
     * 同时保留分阶段延迟兜底重试（最长 3500ms），应对极少数 skin 监听未触发的环境。
     *
     * <p>实现：单向 value 监听 + boolean 标志位防止回传死循环。
     * （禁止使用 bindBidirectional：两侧 ScrollBar 互相绑定会在快速滚动/
     * 触控板手势下形成值传播环路，导致 StackOverflow 或异常被静默吞掉。）
     */
    private static void bindScrollSyncAuto(HBox leftPanel, HBox rightPanel) {
        final boolean[] bound = {false};
        final ListView<DiffRow> leftList = extractListView(leftPanel);
        final ListView<DiffRow> rightList = extractListView(rightPanel);

        // 真正执行绑定的动作：两侧 ScrollBar 都就绪才绑定
        Runnable tryBind = () -> {
            if (bound[0] || leftList == null || rightList == null) return;
            ScrollBar ls = findVerticalScrollBar(leftList);
            ScrollBar rs = findVerticalScrollBar(rightList);
            if (ls != null && rs != null) {
                bindScrollUni(ls, rs);
                bound[0] = true;
                System.out.println("[INFO] 同步滚动绑定成功");
            }
        };

        // 事件驱动：skin 就绪即绑定
        for (ListView<DiffRow> lv : new ListView[]{leftList, rightList}) {
            if (lv == null) continue;
            if (lv.getSkin() != null) {
                tryBind.run();
            } else {
                lv.skinProperty().addListener((obs, oldSkin, newSkin) -> {
                    if (newSkin != null) tryBind.run();
                });
            }
        }

        // 兜底：分阶段延迟重试（含较长延迟），应对 skin 监听未触发等异常环境
        final int[] DELAYS_MS = {100, 400, 1000, 2000, 3500};
        final int[] idx = {0};
        Runnable retry = new Runnable() {
            @Override public void run() {
                if (bound[0]) return;
                tryBind.run();
                if (bound[0]) return;
                int i = idx[0]++;
                if (i >= DELAYS_MS.length) {
                    System.err.println("[WARN] 同步滚动：重试耗尽，放弃绑定（降级为独立滚动）");
                    return;
                }
                PauseTransition pt = new PauseTransition(Duration.millis(DELAYS_MS[i]));
                pt.setOnFinished(e -> run());
                pt.play();
            }
        };
        Platform.runLater(retry);
    }

    /**
     * 单向双向滚动同步：两个 ScrollBar 互监 value 变化，用标志位防止死循环。
     *
     * <p>相比 bindBidirectional 的优势：
     * <ul>
     *   <li>不会形成 JavaFX 属性系统的双向依赖环路</li>
     *   <li>即使一侧触发多次快速更新，另一侧也只跟随最终值</li>
     *   <li>可调试（可在 listener 内加日志而不触发无限递归）</li>
     * </ul>
     */
    private static void bindScrollUni(ScrollBar a, ScrollBar b) {
        final boolean[] syncing = {false};

        a.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            try { b.setValue(newVal.doubleValue()); } finally { syncing[0] = false; }
        });

        b.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (syncing[0]) return;
            syncing[0] = true;
            try { a.setValue(newVal.doubleValue()); } finally { syncing[0] = false; }
        });
    }

    /**
     * 手动绑定两个 ListView 的垂直滚动位置（公开 API，供高级场景使用）。
     * 内部 create() 已自动调用此逻辑的增强版，通常不需要手动调用。
     */
    public static void bindScrollSync(ListView<DiffRow> left, ListView<DiffRow> right) {
        Platform.runLater(() -> {
            try {
                ScrollBar leftBar = findVerticalScrollBar(left);
                ScrollBar rightBar = findVerticalScrollBar(right);
                if (leftBar != null && rightBar != null) {
                    bindScrollUni(leftBar, rightBar);
                    System.out.println("[INFO] 手动同步滚动绑定成功（单向模式）");
                }
            } catch (Exception e) {
                System.err.println("[WARN] 手动同步滚动绑定失败: " + e.getMessage());
            }
        });
    }

    /** 在 ListView 内查找垂直 ScrollBar。 */
    private static ScrollBar findVerticalScrollBar(ListView<?> listView) {
        for (Node n : listView.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar) {
                ScrollBar bar = (ScrollBar) n;
                if (bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    return bar;
                }
            }
        }
        return null;
    }

    /** 构建 CSS 样式字符串（左侧/右侧标题栏）。 */
    private static String buildHeaderStyle(boolean isLeft) {
        String fg = isLeft ? "#c5221f" : "#188038";  // 左红右绿（对应删除/新增语义）
        String bg = "#f8f9fa";
        return "-fx-font-family: " + MONO_FONT + "; "
                + "-fx-font-size: 12px; "
                + "-fx-text-fill: " + fg + "; "
                + "-fx-font-weight: bold; "
                + "-fx-cursor: hand;";
    }

    /** 截断过长路径用于标题栏显示。 */
    private static String truncatePath(String path) {
        if (path == null || path.isEmpty()) return "(空)";
        int maxLen = 50;
        if (path.length() <= maxLen) return path;
        return "…" + path.substring(path.length() - maxLen);
    }

    /** Color 转 #RRGGBB 字符串。 */
    private static String toHex(Color c) {
        return String.format("%02X%02X%02X",
                (int)(c.getRed() * 255),
                (int)(c.getGreen() * 255),
                (int)(c.getBlue() * 255));
    }

    // ==================== 内部类：自定义 ListCell ====================

    /**
     * 差异行渲染器。
     * 根据 DiffRow.Type 和左/右侧标识，决定该行在当前 ListView 中的展示方式：
     * - 行号 gutter（固定宽度，右对齐数字）
     * - 文本内容（根据类型着色）
     */
    private static class DiffCell extends ListCell<DiffRow> {
        private final boolean isLeft;

        DiffCell(boolean isLeft) {
            this.isLeft = isLeft;
        }

        @Override
        protected void updateItem(DiffRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                setText(null);
                setStyle("");
                return;
            }

            try {
                renderRow(row);
            } catch (Exception ex) {
                // 防止单行渲染异常拖垮整个 ListView（表现为白板/崩溃）
                System.err.println("[ERROR] DiffCell 渲染异常（行 " + (getIndex()) + "）: " + ex.getMessage());
                Label errLabel = new Label("!! 渲染异常: " + ex.getClass().getSimpleName());
                errLabel.setStyle("-fx-text-fill: red; -fx-font-family: " + MONO_FONT + "; -fx-padding: 2 8;");
                setGraphic(errLabel);
                setText(null);
                setStyle("-fx-background-color: #ffeeee;");
            }
        }

        /** 实际渲染一行内容（从 updateItem 中分离出来以便 try-catch 包裹）。 */
        private void renderRow(DiffRow row) {
            DiffRow.Type type = row.getType();
            String text = isLeft ? row.getLeftText() : row.getRightText();
            int lineNo = isLeft ? row.getLeftLineNo() : row.getRightLineNo();

            // 处理空侧（如删除行的右侧为空）
            boolean isEmptySide = text == null;

            // 行号格式化
            String noStr = lineNo >= 0 ? String.valueOf(lineNo + 1) : "";  // 1-based display

            // 选择颜色
            String bgColor, textColor;
            switch (type) {
                case DELETED:
                    bgColor = toHex(isLeft ? COLOR_DELETED_BG : COLOR_UNCHANGED_BG);
                    textColor = toHex(isLeft ? COLOR_DELETED_FG : COLOR_UNCHANGED_FG);
                    break;
                case ADDED:
                    bgColor = toHex(isLeft ? COLOR_UNCHANGED_BG : COLOR_ADDED_BG);
                    textColor = toHex(isLeft ? COLOR_UNCHANGED_FG : COLOR_ADDED_FG);
                    break;
                case MODIFIED:
                    // 修改行：两侧都用微黄色背景区分
                    bgColor = toHex(Color.rgb(255, 250, 210));  // 浅黄
                    textColor = toHex(Color.rgb(100, 70, 20));    // 深棕
                    break;
                default: // UNCHANGED
                    bgColor = toHex(COLOR_UNCHANGED_BG);
                    textColor = toHex(COLOR_UNCHANGED_FG);
                    break;
            }

            // 空侧用更浅的背景
            if (isEmptySide) {
                bgColor = toHex(Color.rgb(250, 250, 250));
                textColor = toHex(Color.rgb(200, 200, 200));
            }

            // 构建行内容：[行号] [文本]
            // 选中态（导航定位当前差异行）标记：蓝色行号区 + 左侧蓝色强调边
            boolean selected = isSelected();
            String gutterBg = selected ? toHex(Color.rgb(210, 225, 245)) : toHex(COLOR_GUTTER_BG);

            Label noLabel = new Label(noStr);
            noLabel.setStyle("-fx-font-family: " + MONO_FONT + "; "
                    + "-fx-font-size: 12px; "
                    + "-fx-text-fill: #" + toHex(COLOR_GUTTER_FG) + "; "
                    + "-fx-padding: 0 6 0 4; "
                    + "-fx-min-width: 45; "
                    + "-fx-max-width: 45; "
                    + "-fx-alignment: CENTER_RIGHT; "
                    + "-fx-background-color: #" + gutterBg + ";");
            noLabel.setPrefWidth(45);

            Label contentLabel = new Label(escapeHtml(text == null ? "" : text));
            contentLabel.setStyle("-fx-font-family: " + MONO_FONT + "; "
                    + "-fx-font-size: 13px; "
                    + "-fx-text-fill: #" + textColor + "; "
                    + "-fx-padding: 0 8 0 4; "
                    + "-fx-background-color: #" + bgColor + ";"
                    + "-fx-wrap-text: false;"
                    + "-fx-ellipsis-string: '…';");

            HBox hbox = new HBox(noLabel, contentLabel);
            hbox.setFillHeight(true);
            hbox.setAlignment(Pos.CENTER_LEFT);
            String accent = selected ? " -fx-border-color: #1a73e8; -fx-border-width: 0 0 0 3;" : "";
            hbox.setStyle("-fx-background-color: #" + bgColor + ";" + accent);

            setGraphic(hbox);
            setText(null);
            setPadding(Insets.EMPTY);
        }

        /** 转义 HTML 特殊字符（Label 可能含 < > & 等）。 */
        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;")
                     .replace("<", "&lt;")
                     .replace(">", "&gt;");
        }
    }

    /** 轻量分隔线组件（用于标题栏中间）。 */
    private static class Separator extends Region {
        Separator() {
            setPrefWidth(1);
            setMinWidth(1);
            setMaxWidth(1);
            setStyle("-fx-background-color: #ddd;");
        }
    }
}
