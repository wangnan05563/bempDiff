package com.bempdiff.ui;

import com.bempdiff.Main;
import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectContextAnalyzer;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.FolderDiff;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.parse.FolderParser;
import com.bempdiff.report.MarkdownReport;
import com.bempdiff.ui.DiffRow;
import com.bempdiff.ui.DiffRowsBuilder;
import com.bempdiff.ui.DiffView;
import com.bempdiff.ui.ReportViewer;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
    private ProjectContext lastProjectContext;
    private final List<FileAnalysis> aiFiles = new ArrayList<>();
    private final List<String> auditLog = new ArrayList<>();
    private TreeView<String> treeView;
    /** 差异对比 TabPane：每次点击文件树叶子，在此新建或切换到一个 DiffView Tab。 */
    private TabPane diffTabPane;
    /** 已打开的 diff Tab 索引（key → Tab），避免重复打开同一文件。 */
    private final Map<String, Tab> openDiffTabs = new HashMap<>();
    private Label statusBar;
    /** 开始比对后是否自动触发 AI 两阶段分析（FR-CX-01，等价 report --ai）。工具栏开关绑定此字段。 */
    private CheckBox autoAiChk;
    private final Label summaryLabel = new Label("（尚未分析）");
    private final ListView<String> fileList = new ListView<>();
    private final ListView<String> destructiveList = new ListView<>();
    private final ListView<String> auditListView = new ListView<>();
    private TextField oldField;
    private TextField newField;
    private Map<String, DiffStatus> currentStatusMap = new HashMap<>();
    private Map<String, String> currentLayerMap = new HashMap<>();
    /** 差异树过滤条件（按变更类型勾选 + 文件名搜索）；默认全选=显示全部，与历史行为一致。 */
    private final DiffTree.TreeFilter treeFilter = new DiffTree.TreeFilter();
    /** 过滤栏的"当前显示"文字标识（字段化以便各控件更新）。 */
    private Label filterIndicator;
    /** 当前比对是否为"文件夹比较"模式（true 时数据源用 FolderDiff 引擎，且双击查看走文本 diff）。 */
    private boolean folderCompare = false;
    /** 文件夹对比结果（folderCompare 模式下的数据源，替代 PackageSnapshot/DiffResult）。 */
    private FolderDiff.FolderDiffResult folderResult;
    /** relPath → FolderEntry 快速索引，供双击查看时取真实路径与属性。 */
    private final Map<String, FolderDiff.FolderEntry> folderEntries = new HashMap<>();
    private static final Logger LOG = Logger.getLogger(App.class.getName());
    private static final String MSG_COMPARE_FIRST = "请先「开始比对」";
    private static final String FORM_CONTROL = "form-control";
    private static final String BTN_DEFAULT = "btn-default";
    private Stage stage;
    /** 主布局容器（遮罩比对进度时整体 setDisable，禁用其下所有交互控件）。 */
    private BorderPane mainBorderPane;
    /** 比对进度遮罩层（半透明背景 + 居中进度条），覆盖全窗口屏蔽下方操作。 */
    private StackPane busyOverlay;
    /** 遮罩层提示文字。 */
    private Label busyLabel;
    /** 差异树「层级展开」控制条容器（按钮随当前树深度动态生成）。 */
    private HBox treeLevelRow;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Path path = Paths.get(System.getProperty("user.home"), ".bempdiff", "ui-config.properties");
        config = new UiConfig(path);
        BorderPane borderPane = new BorderPane();
        mainBorderPane = borderPane;
        borderPane.setTop(buildToolbar(stage));
        borderPane.setCenter(buildCenter());
        statusBar = new Label("就绪。选择老包/新包后点「开始比对」。");
        statusBar.setPadding(new Insets(4));
        borderPane.setBottom(statusBar);

        // Req 2：比对进度遮罩层（半透明背景 + 居中不确定进度条），覆盖全窗口；
        // 比对期间 mainBorderPane.setDisable(true) 禁用其下所有控件，遮罩本身在最上层拦截点击。
        busyOverlay = buildBusyOverlay();

        Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double initW = Math.min(1280, Math.max(960, visual.getWidth() - 80));
        double initH = Math.min(800, Math.max(600, visual.getHeight() - 80));
        StackPane rootStack = new StackPane(borderPane, busyOverlay);
        Scene scene = new Scene(rootStack, initW, initH);
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        borderPane.getStyleClass().add("bootstrap");
        stage.setScene(scene);
        stage.setTitle("BEMP WAR/JAR 差异比对与智能分析工具");
        // 应用图标：替换默认 Java 图标。从 classpath 资源 /bempdiff-logo.png 加载（打包时并入 app.jar）；
        // 资源缺失时静默降级为平台默认图标，不影响启动。
        try (InputStream iconStream = App.class.getResourceAsStream("/bempdiff-logo.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {
            // 图标加载失败：保留默认图标，不阻断启动
        }
        stage.setMinWidth(900);
        stage.setMinHeight(540);
        stage.show();
    }

    private VBox buildToolbar(Stage stage) {
        oldField = new TextField();
        oldField.setPromptText("老包(.war/.jar) 或文件夹路径");
        oldField.setPrefColumnCount(22);
        oldField.getStyleClass().add(FORM_CONTROL);
        newField = new TextField();
        newField.setPromptText("新包(.war/.jar) 或文件夹路径");
        newField.setPrefColumnCount(22);
        newField.getStyleClass().add(FORM_CONTROL);
        Button browseOld = new Button("浏览包…");
        browseOld.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseOld.setOnAction(e -> pick(stage, oldField, false));
        Button browseNew = new Button("浏览包…");
        browseNew.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseNew.setOnAction(e -> pick(stage, newField, true));
        Button browseOldDir = new Button("浏览目录…");
        browseOldDir.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseOldDir.setOnAction(e -> pickFolder(stage, oldField, false));
        Button browseNewDir = new Button("浏览目录…");
        browseNewDir.getStyleClass().addAll("btn", BTN_DEFAULT);
        browseNewDir.setOnAction(e -> pickFolder(stage, newField, true));
        // FR-CX-01：开始比对后自动触发 AI 两阶段分析（等价 report --ai）的开关；默认开，可关闭以省 API 调用。
        autoAiChk = new CheckBox("自动 AI");
        autoAiChk.getStyleClass().addAll("btn", BTN_DEFAULT);
        autoAiChk.setStyle("-fx-font-size: 11px;");
        autoAiChk.setSelected(config.isAutoAiOnCompare());
        if (config.isAutoAiOnCompare()) {
            autoAiChk.getStyleClass().remove(BTN_DEFAULT);
            autoAiChk.getStyleClass().add("btn-primary");
        }
        autoAiChk.selectedProperty().addListener((obs, old, sel) -> {
            config.setAutoAiOnCompare(sel);
            if (sel) {
                autoAiChk.getStyleClass().remove(BTN_DEFAULT);
                if (!autoAiChk.getStyleClass().contains("btn-primary")) autoAiChk.getStyleClass().add("btn-primary");
            } else {
                autoAiChk.getStyleClass().remove("btn-primary");
                if (!autoAiChk.getStyleClass().contains(BTN_DEFAULT)) autoAiChk.getStyleClass().add(BTN_DEFAULT);
            }
            config.save();
        });
        HBox pathBox = new HBox(6, new Label("老:"), oldField, browseOld, browseOldDir,
                new Label("新:"), newField, browseNew, browseNewDir, autoAiChk);
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
        Button previewBtn = new Button("预览报告");
        previewBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        previewBtn.setOnAction(e -> onPreviewReport());
        Button openBtn = new Button("打开报告");
        openBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        openBtn.setOnAction(e -> onOpenReport());
        Button settingsBtn = new Button("设置");
        settingsBtn.setGraphic(new FontIcon("bi-gear"));
        settingsBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        settingsBtn.setOnAction(e -> ConfigDialog.show(stage, config, getHostServices(), statusBar));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionBox = new HBox(6, compareBtn, aiBtn, reportBtn, exportBtn, previewBtn, openBtn, spacer, settingsBtn);
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
            folderCompare = false; // 选了包文件，回到包比对模式
            field.setText(f.getAbsolutePath());
            if (isNew) {
                newPath = f.toPath();
            } else {
                oldPath = f.toPath();
            }
        }
    }

    /** 选择文件夹（源/目标目录）作为比较对象。设置 folderCompare 标志，后续比对走 FolderParser。 */
    private void pickFolder(Stage stage, TextField field, boolean isNew) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(isNew ? "选择目标文件夹" : "选择源文件夹");
        File dir = dc.showDialog(stage);
        if (dir != null) {
            folderCompare = true; // 选了目录，进入文件夹比较模式
            field.setText(dir.getAbsolutePath());
            if (isNew) {
                newPath = dir.toPath();
            } else {
                oldPath = dir.toPath();
            }
        }
    }

    private SplitPane buildCenter() {
        // ---- 左侧：差异文件树 + 显示模式过滤控件 ----
        treeView = new TreeView<>();
        treeView.setMinWidth(180);
        treeView.setPrefWidth(220);
        treeView.setCellFactory(tv -> createDiffTreeCell());
        treeView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, neu) -> onTreeSelect(neu));

        // 显示模式过滤：分段切换（仅差异 / 全部）+ 搜索框，清晰标识当前模式并可一键切换
        VBox filterBar = buildDiffTreeFilterBar();

        VBox leftBox = new VBox(filterBar, treeView);
        leftBox.setMinWidth(180);
        leftBox.setPrefWidth(220);
        leftBox.setSpacing(2);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // ---- 中间：DiffView TabPane（Beyond Compare 风格，每文件独立 Tab）----
        diffTabPane = new TabPane();
        diffTabPane.setStyle("-fx-tab-min-height: 28; -fx-tab-max-height: 28;");
        diffTabPane.setMinWidth(400);
        // 默认空状态提示
        Tab emptyTab = new Tab("对比视图", new Label("  请在左侧选择一个差异文件以查看对比"));
        emptyTab.setClosable(false);
        diffTabPane.getTabs().add(emptyTab);

        // ---- 右侧：AI 分析 / 汇总信息面板（保持不变）----
        TabPane infoTabPane = new TabPane();
        infoTabPane.setMinWidth(200);
        Tab summaryTab     = new Tab("全局汇总",   new ScrollPane(summaryLabel));
        Tab fileTab        = new Tab("单文件分析", new ScrollPane(fileList));
        Tab destructiveTab = new Tab("破坏性变更", new ScrollPane(destructiveList));
        Tab auditTab       = new Tab("审计日志",   new ScrollPane(auditListView));
        for (Tab t : new Tab[]{summaryTab, fileTab, destructiveTab, auditTab}) {
            t.setClosable(false);
        }
        infoTabPane.getTabs().addAll(summaryTab, fileTab, destructiveTab, auditTab);

        // 三栏布局：[文件树 | 对比视图(TabPane) | 信息面板]
        SplitPane mainSplit = new SplitPane(leftBox, diffTabPane, infoTabPane);
        mainSplit.setDividerPositions(0.22, 0.65);
        return mainSplit;
    }

    /**
     * 构建差异树顶部的显示模式过滤栏：分段 Toggle（仅差异 / 全部）+ 当前模式文字标识。
     * 切换时即时重建差异树。
     */
    /**
     * 构建差异树顶部的过滤栏：
     *  - 搜索框：模糊搜索文件名（不区分大小写子串），勾选"正则"后按 Java 正则匹配文件名；
     *  - 类型勾选：修改 / 新增 / 删除 / 未变，可任意组合过滤显示；
     *  - 当前显示文字标识，随选择实时更新。
     * 任意条件变化即时重建差异树（复用既有 diff 快照，不重新解析）。
     */
    private VBox buildDiffTreeFilterBar() {
        TextField searchField = new TextField();
        searchField.setPromptText("搜索文件名（支持正则）");
        searchField.getStyleClass().add(FORM_CONTROL);
        searchField.setPrefWidth(120);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        // 从持久化配置恢复上次搜索词
        String savedSearch = config.getFilterSearch() == null ? "" : config.getFilterSearch();
        searchField.setText(savedSearch);
        treeFilter.search = savedSearch;
        // 防抖：连续输入期间不重建树，停止输入 200ms 后刷新，避免数千文件大包时每次按键卡顿
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));
        searchDebounce.setOnFinished(evt -> {
            persistFilter();                 // 防抖结束再落盘，避免每次按键都写文件
            if (folderCompare && folderResult != null) rebuildFolderTree();
            else rebuildTree();
        });
        searchField.textProperty().addListener((obs, old, nv) -> {
            treeFilter.search = nv;          // 立即记录最新输入；重建延迟到防抖结束
            searchDebounce.playFromStart();
        });

        ToggleButton regexBtn = new ToggleButton(".* 正则");
        regexBtn.getStyleClass().addAll("btn", BTN_DEFAULT);
        regexBtn.setStyle("-fx-font-size: 11px;");
        // 从持久化配置恢复正则开关
        treeFilter.regex = config.isFilterRegex();
        regexBtn.setSelected(treeFilter.regex);
        if (treeFilter.regex) {
            regexBtn.getStyleClass().remove(BTN_DEFAULT);
            regexBtn.getStyleClass().add("btn-primary");
        }
        regexBtn.selectedProperty().addListener((obs, old, sel) -> {
            treeFilter.regex = sel;
            if (sel) {
                regexBtn.getStyleClass().remove(BTN_DEFAULT);
                if (!regexBtn.getStyleClass().contains("btn-primary")) regexBtn.getStyleClass().add("btn-primary");
            } else {
                regexBtn.getStyleClass().remove("btn-primary");
                if (!regexBtn.getStyleClass().contains(BTN_DEFAULT)) regexBtn.getStyleClass().add(BTN_DEFAULT);
            }
            persistFilter();
            if (folderCompare && folderResult != null) rebuildFolderTree();
            else rebuildTree();
        });

        HBox searchRow = new HBox(4, searchField, regexBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        // 类型勾选：修改 / 新增 / 删除 / 未变（可任意组合过滤显示）；初始态从持久化配置恢复
        CheckBox cbMod  = makeFilterCheckBox("修改", config.isFilterShowModified(), v -> treeFilter.showModified = v);
        CheckBox cbAdd  = makeFilterCheckBox("新增", config.isFilterShowAdded(), v -> treeFilter.showAdded = v);
        CheckBox cbDel  = makeFilterCheckBox("删除", config.isFilterShowDeleted(), v -> treeFilter.showDeleted = v);
        CheckBox cbSame = makeFilterCheckBox("未变", config.isFilterShowUnchanged(), v -> treeFilter.showUnchanged = v);
        HBox checkRow = new HBox(8, cbMod, cbAdd, cbDel, cbSame);
        checkRow.setAlignment(Pos.CENTER_LEFT);

        filterIndicator = new Label("显示: 全部");
        filterIndicator.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // Req 4：差异树「层级展开」控制条 —— 全部展开 / 全部合并 + 按当前树深度动态生成「展开到 N 层」按钮。
        // 动态按钮在每次重建树后由 refreshTreeLevelButtons() 填充（树深度未知，无法静态写死）。
        Label levelTitle = new Label("层级展开:");
        levelTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        treeLevelRow = new HBox(4);
        treeLevelRow.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(4, searchRow, checkRow, filterIndicator, levelTitle, treeLevelRow);
        bar.setPadding(new Insets(4, 6, 4, 6));
        // 按恢复后的过滤偏好刷新标识（比对前 currentStatusMap 为空，仅反映类型组合）
        updateFilterIndicator();
        return bar;
    }

    /** 创建一个变更类型勾选框：选中态变更过滤标记并即时重建树（文件夹模式走 rebuildFolderTree）。 */
    private CheckBox makeFilterCheckBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setStyle("-fx-font-size: 11px;");
        cb.selectedProperty().addListener((obs, old, nv) -> {
            onChange.accept(nv);
            persistFilter();
            if (folderCompare && folderResult != null) rebuildFolderTree();
            else rebuildTree();
        });
        return cb;
    }

    /** 将当前差异树过滤偏好写入 UiConfig 并落盘（properties）。搜索词/勾选/正则均为用户偏好，非敏感信息。 */
    private void persistFilter() {
        config.setFilterSearch(treeFilter.search == null ? "" : treeFilter.search);
        config.setFilterRegex(treeFilter.regex);
        config.setFilterShowModified(treeFilter.showModified);
        config.setFilterShowAdded(treeFilter.showAdded);
        config.setFilterShowDeleted(treeFilter.showDeleted);
        config.setFilterShowUnchanged(treeFilter.showUnchanged);
        config.save();
    }

    /** 应用退出时兜底落盘过滤偏好（防止极端情况下最后一步变更未写盘）。 */
    @Override
    public void stop() {
        try {
            persistFilter();
        } catch (Exception ignored) {
            // 退出时落盘失败不阻断关闭
        }
    }

    /**
     * 构建比对进度遮罩层：半透明全窗口背景 + 居中卡片（不确定进度条 + 提示文字）。
     * 默认不可见；showBusyOverlay 时显示并禁用主布局，hideBusyOverlay 时恢复。
     */
    private StackPane buildBusyOverlay() {
        StackPane ov = new StackPane();
        ov.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
        ov.setAlignment(Pos.CENTER);
        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(18, 26, 18, 26));
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 10, 0, 0, 3);");
        ProgressBar pb = new ProgressBar();
        pb.setProgress(-1.0); // 不确定模式：比对耗时随包体浮动，无法预估百分比
        pb.setPrefWidth(240);
        pb.setPrefHeight(16);
        busyLabel = new Label("正在比对，请稍候…");
        busyLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #202124;");
        card.getChildren().addAll(pb, busyLabel);
        ov.getChildren().add(card);
        ov.setVisible(false);
        return ov;
    }

    /** 显示比对遮罩并禁用下方所有交互控件（防止大包比对慢时误触）。 */
    private void showBusyOverlay(String msg) {
        if (busyLabel != null && msg != null) busyLabel.setText(msg);
        if (mainBorderPane != null) mainBorderPane.setDisable(true);
        if (busyOverlay != null) busyOverlay.setVisible(true);
    }

    /** 隐藏比对遮罩并恢复下方交互控件。 */
    private void hideBusyOverlay() {
        if (busyOverlay != null) busyOverlay.setVisible(false);
        if (mainBorderPane != null) mainBorderPane.setDisable(false);
    }

    /** 展开树中所有节点（搜索命中时便于直接看到结果）。 */
    private void expandAll(TreeItem<String> item) {
        if (item == null) return;
        item.setExpanded(true);
        for (TreeItem<String> c : item.getChildren()) expandAll(c);
    }

    /**
     * 按当前树深度动态刷新「层级展开」控制条按钮：全部展开 / 全部合并 + 展开到 1..maxDepth 层。
     * 每次比对成功或过滤条件变化重建树后调用，使控制条始终匹配最新树结构。
     */
    private void refreshTreeLevelButtons() {
        if (treeLevelRow == null) return;
        treeLevelRow.getChildren().clear();
        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;
        int maxDepth = maxTreeDepth(root, 0);
        treeLevelRow.getChildren().add(smallTreeBtn("全部展开", e -> expandAll(root)));
        treeLevelRow.getChildren().add(smallTreeBtn("全部合并", e -> collapseAll(root)));
        for (int d = 1; d <= maxDepth; d++) {
            final int depth = d;
            treeLevelRow.getChildren().add(smallTreeBtn("展开到 " + depth + " 层", e -> setExpandedToDepth(root, 0, depth)));
        }
    }

    /** 生成一个小型 Bootstrap 风格按钮（用于差异树层级控制条）。 */
    private Button smallTreeBtn(String text, EventHandler<ActionEvent> h) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", BTN_DEFAULT);
        b.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        b.setOnAction(h);
        return b;
    }

    /** 计算树的最大深度（root 深度记为 0）。 */
    private int maxTreeDepth(TreeItem<String> item, int level) {
        int m = level;
        for (TreeItem<String> c : item.getChildren()) {
            m = Math.max(m, maxTreeDepth(c, level + 1));
        }
        return m;
    }

    /**
     * 展开到指定层级：节点自身 depth < targetDepth 时展开，否则折叠（root 恒展开）。
     * 例如 targetDepth=3 → root 与第 1、2 层节点展开，第 3 层及以下折叠，便于按机构层级逐级收看。
     */
    private void setExpandedToDepth(TreeItem<String> item, int level, int targetDepth) {
        item.setExpanded(true);
        for (TreeItem<String> c : item.getChildren()) {
            c.setExpanded((level + 1) < targetDepth);
            setExpandedToDepth(c, level + 1, targetDepth);
        }
    }

    /** 折叠全部子节点（保留 root 自身展开，确保用户始终能看到顶层入口，而非整棵树消失）。 */
    private void collapseAll(TreeItem<String> item) {
        for (TreeItem<String> c : item.getChildren()) {
            c.setExpanded(false);
            collapseAll(c);
        }
    }

    /** 更新过滤栏的"当前显示"文字标识（类型组合 + 搜索命中数）。 */
    private void updateFilterIndicator() {
        if (filterIndicator == null) return;
        List<String> on = new ArrayList<>();
        if (treeFilter.showModified) on.add("修改");
        if (treeFilter.showAdded) on.add("新增");
        if (treeFilter.showDeleted) on.add("删除");
        if (treeFilter.showUnchanged) on.add("未变");
        StringBuilder sb = new StringBuilder("显示: ");
        sb.append(on.isEmpty() ? "无（全部隐藏）" : on.size() == 4 ? "全部" : String.join("·", on));
        if (!treeFilter.search.isBlank()) {
            sb.append(" | 搜索: ").append(treeFilter.search);
            if (treeFilter.error() != null) sb.append(" (").append(treeFilter.error()).append(")");
            sb.append(" → ").append(currentStatusMap.size()).append(" 个文件");
        } else {
            sb.append(" | 共 ").append(currentStatusMap.size()).append(" 个文件");
        }
        filterIndicator.setText(sb.toString());
    }

    /** 按当前过滤条件重建差异树（不重新解析/比对，复用既有 diff 与快照）。 */
    private void rebuildTree() {
        if (diff == null) {
            return; // 尚未比对，仅记忆偏好，待下次比对生效
        }
        treeFilter.compileSearch();
        DiffTree.TreeResult r = DiffTree.build(diff, oldSnap, newSnap, treeFilter);
        treeView.setRoot(r.root);
        refreshTreeLevelButtons();
        currentStatusMap = r.statusMap;
        currentLayerMap = r.layerMap;
        if (!treeFilter.search.isBlank()) expandAll(r.root);
        updateFilterIndicator();
    }

    /** 由 FolderDiff 结果构建可展开差异树（folderCompare 模式），应用当前过滤条件。 */
    private TreeItem<String> buildFolderTree(FolderDiff.FolderDiffResult result, DiffTree.TreeFilter filter) {
        currentStatusMap.clear();
        currentLayerMap.clear();
        folderEntries.clear();
        treeFilter.compileSearch();
        Map<String, Boolean> visCache = new HashMap<>();
        TreeItem<String> root = new TreeItem<>("(根)");
        root.setExpanded(true);
        for (FolderDiff.FolderEntry e : result.roots) {
            if (folderNodeVisible(e, filter, visCache)) {
                addFolderNode(e, root, filter, visCache);
            }
        }
        return root;
    }

    /** 递归判断节点或其子树是否通过过滤（类型勾选 + 搜索）；结果缓存避免重复计算。 */
    private boolean folderNodeVisible(FolderDiff.FolderEntry e, DiffTree.TreeFilter filter, Map<String, Boolean> cache) {
        Boolean cached = cache.get(e.relPath);
        if (cached != null) return cached;
        DiffStatus ds = mapFolderStatus(e.status);
        boolean self = (filter == null) || (filter.statusEnabled(ds) && filter.matchesSearch(e.relPath));
        boolean childVisible = false;
        if (e.children != null) {
            for (FolderDiff.FolderEntry c : e.children) {
                if (folderNodeVisible(c, filter, cache)) { childVisible = true; break; }
            }
        }
        boolean visible = self || childVisible;
        cache.put(e.relPath, visible);
        return visible;
    }

    private void addFolderNode(FolderDiff.FolderEntry e, TreeItem<String> parent, DiffTree.TreeFilter filter, Map<String, Boolean> cache) {
        folderEntries.put(e.relPath, e);
        DiffStatus ds = mapFolderStatus(e.status);
        currentStatusMap.put(e.relPath, ds);
        currentLayerMap.put(e.relPath, e.type == FolderDiff.EntryType.DIR ? "DIR" : "FILE");

        TreeItem<String> node = new TreeItem<>(e.relPath);
        node.setExpanded(true);
        parent.getChildren().add(node);
        if (e.children != null) {
            for (FolderDiff.FolderEntry c : e.children) {
                if (cache.getOrDefault(c.relPath, Boolean.FALSE)) {
                    addFolderNode(c, node, filter, cache);
                }
            }
        }
    }

    /** FolderDiff 状态 → DiffStatus 映射（复用树单元格着色逻辑）。 */
    private DiffStatus mapFolderStatus(FolderDiff.FolderDiffStatus s) {
        switch (s) {
            case LEFT_ONLY:   return DiffStatus.DELETED;
            case RIGHT_ONLY:  return DiffStatus.ADDED;
            case MODIFIED:
            case TYPE_MISMATCH: return DiffStatus.MODIFIED;
            default:          return DiffStatus.UNCHANGED;
        }
    }

    private void rebuildFolderTree() {
        if (folderResult == null) return;
        treeView.setRoot(buildFolderTree(folderResult, treeFilter));
        refreshTreeLevelButtons();
        updateFilterIndicator();
        if (!treeFilter.search.isBlank() && treeView.getRoot() != null) expandAll(treeView.getRoot());
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
            alert("请先选择老包/新包，或两个文件夹");
            return;
        }
        oldPath = Paths.get(oldField.getText());
        newPath = Paths.get(newField.getText());
        if (!Files.exists(oldPath) || !Files.exists(newPath)) {
            alert("路径不存在，请重新选择");
            return;
        }
        // 两个路径均为目录 → 进入文件夹对比模式（手动输入路径也适用，不限于浏览选择）
        if (Files.isDirectory(oldPath) && Files.isDirectory(newPath)) {
            folderCompare = true;
        }
        showBusyOverlay("正在比对，请稍候…");
        statusBar.setText("比对中…");
        Task<DiffTree.TreeResult> task = new Task<DiffTree.TreeResult>() {
            @Override
            protected DiffTree.TreeResult call() throws Exception {
                // 每次比对前清掉上一次的包比对状态，避免文件夹模式误用旧快照
                diff = null;
                stats = null;
                // FR-CX-01：清空上一次 AI 分析残留，避免自动触发前的旧数据滞留右侧面板
                aiSummary = null;
                aiFiles.clear();
                if (folderCompare) {
                    // 文件夹对比：独立 FolderDiff 引擎（递归、名称/大小/修改时间/内容哈希、逐项错误隔离）
                    folderResult = FolderDiff.compare(oldPath, newPath, new FolderDiff.Options());
                    return null;
                }
                DiffEngine engine = new DiffEngine();
                PackageParser parser = new PackageParser();
                ParseConfig pc = config.toParseConfig();
                oldSnap = parser.parse(oldPath, pc, config.isExpandAll());
                newSnap = parser.parse(newPath, pc, config.isExpandAll());
                diff = engine.compute(oldSnap, newSnap);
                stats = engine.stats(diff);
                return DiffTree.build(diff, oldSnap, newSnap, treeFilter);
            }
        };
        task.setOnSucceeded(e -> {
            hideBusyOverlay();
            if (folderCompare && folderResult != null) {
                treeView.setRoot(buildFolderTree(folderResult, treeFilter));
                refreshTreeLevelButtons();
                if (!treeFilter.search.isBlank()) expandAll(treeView.getRoot());
                updateFilterIndicator();
                statusBar.setText("文件夹比对完成：" + folderResult.summary);
                summaryLabel.setText("文件夹对比结果\n" + folderResult.summary);
                audit("文件夹比对 " + oldPath.getFileName() + " → " + newPath.getFileName() + " | " + folderResult.summary);
            } else {
                DiffTree.TreeResult r = task.getValue();
                treeView.setRoot(r.root);
                refreshTreeLevelButtons();
                currentStatusMap = r.statusMap;
                currentLayerMap = r.layerMap;
                if (!treeFilter.search.isBlank()) expandAll(r.root);
                updateFilterIndicator();
                statusBar.setText("比对完成：" + stats);
                audit("比对 " + oldPath.getFileName() + " → " + newPath.getFileName() + " | " + stats);
                // FR-CX-01：开始比对成功后自动触发 AI 两阶段分析（等价 report --ai），
                // 让右侧面板与报告自动具备智能化差异说明；开关见工具栏「自动 AI」（默认开）。
                // 仅在启用 AI 且已配置 Key 时自动跑真实分析，避免无 Key 时自动产生 Mock 噪声。
                if (config.isAutoAiOnCompare() && config.isAiEnabled() && !config.getAiApiKey().isEmpty()) {
                    startAiAnalysisTask();
                }
            }
        });
        task.setOnFailed(e -> {
            hideBusyOverlay();
            statusBar.setText("比对失败");
            alert("比对失败：" + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    private void onTreeSelect(TreeItem<String> node) {
        String key = resolveTreeKey(node);
        if (key == null) return;

        // 文件夹比较模式：文本类文件直接做行级内容 diff（复用 DiffRowsBuilder）
        if (folderCompare) {
            showFolderDiff(key);
            return;
        }

        // 如果该文件的 diff Tab 已存在，直接切换到它（不重复加载）
        Tab existing = openDiffTabs.get(key);
        if (existing != null) {
            diffTabPane.getSelectionModel().select(existing);
            statusBar.setText("已切换到: " + key);
            return;
        }

        LogicalEntry oe = oldSnap.getEntries().get(key);
        LogicalEntry ne = newSnap.getEntries().get(key);
        boolean isClass = (oe != null && oe.getFileClass() == FileClass.CLASS && oe.getLayer() == Layer.L1)
                || (ne != null && ne.getFileClass() == FileClass.CLASS && ne.getLayer() == Layer.L1);
        boolean isText = (oe != null && oe.getFileClass().isTextDiffable())
                || (ne != null && ne.getFileClass().isTextDiffable());
        if (!isClass && !isText) {
            showUnsupportedTab(key);
            return;
        }

        statusBar.setText("加载 " + key + " …");
        Task<DecompiledUnit> t = new Task<DecompiledUnit>() {
            @Override
            protected DecompiledUnit call() throws Exception {
                if (isText) {
                    FileClass fc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.JS);
                    return new FrontendTextDiff().diff(oldSnap, newSnap, oe, ne, key, fc);
                }
                Decompiler dec = new Decompiler(cfrOrNull(), findJava());
                return dec.decompile(oldSnap, newSnap, oe, ne, key);
            }
        };
        t.setOnSucceeded(e -> {
            DecompiledUnit u = t.getValue();
            if (u.isOk()) {
                showDiffViewTab(key, u, oe, ne);
            } else {
                showErrorTab(key, u.getError());
            }
            statusBar.setText("反编译完成：" + key);
        });
        t.setOnFailed(e -> statusBar.setText("反编译失败：" + t.getException().getMessage()));
        new Thread(t).start();
    }

    /**
     * 将反编译结果渲染为 Beyond Compare 风格的 DiffView，放入新 Tab。
     * 如果是同名文件（key 相同）但已打开过，则复用已有 Tab。
     */
    private void showDiffViewTab(String key, DecompiledUnit u, LogicalEntry oe, LogicalEntry ne) {
        // 构建文件路径标题
        String leftPath = buildFilePath(oe, key, oldPath);
        String rightPath = buildFilePath(ne, key, newPath);

        // 用 DiffRowsBuilder 生成双栏对齐行
        List<DiffRow> rows = DiffRowsBuilder.build(u.getOldSource(), u.getNewSource());

        openDiffTab(key, rows, leftPath, rightPath);
    }

    /**
     * 创建/复用"对比视图"Tab（左侧路径、右侧路径、双栏行）。供包反编译与文件夹文本 diff 共用。
     */
    private void openDiffTab(String key, List<DiffRow> rows, String leftPath, String rightPath) {
        String shortName = key.substring(key.lastIndexOf('/') + 1);

        BorderPane view = DiffView.create(leftPath, rightPath, rows);

        Tab tab;
        Tab existing = openDiffTabs.get(key);
        if (existing != null) {
            tab = existing;
            tab.setContent(view);
        } else {
            tab = new Tab(shortName, view);
            tab.setClosable(true);
            tab.setOnClosed(e -> openDiffTabs.remove(key));
            openDiffTabs.put(key, tab);

            if (diffTabPane.getTabs().size() == 1 && !diffTabPane.getTabs().get(0).isClosable()) {
                diffTabPane.getTabs().remove(0);
            }

            diffTabPane.getTabs().add(tab);
        }

        diffTabPane.getSelectionModel().select(tab);
        audit("查看差异: " + key);
    }

    /** 文件夹模式：双击差异条目 -> 展开查看具体不同之处。
     *  修改的文本文件做行级内容 diff；仅属性不同/二进制/超大文件给出属性变更说明。 */
    private void showFolderDiff(String key) {
        FolderDiff.FolderEntry e = folderEntries.get(key);
        if (e == null) {
            showFolderNoteTab(key, "未找到该条目");
            return;
        }
        switch (e.status) {
            case LEFT_ONLY:
                showFolderNoteTab(key, "仅左侧存在（右侧已删除）\n\n" + (e.leftPath != null ? e.leftPath : key));
                return;
            case RIGHT_ONLY:
                showFolderNoteTab(key, "仅右侧存在（左侧新增）\n\n" + (e.rightPath != null ? e.rightPath : key));
                return;
            case TYPE_MISMATCH:
                showFolderNoteTab(key, "类型冲突：一侧为文件、另一侧为目录，无法逐内容对比");
                return;
            case SAME:
                showFolderNoteTab(key, "两侧内容完全一致（名称/大小/修改时间/内容均相同）");
                return;
            default:
                break;
        }
        // MODIFIED：内容不同且为文本文件 -> 行级 diff；否则给出属性变更说明
        if (e.isContentModified() && e.leftPath != null && e.rightPath != null) {
            try {
                String oldText = FolderParser.readText(Paths.get(e.leftPath));
                String newText = FolderParser.readText(Paths.get(e.rightPath));
                List<DiffRow> rows = DiffRowsBuilder.build(oldText, newText);
                openDiffTab(key, rows, e.leftPath, e.rightPath);
                return;
            } catch (java.io.IOException ex) {
                LOG.warning("读取文件失败，改显示属性变更: " + ex.getMessage());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("两侧均存在但存在差异（").append(e.type == FolderDiff.EntryType.DIR ? "目录" : "文件").append("）\n\n");
        if (e.attrChanges.contains(FolderDiff.AttrChange.SIZE))
            sb.append("大小: ").append(FolderDiff.fmtSize(e.sizeLeft)).append(" → ").append(FolderDiff.fmtSize(e.sizeRight)).append('\n');
        if (e.attrChanges.contains(FolderDiff.AttrChange.MTIME))
            sb.append("修改时间: ").append(FolderDiff.fmtMtime(e.mtimeLeft)).append(" → ").append(FolderDiff.fmtMtime(e.mtimeRight)).append('\n');
        if (e.attrChanges.contains(FolderDiff.AttrChange.CONTENT))
            sb.append("内容: 不同（二进制或超大文件，未做行级 diff；如需可导出后人工比对）\n");
        if (e.attrChanges.contains(FolderDiff.AttrChange.TYPE))
            sb.append("类型: 一侧为文件、另一侧为目录\n");
        showFolderNoteTab(key, sb.toString());
    }

    /** 文件夹模式：以信息面板展示条目说明（中性，非"不支持"）。 */
    private void showFolderNoteTab(String key, String msg) {
        String shortName = key.substring(key.lastIndexOf('/') + 1);
        Label placeholder = new Label("  " + msg);
        placeholder.setStyle("-fx-text-fill: #202124; -fx-font-size: 13px; -fx-padding: 20;");
        Tab tab = new Tab(shortName, new ScrollPane(placeholder));
        tab.setClosable(true);
        removeDefaultEmptyTab();
        diffTabPane.getTabs().add(tab);
        diffTabPane.getSelectionModel().select(tab);
        openDiffTabs.put(key, tab);
        tab.setOnClosed(e -> openDiffTabs.remove(key));
    }

    /** 读文件文本，失败返回空串（文件夹模式双击查看时尽量不崩溃）。 */
    private String safeRead(Path p) {
        try {
            return FolderParser.readText(p);
        } catch (java.io.IOException ex) {
            return "";
        }
    }

    /** 显示"不支持源码级 diff"的占位 Tab。 */
    private void showUnsupportedTab(String key) {
        String shortName = key.substring(key.lastIndexOf('/') + 1);
        Label placeholder = new Label("  该文件类型不支持源码级差异对比\n\n"
                + "  文件: " + key + "\n\n"
                + "  提示: Java .class 支持反编译对比；配置文件(.xml/.properties 等)/JSP(.jsp/.tag)/"
                + "前端 JS/HTML/CSS 支持内容级逐行对比；图片/字体等二进制仅做哈希比对");
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 20;");
        Tab tab = new Tab(shortName, new ScrollPane(placeholder));
        tab.setClosable(true);
        removeDefaultEmptyTab();
        diffTabPane.getTabs().add(tab);
        diffTabPane.getSelectionModel().select(tab);
        openDiffTabs.put(key, tab);
        tab.setOnClosed(e -> openDiffTabs.remove(key));
    }

    /** 文件夹模式：二进制/超大文件不支持内容 diff 时的占位 Tab（结构级差异已在左侧树标注）。 */
    private void showFolderUnsupportedTab(String key) {
        String shortName = key.substring(key.lastIndexOf('/') + 1);
        Label placeholder = new Label("  该文件为二进制或过大，仅做结构级差异识别\n\n"
                + "  文件: " + key + "\n\n"
                + "  提示: 已在左侧差异树中按 新增 / 修改 / 删除 标注；"
                + "文本类文件（如 .java/.txt/.md/.xml/.properties/.jsp/.js/.html/.css）可双击查看内容差异");
        placeholder.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 20;");
        Tab tab = new Tab(shortName, new ScrollPane(placeholder));
        tab.setClosable(true);
        removeDefaultEmptyTab();
        diffTabPane.getTabs().add(tab);
        diffTabPane.getSelectionModel().select(tab);
        openDiffTabs.put(key, tab);
        tab.setOnClosed(e -> openDiffTabs.remove(key));
    }

    /** 显示反编译错误的占位 Tab。 */
    private void showErrorTab(String key, String error) {
        String shortName = key.substring(key.lastIndexOf('/') + 1);
        Label errLabel = new Label("  反编译失败\n\n  文件: " + key + "\n  错误: " + error);
        errLabel.setStyle("-fx-text-fill: #d93025; -fx-font-size: 13px; -fx-padding: 20;");
        Tab tab = new Tab(shortName, new ScrollPane(errLabel));
        tab.setClosable(true);
        removeDefaultEmptyTab();
        diffTabPane.getTabs().add(tab);
        diffTabPane.getSelectionModel().select(tab);
        openDiffTabs.put(key, tab);
        tab.setOnClosed(e -> openDiffTabs.remove(key));
    }

    /** 移除默认空状态 Tab（如果还存在）。 */
    private void removeDefaultEmptyTab() {
        if (!diffTabPane.getTabs().isEmpty() && !diffTabPane.getTabs().get(0).isClosable()) {
            diffTabPane.getTabs().remove(0);
        }
    }

    /** 构建显示用的文件路径字符串。 */
    private static String buildFilePath(LogicalEntry entry, String fallbackKey, Path packagePath) {
        if (entry != null && entry.getSrc() != null) {
            String outer = entry.getSrc().getOuterEntry();
            String inner = entry.getSrc().getInnerEntry();
            if (inner != null) return inner;
            if (outer != null) return outer;
        }
        if (packagePath != null) {
            return packagePath.getFileName().toString() + " | " + fallbackKey;
        }
        return fallbackKey;
    }

    private String resolveTreeKey(TreeItem<String> node) {
        if (node == null) return null;
        String key = node.getValue();
        if (key == null) return null;
        if (folderCompare) {
            // 文件夹模式：key 即相对路径，查 folderEntries 即可（不依赖包快照）
            return folderEntries.containsKey(key) ? key : null;
        }
        if (!currentStatusMap.containsKey(key)) return null;
        if (oldSnap == null || newSnap == null) return null;
        return key;
    }

    private void onAnalyze() {
        if (diff == null) {
            alert(MSG_COMPARE_FIRST);
            return;
        }
        startAiAnalysisTask();
    }

    /**
     * 启动两阶段 AI 分析后台任务（「AI 分析」按钮与「开始比对」自动触发共用，等价 report --ai）。
     * 先解 L1 类与前端源码，再跑 stageA(整体)+stageB(逐文件)，结果填充 aiSummary/aiFiles 并刷新右侧面板。
     */
    private void startAiAnalysisTask() {
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
                // 文本类文件（配置文件/JSP/JS/HTML/CSS）：纳入 stageA 摘要与 stageB 深读
                FrontendTextDiff ftd = new FrontendTextDiff();
                List<String> feCands = DiffEngine.collectTextDiffCandidates(diff, oldSnap, newSnap);
                int feN = Math.min(feCands.size(), config.getStageBTopK());
                for (int i = 0; i < feN; i++) {
                    String k = feCands.get(i);
                    LogicalEntry oe = oldSnap.getEntries().get(k);
                    LogicalEntry ne = newSnap.getEntries().get(k);
                    FileClass ffc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.JS);
                    decompiled.put(k, ftd.diff(oldSnap, newSnap, oe, ne, k, ffc));
                }
                AiConfig aiCfg = config.toAiConfig();
                Path replayDir = Paths.get(System.getProperty("user.home"), ".bempdiff", "ai_replay");
                AiAnalyzer ai = (config.isAiEnabled() && !config.getAiApiKey().isEmpty())
                        ? new HttpAiAnalyzer(aiCfg) : new MockAiAnalyzer(replayDir);
                // 项目级上下文增强：可选 --project 等价物（设置面板「工程目录」+「启用」），
                // 离线扫描工程结构作为 AI 分析依据；扫描失败静默降级为 null（不影响基础比对）。
                ProjectContext ctx = null;
                if (config.isProjectContextEnabled() && config.getProjectContextDir() != null
                        && !config.getProjectContextDir().trim().isEmpty()) {
                    try {
                        ctx = ProjectContextAnalyzer.analyze(Paths.get(config.getProjectContextDir().trim()));
                    } catch (RuntimeException ex) {
                        LOG.warning("项目级上下文扫描失败，已忽略: " + ex.getMessage());
                        ctx = null;
                    }
                }
                try {
                    aiSummary = ai.stageA(diff, decompiled, aiCfg, ctx);
                    List<AiAnalyzer.DecompileReq> reqs = new ArrayList<>();
                    for (Map.Entry<String, DecompiledUnit> e : decompiled.entrySet()) {
                        String k = e.getKey();
                        LogicalEntry oe = oldSnap.getEntries().get(k);
                        LogicalEntry ne = newSnap.getEntries().get(k);
                        FileClass fc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.CLASS);
                        reqs.add(new AiAnalyzer.DecompileReq(k, e.getValue(), fc));
                    }
                    aiFiles.clear();
                    aiFiles.addAll(ai.stageB(reqs, aiCfg, ctx));
                } catch (RuntimeException ex) {
                    aiSummary = new StageASummary();
                    aiSummary.setOverallRisk("UNKNOWN");
                    aiSummary.setImpactScope("AI 服务不可用：" + ex.getMessage());
                    aiSummary.setTestThemes(Arrays.asList("人工核对全部差异文件", "回归核心业务流程"));
                    aiSummary.setFileRisks(new ArrayList<>());
                    aiFiles.clear();
                }
                // 透传 ctx 给报告渲染（让 AI 章节携带项目级上下文影响说明）
                lastProjectContext = ctx;
                return null;
            }
        };
        t.setOnSucceeded(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("整体风险: ").append(aiSummary.getOverallRisk())
              .append("\n影响范围: ").append(aiSummary.getImpactScope());
            if (lastProjectContext != null
                    && aiSummary.getContextInfluence() != null && !aiSummary.getContextInfluence().isEmpty()) {
                sb.append("\n\n【项目级上下文影响】\n").append(aiSummary.getContextInfluence());
            } else if (lastProjectContext != null) {
                sb.append("\n\n[项目级上下文已启用：").append(lastProjectContext.getBuildSystem())
                  .append(" / ").append(lastProjectContext.getModules().size()).append(" 模块]");
            }
            sb.append("\n\n测试主题: ").append(aiSummary.getTestThemes());
            summaryLabel.setText(sb.toString());
            fileList.getItems().clear();
            for (FileAnalysis fa : aiFiles) {
                StringBuilder line = new StringBuilder();
                line.append(fa.getKey()).append(" | ").append(fa.getRisk()).append(" | ").append(fa.getIntent());
                if (lastProjectContext != null
                        && fa.getContextInfluence() != null && !fa.getContextInfluence().isEmpty()) {
                    line.append(" | 上下文: ").append(fa.getContextInfluence());
                }
                fileList.getItems().add(line.toString());
            }
            destructiveList.getItems().clear();
            for (String s : DiffTree.destructiveChanges(diff, oldSnap)) {
                destructiveList.getItems().add(s);
            }
            statusBar.setText("AI 分析完成" + (lastProjectContext != null ? "（含项目级上下文）" : ""));
            audit("AI 分析 | provider=" + config.getAiProvider() + " | 深读=" + aiFiles.size()
                    + " | 项目上下文=" + (lastProjectContext != null));
        });
        t.setOnFailed(e -> {
            statusBar.setText("AI 分析失败");
            alert("AI 分析失败：" + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    private void onPreviewReport() {
        if (diff == null) {
            alert(MSG_COMPARE_FIRST);
            return;
        }
        statusBar.setText("生成报告预览…");
        Task<String> t = makeReportTask();
        t.setOnSucceeded(e -> {
            ReportViewer.show(stage, t.getValue(), "差异分析报告预览");
            statusBar.setText("报告预览已打开");
        });
        t.setOnFailed(e -> {
            statusBar.setText("生成报告失败");
            alert("生成报告失败：" + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    private void onOpenReport() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.txt"));
        fc.setInitialFileName("diff_report.md");
        java.io.File f = fc.showOpenDialog(stage);
        if (f != null) {
            ReportViewer.showFile(stage, f.toPath());
        }
    }

    /** 构建报告 Markdown 字符串（预览与导出共用，避免重复逻辑）。 */
    private Task<String> makeReportTask() {
        return new Task<String>() {
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
                Map<String, DecompiledUnit> fe = buildTextMap(oldSnap, newSnap, diff, config.getTopK());
                // 若已执行过 AI 分析（aiSummary 非空），则报告携带 AI 智能分析章节与项目级上下文
                if (aiSummary != null) {
                    return new MarkdownReport(config.getTopK()).render(oldSnap, newSnap, diff, stats, dd, fe,
                            aiSummary, aiFiles, lastProjectContext);
                }
                return new MarkdownReport(config.getTopK()).render(oldSnap, newSnap, diff, stats, dd, fe);
            }
        };
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
        final File outFile = f;
        Task<String> t = makeReportTask();
        t.setOnSucceeded(e -> {
            try {
                Files.writeString(outFile.toPath(), t.getValue(), StandardCharsets.UTF_8);
                statusBar.setText("报告已导出：" + outFile);
                audit("导出报告 " + outFile);
            } catch (java.io.IOException ex) {
                statusBar.setText("写入报告失败");
                alert("写入报告失败：" + ex.getMessage());
            }
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
                // 文本类文件（配置文件/JSP/前端 JS/HTML/CSS）一并纳入源码导出 zip
                dd.putAll(buildTextMap(oldSnap, newSnap, diff, config.getTopK()));
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

    /** 构建文本类文件内容 diff 集合（CONFIG/JSP/JS/HTML/CSS），供报告与资产导出复用。 */
    private Map<String, DecompiledUnit> buildTextMap(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                                     DiffResult diff, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        FrontendTextDiff ftd = new FrontendTextDiff();
        List<String> feCands = DiffEngine.collectTextDiffCandidates(diff, oldSnap, newSnap);
        int feN = Math.min(feCands.size(), topK);
        for (int i = 0; i < feN; i++) {
            String k = feCands.get(i);
            LogicalEntry oe = oldSnap.getEntries().get(k);
            LogicalEntry ne = newSnap.getEntries().get(k);
            FileClass ffc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.JS);
            m.put(k, ftd.diff(oldSnap, newSnap, oe, ne, k, ffc));
        }
        return m;
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
                // App 是 JavaFX Application，入口被 JavaFX Launcher 接管，
                // toolkit 初始化后会保活 JVM；批处理为纯命令行流程，必须显式退出，
                // 否则命令执行完毕后进程不终止（挂起）。
                System.exit(0);
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
            case "compare-folders":
            case "folderdiff":
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