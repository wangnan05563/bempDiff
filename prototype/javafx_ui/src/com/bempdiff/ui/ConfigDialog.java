package com.bempdiff.ui;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.config.AiConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;

/**
 * 设置弹窗（FR9：所有配置在 UI 有入口）。AI 服务 / 解析与导出 两页。
 * 连接测试按钮对应 FR9.4。
 */
public class ConfigDialog {

    private static final String FORM_CONTROL = "form-control";

    private ConfigDialog() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static void show(Stage owner, UiConfig cfg) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("设置");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TabPane tabs = new TabPane();

        // ---- AI 服务页 ----
        GridPane ai = new GridPane();
        ai.setHgap(8); ai.setVgap(8); ai.setPadding(new Insets(10));
        ComboBox<String> provider = new ComboBox<>();
        provider.getItems().addAll("openai", "azure", "ollama");
        provider.setValue(cfg.getAiProvider());
        provider.getStyleClass().add(FORM_CONTROL);
        TextField baseUrl = new TextField(cfg.getAiBaseUrl());
        baseUrl.getStyleClass().add(FORM_CONTROL);
        PasswordField apiKey = new PasswordField();
        apiKey.setText(cfg.getAiApiKey());
        apiKey.getStyleClass().add(FORM_CONTROL);
        TextField model = new TextField(cfg.getAiModel());
        model.getStyleClass().add(FORM_CONTROL);
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

        int r = 0;
        ai.add(new Label("厂商预设"), 0, r); ai.add(provider, 1, r++);
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
        CheckBox persistKey = new CheckBox("记住 API Key（明文存盘，默认关闭更安全）");
        persistKey.setSelected(cfg.isPersistApiKey());
        ai.add(persistKey, 1, r++);
        ai.add(testBtn, 0, r); ai.add(testOut, 1, r);
        testBtn.setOnAction(e -> {
            AiConfig ac = cfg.toAiConfig();
            ac.setProvider(provider.getValue());
            ac.setBaseUrl(baseUrl.getText());
            ac.setApiKey(apiKey.getText());
            ac.setModel(model.getText());
            AiAnalyzer tester = new HttpAiAnalyzer(ac);
            boolean ok = tester.testConnection(ac);
            testOut.setText(ok ? "连接成功" : "连接失败（检查 URL/Key/网络）");
        });
        Tab aiTab = new Tab("AI 服务", ai);
        aiTab.setClosable(false);

        // ---- 解析与导出页 ----
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
        parse.add(browse, 0, pr);
        Tab parseTab = new Tab("解析与导出", parse);
        parseTab.setClosable(false);

        tabs.getTabs().addAll(aiTab, parseTab);
        dlg.getDialogPane().setContent(tabs);
        dlg.getDialogPane().getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        dlg.getDialogPane().getStyleClass().add("bootstrap");
        dlg.setResizable(true);

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
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
                cfg.save();
            }
        });
    }
}