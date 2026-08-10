package com.bempdiff.ui;

import com.bempdiff.Main;
import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.report.MarkdownReport;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.kordamp.ikonli.javafx.FontIcon;

public class App extends Application {
    private UiConfig config;
    private Path oldPath;
    private Path newPath;
    private PackageSnapshot oldSnap;
    private PackageSnapshot newSnap;
    private DiffResult diff;
    private DiffStats stats;
    private final Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
    private StageASummary aiSummary;
    private final List<FileAnalysis> aiFiles = new ArrayList<>();
    private final List<String> auditLog = new ArrayList<>();
    private TreeView<String> treeView;
    private TextArea oldSrc;
    private TextArea newSrc;
    private Label statusBar;
    private final Label summaryLabel = new Label("（尚未分析）");
    private final ListView<String> fileList = new ListView<>();
    private final ListView<String> destructiveList = new ListView<>();
    private final ListView<String> auditListView = new ListView<>();
    private TextField oldField;
    private TextField newField;
    private Map<String, DiffStatus> currentStatusMap = new HashMap<>();
    private Map<String, String> currentLayerMap = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(App.class.getName());
    private static final String MSG_COMPARE_FIRST = "请先「开始比对」";
    private static final String FORM_CONTROL = "form-control";
    private static final String BTN_DEFAULT = "btn-default";

    @Override
    public void start(Stage stage) {
        Path path = Paths.get(System.getProperty("user.home"), ".bempdiff", "ui-config.properties");
        config = new UiConfig(path);
        BorderPane borderPane = new BorderPane();
        borderPane.setTop(buildToolbar(stage));
        borderPane.setCenter(buildCenter());
        statusBar = new Label("就绪。选择老包/新包后点「开始比对」。");
        statusBar.setPadding(new Insets(4));
        borderPane.setBottom(statusBar);
        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double initW = Math.min(1280, Math.max(960, visual.getWidth() - 80));
        double initH = Math.min(800, Math.max(600, visual.getHeight() - 80));
        Scene scene = new Scene(borderPane, initW, initH);
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        borderPane.getStyleClass().add("bootstrap");
        stage.setScene(scene);
        stage.setTitle("BEMP WAR/JAR 差异比对与智能分析工具");
        stage.setMinWidth(900);
        stage.setMinHeight(540);
        stage.show();
    }

    private VBox buildToolbar(Stage stage) {
        oldField = new TextField();
        oldField.setPromptText("老包（生产版本）.war/.jar");
        oldField.setPrefColumnCount(22);
        oldField.getStyleClass().add(FORM_CONTROL);
        newField = new TextField();
        newField.setPromptText("新包（产品部下发）.war/.jar");
        newField.setPrefColumnCount(22);
        newField.getStyleClass().add(FORM_CONTROL);
        Button browseOld = new Button("浏览…");
        browseOld.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseOld.setOnAction(e -> pick(stage, oldField, false));
        Button browseNew = new Button("浏览…");
        browseNew.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseNew.setOnAction(e -> pick(stage, newField, true));
        HBox pathBox = new HBox(6, new Label("老:"), oldField, browseOld, new Label("新:"), newField, browseNew);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(oldField, Priority.ALWAYS);
        HBox.setHgrow(newField, Priority.ALWAYS);
        ToolBar pathBar = new ToolBar(pathBox);
        pathBar.setPrefHeight(Region.USE_COMPUTED_SIZE);

        Button compareBtn = new Button("开始比对");
        compareBtn.getStyleClass().addAll("btn", "btn-primary");
        compareBtn.setOnAction(e -> onCompare());
        Button aiBtn = new Button("AI 分析");
        aiBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        aiBtn.setOnAction(e -> onAnalyze());
        Button reportBtn = new Button("导出报告");
        reportBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        reportBtn.setOnAction(e -> onExportReport());
        Button exportBtn = new Button("导出资产");
        exportBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        exportBtn.setOnAction(e -> onExportAssets());
        Button settingsBtn = new Button("设置");
        settingsBtn.setGraphic(new FontIcon("bi-gear"));
        settingsBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        settingsBtn.setOnAction(e -> ConfigDialog.show(stage, config));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionBox = new HBox(6, compareBtn, aiBtn, reportBtn, exportBtn, spacer, settingsBtn);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        ToolBar actionBar = new ToolBar(actionBox);
        actionBar.setPrefHeight(Region.USE_COMPUTED_SIZE);

        VBox toolbar = new VBox(pathBar, actionBar);
        toolbar.setSpacing(4);
        toolbar.setPadding(new Insets(6, 8, 4, 8));
        return toolbar;
    }

