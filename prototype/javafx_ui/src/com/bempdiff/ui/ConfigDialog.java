package com.bempdiff.ui;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.AiProfile;
import com.bempdiff.config.AiProfilesStore;
import com.bempdiff.config.AiVendorConfigStore;
import com.bempdiff.config.LlmPreset;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 设置弹窗（FR9：所有配置在 UI 有入口）。AI 服务 / 解析与导出 两页。
 *
 * <p>AI 服务页支持（参考 Karpathy 项目 LlmPreset + BYOK 多配置）：
 * <ul>
 *   <li>厂商预设切换：下拉选择 OpenAI/DeepSeek/通义千问 等，自动填入 Base URL 与默认模型</li>
 *   <li>获取 API Key 超链接：随当前预设变化，一键打开官方申请页</li>
 *   <li>多配置保存：命名保存当前连接三元组，可切换/删除，切换时返显全部字段</li>
 * </ul>
 * 连接测试按钮对应 FR9.4。
 */
public class ConfigDialog {

    private static final String FORM_CONTROL = "form-control";

    private ConfigDialog() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static void show(Stage owner, UiConfig cfg, HostServices host, Label statusBar) {
        // 悬停提示写回主窗口底部状态栏：捕获原文本，弹窗期间显示就绪提示，关闭后还原
        String originalStatus = (statusBar != null) ? statusBar.getText() : "";
        String hintDefault = "设置：将鼠标悬停在任意字段上，可在窗口底部（状态栏）查看该字段的说明。";
        if (statusBar != null) statusBar.setText(hintDefault);
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("设置");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TabPane tabs = new TabPane();

        // ==================== AI 服务页 ====================
        GridPane ai = new GridPane();
        ai.setHgap(8); ai.setVgap(8); ai.setPadding(new Insets(10));

        // ---- 内置厂商预设 ----
        ComboBox<LlmPreset> presetCombo = new ComboBox<>();
        presetCombo.getItems().addAll(LlmPreset.builtinPresets());
        presetCombo.getStyleClass().add(FORM_CONTROL);
        // 若当前 provider 命中某个内置预设，则预选
        LlmPreset currentPreset = LlmPreset.byKey(cfg.getAiProvider());
        if (currentPreset != null) presetCombo.setValue(currentPreset);

        // 获取 API Key 超链接（随预设变化）
        Hyperlink keyLink = new Hyperlink();
        keyLink.setStyle("-fx-text-fill: #1a73e8; -fx-border-color: transparent;");
        keyLink.setVisited(false);
        keyLink.setOnAction(e -> {
            if (keyLink.getUserData() instanceof String url && !url.isEmpty()) {
                if (host != null) host.showDocument(url);
            }
        });
        Runnable refreshKeyLink = () -> {
            LlmPreset p = presetCombo.getValue();
            if (p != null && p.getApiKeyUrl() != null && !p.getApiKeyUrl().isEmpty()) {
                keyLink.setText("获取 " + p.getLabel() + " API Key ↗");
                keyLink.setUserData(p.getApiKeyUrl());
                keyLink.setDisable(false);
                keyLink.setVisible(true);
            } else {
                keyLink.setText("获取 API Key ↗");
                keyLink.setUserData(null);
                keyLink.setDisable(true);
                keyLink.setVisible(true);
            }
        };
        refreshKeyLink.run();

        // ---- 我的命名配置（多配置保存/切换） ----
        AiProfilesStore store = new AiProfilesStore(
                cfg.getConfigFile().resolveSibling("ai-profiles.properties"));
        ComboBox<AiProfile> profileCombo = new ComboBox<>();
        Runnable reloadProfiles = () -> {
            List<AiProfile> profiles = store.load();
            profileCombo.getItems().setAll(profiles);
        };
        reloadProfiles.run();
        profileCombo.getStyleClass().add(FORM_CONTROL);
        Button saveProfileBtn = new Button("保存当前为配置");
        saveProfileBtn.getStyleClass().addAll("btn", "btn-default");
        Button delProfileBtn = new Button("删除");
        delProfileBtn.getStyleClass().addAll("btn", "btn-default");

        // ---- 连接字段 ----
        ComboBox<String> provider = new ComboBox<>();
        List<String> providerKeys = new ArrayList<>();
        for (LlmPreset p : LlmPreset.builtinPresets()) providerKeys.add(p.getKey());
        providerKeys.add("custom");
        provider.getItems().addAll(providerKeys);
        provider.setValue(cfg.getAiProvider());
        provider.getStyleClass().add(FORM_CONTROL);
        TextField baseUrl = new TextField(cfg.getAiBaseUrl());
        baseUrl.getStyleClass().add(FORM_CONTROL);
        PasswordField apiKey = new PasswordField();
        apiKey.setText(cfg.getAiApiKey());
        apiKey.getStyleClass().add(FORM_CONTROL);
        TextField model = new TextField(cfg.getAiModel());
        model.getStyleClass().add(FORM_CONTROL);
        CheckBox persistKey = new CheckBox("记住 API Key（明文存盘，默认关闭更安全）");
        persistKey.setSelected(cfg.isPersistApiKey());
        CheckBox aiEnabled = new CheckBox("启用 AI 智能分析");
        aiEnabled.setSelected(cfg.isAiEnabled());
        TextField stageBTopK = new TextField(String.valueOf(cfg.getStageBTopK()));
        stageBTopK.getStyleClass().add(FORM_CONTROL);
        TextField costGate = new TextField(String.valueOf(cfg.getCostGateWarnTokens()));
        costGate.getStyleClass().add(FORM_CONTROL);
        TextField httpProxy = new TextField(cfg.getHttpProxy());
        httpProxy.getStyleClass().add(FORM_CONTROL);
        TextField httpsProxy = new TextField(cfg.getHttpsProxy());
        httpsProxy.getStyleClass().add(FORM_CONTROL);
        Button testBtn = new Button("连接测试");
        testBtn.getStyleClass().addAll("btn", "btn-default");
        Label testOut = new Label();

        // ---- 厂商独立配置（切换厂商不覆盖已保存配置，互不影响，切回可恢复） ----
        AiVendorConfigStore vendorStore = new AiVendorConfigStore(
                cfg.getConfigFile().resolveSibling("ai-vendor-config.properties"));
        // 内存快照：每个 provider 独立保留 baseUrl/apiKey/model；apiKey 始终在内存中记住，
        // 仅 persistKey=true 时才随 saveAll 落盘（安全约定）。
        Map<String, AiVendorConfigStore.Snapshot> vendorMap =
                new LinkedHashMap<>(vendorStore.loadAll());
        // 当前激活厂商（手动跟踪，因 setOnAction 触发时 provider.getValue() 已变）
        final String[] currentVendor = { cfg.getAiProvider() };

        // 切换厂商核心逻辑：先存当前厂商 → 再恢复/初始化目标厂商
        java.util.function.Consumer<String> applyVendor = (targetKey) -> {
            if (targetKey == null) return;
            // 1) 保存当前厂商快照（不触碰其它厂商）
            vendorMap.put(currentVendor[0], new AiVendorConfigStore.Snapshot(
                    baseUrl.getText(), apiKey.getText(), model.getText()));
            // 2) 恢复目标厂商：有快照用快照；否则按内置预设默认值（新厂商 Key 留空待填）
            currentVendor[0] = targetKey;
            provider.setValue(targetKey);
            LlmPreset p = LlmPreset.byKey(targetKey);
            presetCombo.setValue(p);
            AiVendorConfigStore.Snapshot saved = vendorMap.get(targetKey);
            if (saved != null) {
                baseUrl.setText(saved.baseUrl());
                apiKey.setText(saved.apiKey());
                model.setText(saved.model());
            } else if (p != null) {
                // 已配置过的内置厂商：填入其官方默认 baseUrl/model，Key 各自独立留空
                baseUrl.setText(p.getBaseUrl());
                model.setText(p.getModel());
                apiKey.setText("");
            } else {
                // custom 且无快照：清空供用户手填
                baseUrl.setText("");
                apiKey.setText("");
                model.setText("");
            }
            refreshKeyLink.run();
        };

        // ---- 预设切换：自动填充 baseUrl/model 并刷新链接 ----
        presetCombo.setOnAction(e -> {
            LlmPreset p = presetCombo.getValue();
            if (p == null) return;
            applyVendor.accept(p.getKey());
        });

        // ---- 直接改 provider 下拉：同样走厂商独立记忆 ----
        provider.setOnAction(e -> {
            String v = provider.getValue();
            if (v != null) applyVendor.accept(v);
        });

        // ---- 命名配置切换：返显全部字段 ----
        profileCombo.setOnAction(e -> {
            AiProfile pf = profileCombo.getValue();
            if (pf == null) return;
            // 保存切换前厂商快照，避免丢失未保存编辑
            vendorMap.put(currentVendor[0], new AiVendorConfigStore.Snapshot(
                    baseUrl.getText(), apiKey.getText(), model.getText()));
            // 应用命名配置
            provider.setValue(pf.getProvider());
            baseUrl.setText(pf.getBaseUrl());
            apiKey.setText(pf.getApiKey() == null ? "" : pf.getApiKey());
            model.setText(pf.getModel());
            // 若命中内置预设，联动预设下拉与链接
            LlmPreset p = LlmPreset.byKey(pf.getProvider());
            presetCombo.setValue(p);
            currentVendor[0] = pf.getProvider();
            refreshKeyLink.run();
        });

        // ---- 保存命名配置 ----
        saveProfileBtn.setOnAction(e -> {
            String defaultName = profileCombo.getValue() != null ? profileCombo.getValue().getName()
                    : (presetCombo.getValue() != null ? presetCombo.getValue().getLabel() : "我的配置");
            TextInputDialog d = new TextInputDialog(defaultName);
            d.setTitle("保存 LLM 配置");
            d.setHeaderText("给这套配置起个名字（便于后续切换返显）");
            d.setContentText("配置名称:");
            Optional<String> opt = d.showAndWait();
            if (!opt.isPresent()) return;
            String name = opt.get().trim();
            if (name.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "配置名称不能为空").showAndWait();
                return;
            }
            AiProfile pf = new AiProfile(name, provider.getValue(),
                    baseUrl.getText().trim(), apiKey.getText(), model.getText());
            store.upsert(pf, persistKey.isSelected());
            reloadProfiles.run();
            // 选中刚保存的
            for (AiProfile item : profileCombo.getItems()) {
                if (name.equals(item.getName())) { profileCombo.setValue(item); break; }
            }
            testOut.setStyle("-text-fill: green;");
            testOut.setText("已保存配置：" + name);
        });

