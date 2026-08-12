package com.bempdiff.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UI 配置（持久化到 properties 文件）。所有配置项在设置弹窗有入口（FR9）。
 * 对应详细设计 §5.7 配置模型。
 */
public class UiConfig {

    private static final Logger LOG = Logger.getLogger(UiConfig.class.getName());
    private static final String KEY_AI_API_KEY = "aiApiKey";
    private static final String DEFAULT_FALSE = "false";

    private String aiProvider = "openai";
    private String aiBaseUrl = "https://api.openai.com/v1";
    private String aiApiKey = "";
    private String aiModel = "gpt-4o-mini";
    private boolean aiEnabled = false;
    private int stageBTopK = 15;
    private double costGateWarnTokens = 8000;

    private String internalPrefixes = "com.hundsun";   // 内部业务码包前缀（L1 识别）
    private boolean expandAll = false;                  // 是否递归展开 lib（默认仅 L0/L2）
    private int topK = 15;                              // 反编译/深读 Top-K

    private String cfrJar = "";                          // CFR 路径（空则用 javap 降级）
    private String httpProxy = "";
    private String httpsProxy = "";
    private boolean blockPrivateEndpoints = false;        // 严格 SSRF：拒绝回环/私网（本地 Ollama 需关）
    private boolean persistApiKey = false;                // 密钥隔离：默认 false，API Key 仅内存态不落盘

    private String projectContextDir = "";                // 项目级上下文：工程/目录路径（空=不扫描）
    private boolean projectContextEnabled = false;        // 项目级上下文：是否启用增强分析

    // 差异树过滤偏好（搜索/勾选持久化）：默认全部显示，与历史行为一致
    private String filterSearch = "";                     // 搜索词（模糊或正则）
    private boolean filterRegex = false;                  // 搜索是否正则模式
    private boolean filterShowModified = true;            // 显示「修改」
    private boolean filterShowAdded = true;               // 显示「新增」
    private boolean filterShowDeleted = true;             // 显示「删除」
    private boolean filterShowUnchanged = true;           // 显示「未变」

    private boolean autoAiOnCompare = true;               // FR-CX-01：开始比对后自动触发 AI 两阶段分析（等价 report --ai）

    private final Path file;

    public UiConfig(Path file) {
        this.file = file;
        load();
    }