    private void pick(Stage stage, TextField field, boolean isNew) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAR/JAR", "*.war", "*.jar", "*.zip"));
        File f = fc.showOpenDialog(stage);
        if (f != null) {
            field.setText(f.getAbsolutePath());
            if (isNew) {
                newPath = f.toPath();
            } else {
                oldPath = f.toPath();
            }
        }
    }

    private SplitPane buildCenter() {
        treeView = new TreeView<>();
        treeView.setMinWidth(160);
        treeView.setCellFactory(tv -> createDiffTreeCell());
        treeView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, neu) -> onTreeSelect(neu));
        oldSrc = new TextArea();
        oldSrc.setEditable(false);
        oldSrc.getStyleClass().add(FORM_CONTROL);
        oldSrc.setStyle("-fx-font-family: monospace;");
        oldSrc.setMinWidth(180);
        newSrc = new TextArea();
        newSrc.setEditable(false);
        newSrc.getStyleClass().add(FORM_CONTROL);
        newSrc.setStyle("-fx-font-family: monospace;");
        newSrc.setMinWidth(180);
        SplitPane srcSplit = new SplitPane(oldSrc, newSrc);
        srcSplit.setDividerPositions(0.5);
        srcSplit.setMinWidth(260);
        TabPane tabPane = new TabPane();
        tabPane.setMinWidth(200);
        Tab summaryTab = new Tab("全局汇总", new ScrollPane(summaryLabel));
        Tab fileTab = new Tab("单文件分析", new ScrollPane(fileList));
        Tab destructiveTab = new Tab("破坏性变更", new ScrollPane(destructiveList));
        Tab auditTab = new Tab("审计日志", new ScrollPane(auditListView));
        for (Tab t : new Tab[]{summaryTab, fileTab, destructiveTab, auditTab}) {
            t.setClosable(false);
        }
        tabPane.getTabs().addAll(summaryTab, fileTab, destructiveTab, auditTab);
        SplitPane mainSplit = new SplitPane(treeView, srcSplit, tabPane);
        mainSplit.setDividerPositions(0.28, 0.7);
        return mainSplit;
    }

    private TreeCell<String> createDiffTreeCell() {
        return new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                DiffStatus st = currentStatusMap.get(item);
                if (st != null) {
                    String layer = currentLayerMap.getOrDefault(item, "?");
                    String name = item.substring(item.lastIndexOf('/') + 1);
                    setText(name + "  [" + layer + "]");
                    if (st == DiffStatus.ADDED) {
                        setStyle("-fx-text-fill: #1a73e8;");
                    } else if (st == DiffStatus.DELETED) {
                        setStyle("-fx-text-fill: #d93025;");
                    } else if (st == DiffStatus.MODIFIED) {
                        setStyle("-fx-text-fill: #e37400;");
                    } else {
                        setStyle("-fx-text-fill: #666;");
                    }
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #202124;");
                }
            }
        };
    }

    private void onCompare() {
        if (oldField.getText().isEmpty() || newField.getText().isEmpty()) {
            alert("请先选择老包和新包");
            return;
        }
        oldPath = Paths.get(oldField.getText());
        newPath = Paths.get(newField.getText());
        statusBar.setText("比对中…");
        Task<DiffTree.TreeResult> task = new Task<DiffTree.TreeResult>() {
            @Override
            protected DiffTree.TreeResult call() throws Exception {
                PackageParser parser = new PackageParser();
                ParseConfig pc = config.toParseConfig();
                oldSnap = parser.parse(oldPath, pc, config.isExpandAll());
                newSnap = parser.parse(newPath, pc, config.isExpandAll());
                DiffEngine engine = new DiffEngine();
                diff = engine.compute(oldSnap, newSnap);
                stats = engine.stats(diff);
                return DiffTree.build(diff, oldSnap, newSnap);
            }
        };
        task.setOnSucceeded(e -> {
            DiffTree.TreeResult r = task.getValue();
            treeView.setRoot(r.root);
            currentStatusMap = r.statusMap;
            currentLayerMap = r.layerMap;
            statusBar.setText("比对完成：" + stats);
            audit("比对 " + oldPath.getFileName() + " → " + newPath.getFileName() + " | " + stats);
        });
        task.setOnFailed(e -> {
            statusBar.setText("比对失败");
            alert("比对失败：" + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    private void onTreeSelect(TreeItem<String> node) {
        String key = resolveTreeKey(node);
        if (key == null) {
            oldSrc.setText("");
            newSrc.setText("");
            return;
        }
        LogicalEntry oe = oldSnap.getEntries().get(key);
        LogicalEntry ne = newSnap.getEntries().get(key);
        boolean isClass = (oe != null && oe.getFileClass() == FileClass.CLASS && oe.getLayer() == Layer.L1)
                || (ne != null && ne.getFileClass() == FileClass.CLASS && ne.getLayer() == Layer.L1);
        if (!isClass) {
            oldSrc.setText("(非业务 class，无源码级 diff)");
            newSrc.setText("");
            return;
        }
        statusBar.setText("反编译 " + key + " …");
        Task<DecompiledUnit> t = new Task<DecompiledUnit>() {
            @Override
            protected DecompiledUnit call() throws Exception {
                Decompiler dec = new Decompiler(cfrOrNull(), findJava());
                return dec.decompile(oldSnap, newSnap, oe, ne, key);
            }
        };
        t.setOnSucceeded(e -> {
            DecompiledUnit u = t.getValue();
            if (u.isOk()) {
                oldSrc.setText(u.getOldSource() != null ? u.getOldSource() : "");
                newSrc.setText(u.getNewSource() != null ? u.getNewSource() : "");
            } else {
                oldSrc.setText("反编译失败：" + u.getError());
                newSrc.setText("");
            }
            statusBar.setText("反编译完成：" + key);
        });
        t.setOnFailed(e -> statusBar.setText("反编译失败：" + t.getException().getMessage()));
        new Thread(t).start();
    }

    private String resolveTreeKey(TreeItem<String> node) {
        if (node == null) return null;
        String key = node.getValue();
        if (key == null || !currentStatusMap.containsKey(key)) return null;
        if (oldSnap == null || newSnap == null) return null;
        return key;
    }

    private void onAnalyze() {
        if (diff == null) {
            alert(MSG_COMPARE_FIRST);
            return;
        }
        statusBar.setText("AI 分析中…");
        Task<Void> t = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Decompiler dec = new Decompiler(cfrOrNull(), findJava());
                List<String> cands = DiffEngine.collectL1ClassCandidates(diff, oldSnap, newSnap);
                decompiled.clear();
                int n = Math.min(cands.size(), config.getStageBTopK());
                for (int i = 0; i < n; i++) {
                    String k = cands.get(i);
                    decompiled.put(k, dec.decompile(oldSnap, newSnap,
                            oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
                }
                AiConfig aiCfg = config.toAiConfig();
                Path replayDir = Paths.get(System.getProperty("user.home"), ".bempdiff", "ai_replay");
                AiAnalyzer ai = (config.isAiEnabled() && !config.getAiApiKey().isEmpty())
                        ? new HttpAiAnalyzer(aiCfg) : new MockAiAnalyzer(replayDir);
                try {
                    aiSummary = ai.stageA(diff, decompiled, aiCfg);
                    List<AiAnalyzer.DecompileReq> reqs = new ArrayList<>();
                    for (Map.Entry<String, DecompiledUnit> e : decompiled.entrySet()) {
                        reqs.add(new AiAnalyzer.DecompileReq(e.getKey(), e.getValue()));
                    }
                    aiFiles.clear();
                    aiFiles.addAll(ai.stageB(reqs, aiCfg));
                } catch (RuntimeException ex) {
                    aiSummary = new StageASummary();
                    aiSummary.setOverallRisk("UNKNOWN");
                    aiSummary.setImpactScope("AI 服务不可用：" + ex.getMessage());
                    aiSummary.setTestThemes(Arrays.asList("人工核对全部差异文件", "回归核心业务流程"));
                    aiSummary.setFileRisks(new ArrayList<>());
                    aiFiles.clear();
                }
                return null;
            }
        };
        t.setOnSucceeded(e -> {
            summaryLabel.setText("整体风险: " + aiSummary.getOverallRisk()
                    + "\n影响范围: " + aiSummary.getImpactScope()
                    + "\n测试主题: " + aiSummary.getTestThemes());
            fileList.getItems().clear();
            for (FileAnalysis fa : aiFiles) {
                fileList.getItems().add(fa.getKey() + " | " + fa.getRisk() + " | " + fa.getIntent());
            }
            destructiveList.getItems().clear();
            for (String s : DiffTree.destructiveChanges(diff, oldSnap)) {
                destructiveList.getItems().add(s);
            }
            statusBar.setText("AI 分析完成");
            audit("AI 分析 | provider=" + config.getAiProvider() + " | 深读=" + aiFiles.size());
        });
        t.setOnFailed(e -> {
            statusBar.setText("AI 分析失败");
            alert("AI 分析失败：" + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    private void onExportReport() {
        if (diff == null) {
            alert(MSG_COMPARE_FIRST);
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        fc.setInitialFileName("diff_report.md");
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        statusBar.setText("导出报告中…");
        Task<Path> t = new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                Decompiler dec = new Decompiler(cfrOrNull(), findJava());
                Map<String, DecompiledUnit> dd = new LinkedHashMap<>();
                List<String> cands = DiffEngine.collectL1ClassCandidates(diff, oldSnap, newSnap);
                int n = Math.min(cands.size(), config.getTopK());
                for (int i = 0; i < n; i++) {
                    String k = cands.get(i);
                    dd.put(k, dec.decompile(oldSnap, newSnap,
                            oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
                }
                Path out = f.toPath();
                new MarkdownReport(config.getTopK()).writeToFile(oldSnap, newSnap, diff, stats, dd, out);
                return out;
            }
        };
        t.setOnSucceeded(e -> {
            statusBar.setText("报告已导出：" + t.getValue());
            audit("导出报告 " + f);
        });
        t.setOnFailed(e -> {
            statusBar.setText("导出失败");
            alert("导出报告失败：" + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    private void onExportAssets() {
        if (diff == null) {
            alert(MSG_COMPARE_FIRST);
            return;
        }
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择导出目录");
        File dir = dc.showDialog(null);
        if (dir == null) return;
        statusBar.setText("导出资产中…");
        Task<String> t = new Task<String>() {
            @Override
            protected String call() throws Exception {
                Decompiler dec = new Decompiler(cfrOrNull(), findJava());
                Map<String, DecompiledUnit> dd = new LinkedHashMap<>();
                List<String> cands = DiffEngine.collectL1ClassCandidates(diff, oldSnap, newSnap);
                int n = Math.min(cands.size(), config.getTopK());
                for (int i = 0; i < n; i++) {
                    String k = cands.get(i);
                    dd.put(k, dec.decompile(oldSnap, newSnap,
                            oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
                }
                Path root = dir.toPath();
                AssetExporter exp = new AssetExporter();
                Path p1 = exp.exportDiffClasses(diff, oldSnap, newSnap, root);
                Path p2 = exp.exportDiffJars(diff, oldSnap, newSnap, root);
                Path p3 = exp.exportDecompiledSources(dd, root, config.getTopK());
                return p1 + " | " + p2 + " | " + p3;
            }
        };
        t.setOnSucceeded(e -> {
            statusBar.setText("资产已导出：" + t.getValue());
            audit("导出资产 " + dir);
        });
        t.setOnFailed(e -> {
            statusBar.setText("导出失败");
            alert("导出资产失败：" + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    private void audit(String msg) {
        String line = LocalDateTime.now() + "  " + msg;
        auditLog.add(line);
        auditListView.getItems().add(line);
    }

    private Path cfrOrNull() {
        return (config.getCfrJar() != null && !config.getCfrJar().isEmpty())
                ? Paths.get(config.getCfrJar()) : null;
    }

    private String findJava() {
        String home = System.getenv("JAVA_HOME");
        if (home != null) {
            Path exe = Paths.get(home, "bin", "java.exe");
            if (exe.toFile().isFile()) return exe.toString();
            Path exe2 = Paths.get(home, "bin", "java");
            if (exe2.toFile().isFile()) return exe2.toString();
        }
        return "java";
    }

    private void alert(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            if (isBatchSubcommand(args[0])) {
                try {
                    Main.main(args);
                } catch (Exception e) {
                    LOG.severe("[headless] 批处理执行失败: " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                }
                return;
            }
            LOG.info("用法: 双击启动 GUI；或命令行运行比对：");
            LOG.info("  BempDiff.exe compare  <old> <new> [--expand-all]");
            LOG.info("  BempDiff.exe report   <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <md>]");
            LOG.info("  BempDiff.exe export   <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <dir>]");
            LOG.info("  BempDiff.exe decompile|inspect|ai ...  （同 com.bempdiff.Main 用法）");
            System.exit(2);
        }
        launch(args);
    }

    private static boolean isBatchSubcommand(String arg) {
        switch (arg) {
            case "inspect":
            case "compare":
            case "decompile":
            case "report":
            case "export":
            case "ai":
                return true;
            default:
                return false;
        }
    }
}