        // ---- 删除命名配置 ----
        delProfileBtn.setOnAction(e -> {
            AiProfile sel = profileCombo.getValue();
            if (sel == null) {
                new Alert(Alert.AlertType.WARNING, "请先在下拉中选择要删除的配置").showAndWait();
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确认删除配置「" + sel.getName() + "」？", ButtonType.OK, ButtonType.CANCEL);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            store.delete(sel.getName());
            reloadProfiles.run();
            testOut.setStyle("");
            testOut.setText("已删除配置：" + sel.getName());
        });

        // ---- 连接测试 ----
        testBtn.setOnAction(e -> {
            AiConfig ac = cfg.toAiConfig();
            ac.setProvider(provider.getValue());
            ac.setBaseUrl(baseUrl.getText().trim());
            ac.setApiKey(apiKey.getText());
            ac.setModel(model.getText());
            HttpAiAnalyzer tester = new HttpAiAnalyzer(ac);
            boolean ok = tester.testConnection(ac);
            if (ok) {
                testOut.setStyle("-text-fill: green;");
                testOut.setText("连接成功");
            } else {
                testOut.setStyle("-text-fill: red;");
                String detail = tester.getLastError();
                testOut.setText(detail != null ? "连接失败: " + detail : "连接失败（检查 URL/Key/网络）");
            }
        });

        // ---- 布局 ----
        int r = 0;
        ai.add(new Label("厂商预设"), 0, r); ai.add(presetCombo, 1, r);
        ai.add(keyLink, 2, r++);
        ai.add(new Label("我的配置"), 0, r); ai.add(profileCombo, 1, r);
        HBox profBtns = new HBox(6, saveProfileBtn, delProfileBtn);
        ai.add(profBtns, 2, r++);
        ai.add(new Label("Provider"), 0, r); ai.add(provider, 1, r++);
        ai.add(new Label("Base URL"), 0, r); ai.add(baseUrl, 1, r++);
        ai.add(new Label("API Key"), 0, r); ai.add(apiKey, 1, r++);
        ai.add(new Label("模型"), 0, r); ai.add(model, 1, r++);
        ai.add(aiEnabled, 1, r++);
        ai.add(new Label("阶段B 深读 Top-K"), 0, r); ai.add(stageBTopK, 1, r++);
        ai.add(new Label("成本闸门(tokens)"), 0, r); ai.add(costGate, 1, r++);
        ai.add(new Label("HTTP 代理"), 0, r); ai.add(httpProxy, 1, r++);
        ai.add(new Label("HTTPS 代理"), 0, r); ai.add(httpsProxy, 1, r++);
        CheckBox blockPrivate = new CheckBox("严格 SSRF（拒绝回环/私网，本地 Ollama 需关闭）");
        blockPrivate.setSelected(cfg.isBlockPrivateEndpoints());
        ai.add(blockPrivate, 1, r++);
        ai.add(persistKey, 1, r++);
        ai.add(testBtn, 0, r); ai.add(testOut, 1, r);
        Tab aiTab = new Tab("AI 服务", ai);
        aiTab.setClosable(false);

        // ==================== 解析与导出页 ====================
        GridPane parse = new GridPane();
        parse.setHgap(8); parse.setVgap(8); parse.setPadding(new Insets(10));
        TextField internalPrefixes = new TextField(cfg.getInternalPrefixes());
        internalPrefixes.getStyleClass().add(FORM_CONTROL);
        CheckBox expandAll = new CheckBox("递归展开 lib（全部 class，可能很慢）");
        expandAll.setSelected(cfg.isExpandAll());
        TextField topK = new TextField(String.valueOf(cfg.getTopK()));
        topK.getStyleClass().add(FORM_CONTROL);
        TextField cfrJar = new TextField(cfg.getCfrJar());
        cfrJar.getStyleClass().add(FORM_CONTROL);
        Button browse = new Button("浏览...");
        browse.getStyleClass().addAll("btn", "btn-default");
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CFR jar", "*.jar"));
            File f = fc.showOpenDialog(owner);
            if (f != null) cfrJar.setText(f.getAbsolutePath());
        });
        int pr = 0;
        parse.add(new Label("内部业务码包前缀(逗号分)"), 0, pr); parse.add(internalPrefixes, 1, pr++);
        parse.add(expandAll, 1, pr++);
        parse.add(new Label("反编译/深读 Top-K"), 0, pr); parse.add(topK, 1, pr++);
        parse.add(new Label("CFR jar 路径"), 0, pr); parse.add(cfrJar, 1, pr++);
        parse.add(browse, 0, pr++);

        // ---- 项目级上下文增强（需求4：可选引入工程完整分析） ----
        CheckBox projectCtxEnabled = new CheckBox("启用项目级上下文增强（结合工程整体理解做 AI 分析）");
        projectCtxEnabled.setSelected(cfg.isProjectContextEnabled());
        TextField projectCtxDir = new TextField(cfg.getProjectContextDir());
        projectCtxDir.getStyleClass().add(FORM_CONTROL);
        Button browseDir = new Button("选择工程目录...");
        browseDir.getStyleClass().addAll("btn", "btn-default");
        browseDir.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("选择要扫描的工程 / 目录");
            File f = dc.showDialog(owner);
            if (f != null) projectCtxDir.setText(f.getAbsolutePath());
        });
        HBox projCtxRow = new HBox(6, projectCtxDir, browseDir);
        parse.add(projectCtxEnabled, 1, pr++);
        parse.add(new Label("工程目录"), 0, pr); parse.add(projCtxRow, 1, pr++);
        Tab parseTab = new Tab("解析与导出", parse);
        parseTab.setClosable(false);

        tabs.getTabs().addAll(aiTab, parseTab);

        // ---- 悬停提示：将字段说明动态显示在主窗口底部状态栏（覆盖设置中所有字段）----
        attachHint(presetCombo, "厂商预设：选择内置厂商（OpenAI / DeepSeek / 通义千问等），自动填入其官方 Base URL 与默认模型；自定义请选 custom。各厂商的 Base URL / API Key / 模型独立保存，切换不互相覆盖，切回原厂商自动恢复。", statusBar, hintDefault);
        attachHint(keyLink, "获取 API Key：一键跳转当前厂商的密钥申请页；切换预设时链接随之变化。", statusBar, hintDefault);
        attachHint(profileCombo, "我的配置：已保存的命名 LLM 配置列表；切换即一键返显 Provider / Base URL / Key / 模型，免去重复填写。", statusBar, hintDefault);
        attachHint(profBtns, "保存 / 删除配置：将当前连接三元组保存为命名配置以便复用，或删除选中的命名配置（需二次确认）。", statusBar, hintDefault);
        attachHint(provider, "Provider：LLM 厂商标识（openai / deepseek / qwen / custom 等），决定请求协议与鉴权方式。", statusBar, hintDefault);
        attachHint(baseUrl, "Base URL：模型服务接口地址（含 /v1 等路径）；公网模型与本地 Ollama 地址不同，需按厂商填写。", statusBar, hintDefault);
        attachHint(apiKey, "API Key：模型服务鉴权密钥；默认不落盘（persistApiKey=false），仅内存态使用，关闭窗口即丢弃。", statusBar, hintDefault);
        attachHint(model, "模型：具体模型名（如 gpt-4o-mini / deepseek-chat）；影响能力、价格与速度。", statusBar, hintDefault);
        attachHint(aiEnabled, "启用 AI 智能分析：总开关；关闭后工具仅做本地差异比对，不发任何请求（含公网 / 本地模型）。", statusBar, hintDefault);
        attachHint(stageBTopK, "阶段B 深读 Top-K：两阶段分析中，阶段B 对多少个高风险 / 勾选文件做逐文件深读；越大越细但越慢越贵。", statusBar, hintDefault);
        attachHint(costGate, "成本闸门(tokens)：单次分析预估 token 消耗的告警阈值；超过则在发送前提示，避免意外超额。", statusBar, hintDefault);
        attachHint(httpProxy, "HTTP 代理：访问公网模型时的 HTTP 代理地址（如 http://127.0.0.1:7890）；留空直连。", statusBar, hintDefault);
        attachHint(httpsProxy, "HTTPS 代理：访问公网模型时的 HTTPS 代理地址；留空直连。", statusBar, hintDefault);
        attachHint(blockPrivate, "严格 SSRF：安全开关；开启后拒绝向回环 / 私网地址发请求，本地 Ollama(127.0.0.1) 需关闭此项才能用。", statusBar, hintDefault);
        attachHint(persistKey, "记住 API Key：开启后密钥明文写入配置文件；默认关闭更安全，密钥仅存内存。", statusBar, hintDefault);
        attachHint(testBtn, "连接测试：用当前连接参数发起一次轻量探针，验证 URL / Key / 网络是否可达。", statusBar, hintDefault);
        attachHint(testOut, "连接测试结果：显示上方「连接测试」的返回（成功 / 失败原因）。", statusBar, hintDefault);
        attachHint(internalPrefixes, "内部业务码包前缀：识别 L1 内部业务代码的包名前缀（逗号分隔）；命中前缀的 class 归业务码层，其余视为第三方依赖。", statusBar, hintDefault);
        attachHint(expandAll, "递归展开 lib：开启后递归展开 WEB-INF/lib 内嵌 jar 的全部 class（含第三方）；更全但解析更慢。", statusBar, hintDefault);
        attachHint(topK, "反编译 / 深读 Top-K：报告与 AI 分析中最多展示 / 深读多少个差异文件（按风险排序）；越大越全但越慢。", statusBar, hintDefault);
        attachHint(cfrJar, "CFR jar 路径：CFR 反编译器 jar 路径；留空则降级使用 javap，反编译可读性较弱。", statusBar, hintDefault);
        attachHint(browse, "浏览：打开文件选择器，定位本机的 cfr-*.jar。", statusBar, hintDefault);
        attachHint(projectCtxEnabled, "项目级上下文增强：开启后，在 AI 分析前自动扫描所选工程目录，识别构建系统 / 模块依赖 / 入口 / 配置约定，作为分析依据，使结论更贴合项目实际（需求4）。", statusBar, hintDefault);
        attachHint(projectCtxDir, "工程目录：要纳入完整分析的工程或目录绝对路径；离线扫描（不触网），仅读取文件结构 / 构建文件 / 配置，不读取源码内容作为上下文。", statusBar, hintDefault);
        attachHint(browseDir, "选择工程目录：打开目录选择器，定位本机要扫描的工程根目录。", statusBar, hintDefault);

        dlg.getDialogPane().setContent(tabs);
        dlg.getDialogPane().getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        dlg.getDialogPane().getStyleClass().add("bootstrap");
        dlg.setResizable(true);

        // 弹窗关闭（含 X / OK / 取消）时还原主窗口原状态栏文本
        dlg.setOnHiding(e -> {
            if (statusBar != null) statusBar.setText(originalStatus);
        });

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                // 落盘厂商独立配置：先存当前激活厂商快照，再整体持久化
                vendorMap.put(currentVendor[0], new AiVendorConfigStore.Snapshot(
                        baseUrl.getText(), apiKey.getText(), model.getText()));
                vendorStore.saveAll(vendorMap, persistKey.isSelected());

                cfg.setAiProvider(provider.getValue());
                cfg.setAiBaseUrl(baseUrl.getText());
                cfg.setAiApiKey(apiKey.getText());
                cfg.setAiModel(model.getText());
                cfg.setAiEnabled(aiEnabled.isSelected());
                try { cfg.setStageBTopK(Integer.parseInt(stageBTopK.getText())); } catch (NumberFormatException e) {
                    // 用户输入非数字时保留原值
                }
                try { cfg.setCostGateWarnTokens(Double.parseDouble(costGate.getText())); } catch (NumberFormatException e) {
                    // 用户输入非数字时保留原值
                }
                cfg.setHttpProxy(httpProxy.getText());
                cfg.setHttpsProxy(httpsProxy.getText());
                cfg.setBlockPrivateEndpoints(blockPrivate.isSelected());
                cfg.setPersistApiKey(persistKey.isSelected());
                cfg.setInternalPrefixes(internalPrefixes.getText());
                cfg.setExpandAll(expandAll.isSelected());
                try { cfg.setTopK(Integer.parseInt(topK.getText())); } catch (NumberFormatException e) {
                    // 用户输入非数字时保留原值
                }
                cfg.setCfrJar(cfrJar.getText());
                cfg.setProjectContextEnabled(projectCtxEnabled.isSelected());
                cfg.setProjectContextDir(projectCtxDir.getText());
                cfg.save();
            }
        });
    }

    /**
     * 为字段绑定悬停提示：鼠标进入时将说明写入主窗口底部状态栏，移开恢复为默认提示。
     * statusBar 为 null（无主窗口引用）时静默跳过，不影响原有交互。
     */
    private static void attachHint(Node node, String hint, Label statusBar, String def) {
        if (statusBar == null || node == null) return;
        node.setOnMouseEntered(e -> statusBar.setText(hint));
        node.setOnMouseExited(e -> statusBar.setText(def));
    }
}
