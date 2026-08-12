package com.bempdiff.ui;

import com.bempdiff.report.MarkdownParser;
import com.bempdiff.report.MarkdownParser.Block;
import com.bempdiff.report.MarkdownParser.InlineToken;
import com.bempdiff.report.MarkdownParser.ListItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.FileChooser;
import org.kordamp.bootstrapfx.BootstrapFX;

/**
 * 独立 Markdown 报告预览窗口。
 *
 * 纯 JavaFX 节点渲染（不依赖 javafx.web / 第三方 Markdown 库）：
 * 使用 com.bempdiff.report.MarkdownParser 解析为块模型，再把各块转为 JavaFX 节点，
 * 正确呈现标题 / 段落 / 代码块 / 有序·无序列表 / GFM 表格 / 分隔线 / 引用 及行内样式。
 *
 * 用法：
 *   ReportViewer.show(stage, markdownString, "标题");   // 直接预览字符串（如报告预览）
 *   ReportViewer.showFile(stage, pathToMd);             // 打开任意 .md 文件
 */
public final class ReportViewer {

    private ReportViewer() {
    }

    /** 等宽字体（代码/差异行共用）。 */
    private static final String MONO = "'Consolas','Monaco','Courier New',monospace";

    /** 差异行渲染的阈值：超过则退化为纯文本（避免超长 diff 非虚拟化节点过多）。 */
    private static final int DIFF_MAX_ROWS = 2500;

    public static void show(Window owner, String markdown, String title) {
        openStage(owner, title != null ? title : "Markdown 报告预览", markdown, null);
    }

    public static void showFile(Window owner, Path file) {
        try {
            String md = Files.readString(file, StandardCharsets.UTF_8);
            openStage(owner, file.getFileName().toString(), md, file);
        } catch (IOException e) {
            alert("无法读取文件：" + e.getMessage());
        }
    }

    private static void openStage(Window owner, String title, String initialMd, Path file) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(title);

        ScrollPane sp = new ScrollPane();
        sp.setFitToWidth(true);
        sp.setPadding(new Insets(10));
        sp.setContent(buildContent(initialMd));