    /** 配置文件路径（供派生 ai-profiles.properties 等兄弟文件）。 */
    public Path getConfigFile() {
        return file;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public void setAiApiKey(String aiApiKey) {
        this.aiApiKey = aiApiKey;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public int getStageBTopK() {
        return stageBTopK;
    }

    public void setStageBTopK(int stageBTopK) {
        this.stageBTopK = stageBTopK;
    }

    public double getCostGateWarnTokens() {
        return costGateWarnTokens;
    }

    public void setCostGateWarnTokens(double costGateWarnTokens) {
        this.costGateWarnTokens = costGateWarnTokens;
    }

    public String getInternalPrefixes() {
        return internalPrefixes;
    }

    public void setInternalPrefixes(String internalPrefixes) {
        this.internalPrefixes = internalPrefixes;
    }

    public boolean isExpandAll() {
        return expandAll;
    }

    public void setExpandAll(boolean expandAll) {
        this.expandAll = expandAll;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getCfrJar() {
        return cfrJar;
    }

    public void setCfrJar(String cfrJar) {
        this.cfrJar = cfrJar;
    }

    public String getHttpProxy() {
        return httpProxy;
    }

    public void setHttpProxy(String httpProxy) {
        this.httpProxy = httpProxy;
    }

    public String getHttpsProxy() {
        return httpsProxy;
    }

    public void setHttpsProxy(String httpsProxy) {
        this.httpsProxy = httpsProxy;
    }

    public boolean isBlockPrivateEndpoints() {
        return blockPrivateEndpoints;
    }

    public void setBlockPrivateEndpoints(boolean blockPrivateEndpoints) {
        this.blockPrivateEndpoints = blockPrivateEndpoints;
    }

    public boolean isPersistApiKey() {
        return persistApiKey;
    }

    public void setPersistApiKey(boolean persistApiKey) {
        this.persistApiKey = persistApiKey;
    }

    public String getProjectContextDir() {
        return projectContextDir;
    }

    public void setProjectContextDir(String projectContextDir) {
        this.projectContextDir = projectContextDir;
    }

    public boolean isProjectContextEnabled() {
        return projectContextEnabled;
    }

    public void setProjectContextEnabled(boolean projectContextEnabled) {
        this.projectContextEnabled = projectContextEnabled;
    }

    public String getFilterSearch() {
        return filterSearch;
    }

    public void setFilterSearch(String filterSearch) {
        this.filterSearch = filterSearch;
    }

    public boolean isFilterRegex() {
        return filterRegex;
    }

    public void setFilterRegex(boolean filterRegex) {
        this.filterRegex = filterRegex;
    }

    public boolean isFilterShowModified() {
        return filterShowModified;
    }

    public void setFilterShowModified(boolean filterShowModified) {
        this.filterShowModified = filterShowModified;
    }

    public boolean isFilterShowAdded() {
        return filterShowAdded;
    }

    public void setFilterShowAdded(boolean filterShowAdded) {
        this.filterShowAdded = filterShowAdded;
    }

    public boolean isFilterShowDeleted() {
        return filterShowDeleted;
    }

    public void setFilterShowDeleted(boolean filterShowDeleted) {
        this.filterShowDeleted = filterShowDeleted;
    }

    public boolean isFilterShowUnchanged() {
        return filterShowUnchanged;
    }

    public void setFilterShowUnchanged(boolean filterShowUnchanged) {
        this.filterShowUnchanged = filterShowUnchanged;
    }

    public boolean isAutoAiOnCompare() {
        return autoAiOnCompare;
    }

    public void setAutoAiOnCompare(boolean autoAiOnCompare) {
        this.autoAiOnCompare = autoAiOnCompare;
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            aiProvider = p.getProperty("aiProvider", aiProvider);
            aiBaseUrl = p.getProperty("aiBaseUrl", aiBaseUrl);
            aiApiKey = p.getProperty(KEY_AI_API_KEY, aiApiKey);
            aiModel = p.getProperty("aiModel", aiModel);
            aiEnabled = Boolean.parseBoolean(p.getProperty("aiEnabled", DEFAULT_FALSE));
            stageBTopK = Integer.parseInt(p.getProperty("stageBTopK", "15"));
            costGateWarnTokens = Double.parseDouble(p.getProperty("costGateWarnTokens", "8000"));
            internalPrefixes = p.getProperty("internalPrefixes", internalPrefixes);
            expandAll = Boolean.parseBoolean(p.getProperty("expandAll", DEFAULT_FALSE));
            topK = Integer.parseInt(p.getProperty("topK", "15"));
            cfrJar = p.getProperty("cfrJar", "");
            httpProxy = p.getProperty("httpProxy", "");
            httpsProxy = p.getProperty("httpsProxy", "");
            blockPrivateEndpoints = Boolean.parseBoolean(p.getProperty("blockPrivateEndpoints", DEFAULT_FALSE));
            persistApiKey = Boolean.parseBoolean(p.getProperty("persistApiKey", DEFAULT_FALSE));
            projectContextDir = p.getProperty("projectContextDir", "");
            projectContextEnabled = Boolean.parseBoolean(p.getProperty("projectContextEnabled", DEFAULT_FALSE));
            filterSearch = p.getProperty("filterSearch", "");
            filterRegex = Boolean.parseBoolean(p.getProperty("filterRegex", DEFAULT_FALSE));
            filterShowModified = Boolean.parseBoolean(p.getProperty("filterShowModified", "true"));
            filterShowAdded = Boolean.parseBoolean(p.getProperty("filterShowAdded", "true"));
            filterShowDeleted = Boolean.parseBoolean(p.getProperty("filterShowDeleted", "true"));
            filterShowUnchanged = Boolean.parseBoolean(p.getProperty("filterShowUnchanged", "true"));
            autoAiOnCompare = Boolean.parseBoolean(p.getProperty("autoAiOnCompare", "true"));
        } catch (IOException | NumberFormatException e) {
            LOG.log(Level.WARNING, "加载 UI 配置失败", e);
        }
    }

    public void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "创建配置目录失败", e);
        }
        Properties p = new Properties();
        p.setProperty("aiProvider", aiProvider);
        p.setProperty("aiBaseUrl", aiBaseUrl);
        // 安全：密钥为空时不落盘；非空时明文写入——本文件可能含敏感凭据，勿提交/共享（BR-SEC-01）
        if (aiApiKey != null && !aiApiKey.isEmpty()) {
            p.setProperty(KEY_AI_API_KEY, aiApiKey);
        }
        p.setProperty("aiModel", aiModel);
        p.setProperty("aiEnabled", String.valueOf(aiEnabled));
        p.setProperty("stageBTopK", String.valueOf(stageBTopK));
        p.setProperty("costGateWarnTokens", String.valueOf(costGateWarnTokens));
        p.setProperty("internalPrefixes", internalPrefixes);
        p.setProperty("expandAll", String.valueOf(expandAll));
        p.setProperty("topK", String.valueOf(topK));
        p.setProperty("cfrJar", cfrJar);
        p.setProperty("httpProxy", httpProxy);
        p.setProperty("httpsProxy", httpsProxy);
        p.setProperty("blockPrivateEndpoints", String.valueOf(blockPrivateEndpoints));
        p.setProperty("projectContextDir", projectContextDir == null ? "" : projectContextDir);
        p.setProperty("projectContextEnabled", String.valueOf(projectContextEnabled));
        // 差异树过滤偏好（搜索/勾选持久化）
        p.setProperty("filterSearch", filterSearch == null ? "" : filterSearch);
        p.setProperty("filterRegex", String.valueOf(filterRegex));
        p.setProperty("filterShowModified", String.valueOf(filterShowModified));
        p.setProperty("filterShowAdded", String.valueOf(filterShowAdded));
        p.setProperty("filterShowDeleted", String.valueOf(filterShowDeleted));
        p.setProperty("filterShowUnchanged", String.valueOf(filterShowUnchanged));
        p.setProperty("autoAiOnCompare", String.valueOf(autoAiOnCompare));
        // 密钥隔离（BR-SEC-01）：默认 persistApiKey=false，API Key 仅内存态不落盘；
        // 仅当显式开启时才明文写入——本文件可能含敏感凭据，勿提交/共享。
        if (persistApiKey && aiApiKey != null && !aiApiKey.isEmpty()) {
            p.setProperty(KEY_AI_API_KEY, aiApiKey);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "BEMP WAR/JAR Diff Tool - UI Config  (注意: 本文件可能含 API Key 等敏感凭据，请勿提交/共享)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "保存 UI 配置失败", e);
        }
    }

    /** 转成 core.ParseConfig（内部包前缀 / expandAll / maxEntryBytes 默认 8MB）。 */
    public com.bempdiff.config.ParseConfig toParseConfig() {
        com.bempdiff.config.ParseConfig c = new com.bempdiff.config.ParseConfig();
        c.setInternalPrefixes(java.util.Arrays.asList(internalPrefixes.split("[,;\\s]+")));
        c.setExpandInternalLib(expandAll);
        c.setExpandAllForPlainJar(expandAll);
        c.setMaxEntryBytes(8L * 1024 * 1024);
        return c;
    }

    /** 转成 core.AiConfig（两阶段 + 成本闸门 + 代理）。 */
    public com.bempdiff.config.AiConfig toAiConfig() {
        com.bempdiff.config.AiConfig c = new com.bempdiff.config.AiConfig();
        c.setEnabled(aiEnabled);
        c.setProvider(aiProvider);
        c.setBaseUrl(aiBaseUrl);
        c.setApiKey(aiApiKey);
        c.setModel(aiModel);
        c.setStageBTopK(stageBTopK);
        c.setCostGateWarnTokens(costGateWarnTokens);
        c.setHttpProxy(httpProxy);
        c.setHttpsProxy(httpsProxy);
        c.setBlockPrivateEndpoints(blockPrivateEndpoints);
        return c;
    }
}