        Button openBtn = new Button("打开…");
        openBtn.getStyleClass().addAll("btn", "btn-default");
        openBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Markdown", "*.md", "*.markdown", "*.txt"));
            java.io.File f = fc.showOpenDialog(stage);
            if (f != null) {
                try {
                    String md = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    sp.setContent(buildContent(md));
                    stage.setTitle(f.getName());
                } catch (IOException ex) {
                    alert("无法读取文件：" + ex.getMessage());
                }
            }
        });

        Button reloadBtn = new Button("重新加载");
        reloadBtn.getStyleClass().addAll("btn", "btn-default");
        if (file != null) {
            reloadBtn.setOnAction(e -> {
                try {
                    sp.setContent(buildContent(Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException ex) {
                    alert("无法读取文件：" + ex.getMessage());
                }
            });
        } else {
            reloadBtn.setDisable(true);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("关闭");
        closeBtn.getStyleClass().addAll("btn", "btn-default");
        closeBtn.setOnAction(e -> stage.close());

        HBox bar = new HBox(6, openBtn, reloadBtn, spacer, closeBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        ToolBar tb = new ToolBar(bar);

        BorderPane bp = new BorderPane();
        bp.setTop(tb);
        bp.setCenter(sp);
        Scene scene = new Scene(bp, 920, 720);
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        bp.getStyleClass().add("bootstrap");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.show();
    }

    // ------------------------------------------------------------------
    // 内容构建
    // ------------------------------------------------------------------

    private static VBox buildContent(String md) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(4, 8, 18, 8));
        root.setFillWidth(true);
        List<Block> blocks = MarkdownParser.parse(md);
        for (Block b : blocks) {
            javafx.scene.Node n = blockToNode(b);
            if (n != null) {
                root.getChildren().add(n);
            }
        }
        return root;
    }

    private static javafx.scene.Node blockToNode(Block b) {
        switch (b.type) {
            case HEADING:
                return buildHeading(b);
            case PARAGRAPH:
                return buildParagraph(b.headingText);
            case CODE:
                return buildCode(b);
            case LIST:
                return buildList(b);
            case TABLE:
                return buildTable(b);
            case HR:
                Separator sep = new Separator();
                sep.setPadding(new Insets(6, 0, 6, 0));
                return sep;
            case QUOTE:
                return buildQuote(b.headingText);
            default:
                return null;
        }
    }

    private static Label buildHeading(Block b) {
        double[] SZ = {0, 24, 20, 17, 15, 13.5, 12.5};
        int lvl = Math.min(6, Math.max(1, b.headingLevel));
        Label l = new Label(b.headingText);
        l.setStyle("-fx-font-size:" + SZ[lvl] + "px; -fx-font-weight:bold; -fx-text-fill:#1f2328;");
        l.setPadding(new Insets(lvl == 1 ? 10 : 6, 0, 4, 0));
        l.setWrapText(true);
        return l;
    }

    private static TextFlow buildParagraph(String text) {
        TextFlow tf = buildInline(text);
        tf.setStyle("-fx-font-size:13px; -fx-text-fill:#24292e;");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private static javafx.scene.Node buildCode(Block b) {
        String code = b.codeText;
        if (isDiffBlock(b)) {
            return buildDiffCode(code);
        }
        TextArea ta = new TextArea(code);
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setStyle("-fx-font-family:" + MONO + ";"
                + " -fx-font-size:12px; -fx-background-color:#f6f8fa;"
                + " -fx-text-fill:#24292e; -fx-border-color:#d0d7de;"
                + " -fx-border-width:1; -fx-border-radius:4;");
        int lines = code.split("\n", -1).length;
        double h = Math.min(Math.max(lines * 16.0 + 14, 44), 540);
        ta.setPrefHeight(h);
        ta.setMaxHeight(h);
        VBox box = new VBox(ta);
        box.setPadding(new Insets(2, 0, 2, 0));
        return box;
    }

    /**
     * 判断代码块是否为统一 diff（删除/新增行）。
     * 判定：① 围栏语言为 diff；② 同时含有 "- " 与 "+ " 行（几乎可确定是差异块）。
     * 仅含单一类型标记（如纯说明里偶发的 "- "）不会误判为 diff。
     */
    private static boolean isDiffBlock(Block b) {
        if ("diff".equalsIgnoreCase(b.codeLang)) {
            return true;
        }
        if (b.codeText == null) {
            return false;
        }
        int minus = 0, plus = 0;
        for (String line : b.codeText.split("\n", -1)) {
            if (line.startsWith("- ")) minus++;
            else if (line.startsWith("+ ")) plus++;
            if (minus > 0 && plus > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将统一 diff 渲染为逐行着色的对比块：
     * <ul>
     *   <li>删除行：浅红底 + 深红字 + 左侧红色强调条</li>
     *   <li>新增行：浅绿底 + 深绿字 + 左侧绿色强调条</li>
     *   <li>上下文行：白底 + 灰字 + 占位符号</li>
     * </ul>
     * 采用 GitHub 风格高对比配色，差异行醒目易辨。
     */
    private static javafx.scene.Node buildDiffCode(String code) {
        String[] lines = code.split("\n", -1);
        // 超长 diff 退化为纯文本，避免非虚拟化节点过多导致卡顿
        if (lines.length > DIFF_MAX_ROWS) {
            TextArea ta = new TextArea(code);
            ta.setEditable(false);
            ta.setWrapText(false);
            ta.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px;"
                    + " -fx-background-color:#f6f8fa; -fx-text-fill:#24292e;"
                    + " -fx-border-color:#d0d7de; -fx-border-width:1; -fx-border-radius:4;");
            double h = Math.min(lines.length * 16.0 + 14, 540);
            ta.setPrefHeight(h);
            ta.setMaxHeight(h);
            VBox box = new VBox(ta);
            box.setPadding(new Insets(2, 0, 2, 0));
            return box;
        }

        VBox rows = new VBox();
        rows.setSpacing(0);
        rows.setStyle("-fx-border-color:#d0d7de; -fx-border-width:1; -fx-border-radius:4;"
                + " -fx-background-color:#ffffff;");

        for (String raw : lines) {
            char kind;          // 'D' 删除 / 'A' 新增 / 'C' 上下文
            String body;
            if (raw.startsWith("- ")) {
                kind = 'D';
                body = raw.substring(2);
            } else if (raw.startsWith("+ ")) {
                kind = 'A';
                body = raw.substring(2);
            } else if (raw.startsWith("  ")) {
                kind = 'C';
                body = raw.substring(2);
            } else {
                kind = 'C';
                body = raw;
            }

            String bg, accent, textCol, signCol, sign;
            if (kind == 'D') {
                bg = "#ffebe9"; accent = "#ff5252"; textCol = "#b71c1c"; signCol = "#ff5252"; sign = "-";
            } else if (kind == 'A') {
                bg = "#e6ffed"; accent = "#2ea043"; textCol = "#0f7a2f"; signCol = "#2ea043"; sign = "+";
            } else {
                bg = "#ffffff"; accent = "transparent"; textCol = "#24292e"; signCol = "#8c959f"; sign = " ";
            }

            HBox row = new HBox(0);
            row.setMinHeight(18);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color:" + bg + "; -fx-border-color:" + accent
                    + "; -fx-border-width:0 0 0 3;");

            Label gutter = new Label(sign);
            gutter.setMinHeight(18);
            gutter.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-font-weight:bold;"
                    + " -fx-text-fill:" + signCol + "; -fx-min-width:26; -fx-max-width:26;"
                    + " -fx-alignment:CENTER; -fx-padding:1 0 1 0;");

            Label content = new Label(body);
            content.setMinHeight(18);
            content.setWrapText(true);
            content.setStyle("-fx-font-family:" + MONO + "; -fx-font-size:12px; -fx-text-fill:" + textCol + ";");
            HBox.setHgrow(content, Priority.ALWAYS);

            row.getChildren().addAll(gutter, content);
            rows.getChildren().add(row);
        }

        VBox box = new VBox(rows);
        box.setPadding(new Insets(2, 0, 2, 0));
        return box;
    }

    private static javafx.scene.Node buildList(Block b) {
        VBox listBox = new VBox(3);
        int num = 1;
        for (ListItem it : b.items) {
            HBox row = new HBox(6);
            String marker = it.ordered ? (num++) + ". " : "• ";
            Label bullet = new Label(marker);
            bullet.setStyle("-fx-font-size:13px; -fx-text-fill:#24292e;");
            TextFlow content = buildInline(it.content);
            content.setStyle("-fx-font-size:13px;");
            content.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(content, Priority.ALWAYS);
            row.getChildren().addAll(bullet, content);
            row.setPadding(new Insets(0, 0, 0, it.indent * 12));
            listBox.getChildren().add(row);
        }
        return listBox;
    }

    private static javafx.scene.Node buildTable(Block b) {
        List<String> header = b.tableHeader;
        int cols = Math.max(header.size(), 1);
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);

        List<List<String>> all = new ArrayList<>();
        all.add(header);
        all.addAll(b.tableRows);

        int[] maxLen = new int[cols];
        for (List<String> row : all) {
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() ? row.get(c) : "";
                maxLen[c] = Math.max(maxLen[c], v.length());
            }
        }

        for (int r = 0; r < all.size(); r++) {
            List<String> row = all.get(r);
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() ? row.get(c) : "";
                Label lbl = new Label(v);
                lbl.setWrapText(true);
                lbl.setMaxWidth(Double.MAX_VALUE);
                String st = "-fx-padding:5 8 5 8; -fx-border-color:#d0d7de;"
                        + " -fx-border-width:0.5; -fx-font-size:12.5px;";
                if (r == 0) {
                    st += " -fx-font-weight:bold; -fx-background-color:#f6f8fa; -fx-text-fill:#1f2328;";
                } else {
                    st += " -fx-text-fill:#24292e;";
                }
                lbl.setStyle(st);
                grid.add(lbl, c, r);
            }
        }

        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPrefWidth(Math.min(maxLen[c] * 8.0 + 22, 380));
            grid.getColumnConstraints().add(cc);
        }
        return grid;
    }

    private static javafx.scene.Node buildQuote(String text) {
        VBox qb = new VBox(4);
        for (String p : text.split("\n", -1)) {
            TextFlow tf = buildInline(p);
            tf.setStyle("-fx-font-size:13px; -fx-font-style:italic; -fx-text-fill:#57606a;");
            qb.getChildren().add(tf);
        }
        qb.setStyle("-fx-border-color:#d0d7de; -fx-border-width:0 0 0 4; -fx-background-color:#f6f8fa;");
        qb.setPadding(new Insets(6, 10, 6, 14));
        return qb;
    }

    private static TextFlow buildInline(String s) {
        TextFlow tf = new TextFlow();
        for (InlineToken tok : MarkdownParser.parseInline(s)) {
            Text t = new Text(tok.value);
            switch (tok.kind) {
                case BOLD:
                    t.setStyle("-fx-font-weight:bold; -fx-font-size:13px; -fx-fill:#24292e;");
                    break;
                case ITALIC:
                    t.setStyle("-fx-font-style:italic; -fx-font-size:13px; -fx-fill:#24292e;");
                    break;
                case CODE:
                    t.setStyle("-fx-font-family:'Consolas','Monaco',monospace;"
                            + " -fx-font-size:12px; -fx-fill:#c7254e;");
                    break;
                default:
                    t.setStyle("-fx-font-size:13px; -fx-fill:#24292e;");
            }
            tf.getChildren().add(t);
        }
        return tf;
    }

    private static void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setContentText(msg);
        a.showAndWait();
    }
}
