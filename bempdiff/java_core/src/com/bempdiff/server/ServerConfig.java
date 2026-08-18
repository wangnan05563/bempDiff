package com.bempdiff.server;

import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web 服务端配置（持久化到 properties 文件）。
 * 与 javafx_ui 的 UiConfig 语义对齐，但置于 java_core，使 server 子命令自包含（不依赖 UI 模块）。
 * 字段覆盖：AI 连接配置、解析选项、差异树过滤偏好、项目级上下文等。
 */
public final class ServerConfig {
    private static final Logger LOG = Logger.getLogger(ServerConfig.class.getName());

    // properties 键常量：同一键在 load/save/updateFrom/toJson 中多处复用，集中定义避免字面量重复。
    private static final String K_AI_PROVIDER = "aiProvider";
    private static final String K_AI_BASE_URL = "aiBaseUrl";
    private static final String K_AI_API_KEY = "aiApiKey";
    private static final String K_AI_MODEL = "aiModel";
    private static final String K_AI_ENABLED = "aiEnabled";
    private static final String K_STAGE_B_TOP_K = "stageBTopK";
    private static final String K_COST_GATE_WARN_TOKENS = "costGateWarnTokens";
    private static final String K_INTERNAL_PREFIXES = "internalPrefixes";
    private static final String K_EXPAND_ALL = "expandAll";
    private static final String K_TOP_K = "topK";
    private static final String K_CFR_JAR = "cfrJar";
    private static final String K_HTTP_PROXY = "httpProxy";
    private static final String K_HTTPS_PROXY = "httpsProxy";
    private static final String K_BLOCK_PRIVATE_ENDPOINTS = "blockPrivateEndpoints";
    private static final String K_PERSIST_API_KEY = "persistApiKey";
    private static final String K_PROJECT_CONTEXT_DIR = "projectContextDir";
    private static final String K_PROJECT_CONTEXT_ENABLED = "projectContextEnabled";
    private static final String K_FILTER_SEARCH = "filterSearch";
    private static final String K_FILTER_REGEX = "filterRegex";
    private static final String K_FILTER_SHOW_MODIFIED = "filterShowModified";
    private static final String K_FILTER_SHOW_ADDED = "filterShowAdded";
    private static final String K_FILTER_SHOW_DELETED = "filterShowDeleted";
    private static final String K_FILTER_SHOW_UNCHANGED = "filterShowUnchanged";
    private static final String K_AUTO_AI_ON_COMPARE = "autoAiOnCompare";
    private static final String K_FALSE = "false";

    private String aiProvider = "openai";
    private String aiBaseUrl = "https://api.openai.com/v1";
    private String aiApiKey = "";
    private String aiModel = "gpt-4o-mini";
    private boolean aiEnabled = false;
    private int stageBTopK = 15;
    private double costGateWarnTokens = 8000;

    private String internalPrefixes = "com.hundsun";
    private boolean expandAll = false;
    private int topK = 15;
    private String cfrJar = "";
    private String httpProxy = "";
    private String httpsProxy = "";
    private boolean blockPrivateEndpoints = false;
    private boolean persistApiKey = false;

    private String projectContextDir = "";
    private boolean projectContextEnabled = false;

    private String filterSearch = "";
    private boolean filterRegex = false;
    private boolean filterShowModified = true;
    private boolean filterShowAdded = true;
    private boolean filterShowDeleted = true;
    private boolean filterShowUnchanged = true;
    private boolean autoAiOnCompare = true;

    private final Path file;

    public ServerConfig(Path file) {
        this.file = file;
        load();
    }

    public static ServerConfig defaultLocation() {
        Path dir = Paths.get(System.getProperty("user.home"), ".bempdiff");
        return new ServerConfig(dir.resolve("bempdiff-web.properties"));
    }

    public void load() {
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            aiProvider = p.getProperty(K_AI_PROVIDER, aiProvider);
            aiBaseUrl = p.getProperty(K_AI_BASE_URL, aiBaseUrl);
            aiApiKey = p.getProperty(K_AI_API_KEY, aiApiKey);
            aiModel = p.getProperty(K_AI_MODEL, aiModel);
            aiEnabled = Boolean.parseBoolean(p.getProperty(K_AI_ENABLED, K_FALSE));
            stageBTopK = Integer.parseInt(p.getProperty(K_STAGE_B_TOP_K, "15"));
            costGateWarnTokens = Double.parseDouble(p.getProperty(K_COST_GATE_WARN_TOKENS, "8000"));
            internalPrefixes = p.getProperty(K_INTERNAL_PREFIXES, internalPrefixes);
            expandAll = Boolean.parseBoolean(p.getProperty(K_EXPAND_ALL, K_FALSE));
            topK = Integer.parseInt(p.getProperty(K_TOP_K, "15"));
            cfrJar = p.getProperty(K_CFR_JAR, "");
            httpProxy = p.getProperty(K_HTTP_PROXY, "");
            httpsProxy = p.getProperty(K_HTTPS_PROXY, "");
            blockPrivateEndpoints = Boolean.parseBoolean(p.getProperty(K_BLOCK_PRIVATE_ENDPOINTS, K_FALSE));
            persistApiKey = Boolean.parseBoolean(p.getProperty(K_PERSIST_API_KEY, K_FALSE));
            projectContextDir = p.getProperty(K_PROJECT_CONTEXT_DIR, "");
            projectContextEnabled = Boolean.parseBoolean(p.getProperty(K_PROJECT_CONTEXT_ENABLED, K_FALSE));
            filterSearch = p.getProperty(K_FILTER_SEARCH, "");
            filterRegex = Boolean.parseBoolean(p.getProperty(K_FILTER_REGEX, K_FALSE));
            filterShowModified = Boolean.parseBoolean(p.getProperty(K_FILTER_SHOW_MODIFIED, "true"));
            filterShowAdded = Boolean.parseBoolean(p.getProperty(K_FILTER_SHOW_ADDED, "true"));
            filterShowDeleted = Boolean.parseBoolean(p.getProperty(K_FILTER_SHOW_DELETED, "true"));
            filterShowUnchanged = Boolean.parseBoolean(p.getProperty(K_FILTER_SHOW_UNCHANGED, "true"));
            autoAiOnCompare = Boolean.parseBoolean(p.getProperty(K_AUTO_AI_ON_COMPARE, "true"));
        } catch (IOException | NumberFormatException e) {
            LOG.log(Level.WARNING, "加载服务端配置失败", e);
        }
    }

    public void save() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "创建配置目录失败", e);
        }
        Properties p = new Properties();
        p.setProperty(K_AI_PROVIDER, aiProvider);
        p.setProperty(K_AI_BASE_URL, aiBaseUrl);
        if (persistApiKey && aiApiKey != null && !aiApiKey.isEmpty()) {
            p.setProperty(K_AI_API_KEY, aiApiKey);
        }
        // 必须持久化 persistApiKey 开关本身：否则重启后 load() 读到默认值 false，
        // 勾选「记住 API Key」的状态丢失（复选框不显示已保存状态）。
        p.setProperty(K_PERSIST_API_KEY, String.valueOf(persistApiKey));
        p.setProperty(K_AI_MODEL, aiModel);
        p.setProperty(K_AI_ENABLED, String.valueOf(aiEnabled));
        p.setProperty(K_STAGE_B_TOP_K, String.valueOf(stageBTopK));
        p.setProperty(K_COST_GATE_WARN_TOKENS, String.valueOf(costGateWarnTokens));
        p.setProperty(K_INTERNAL_PREFIXES, internalPrefixes);
        p.setProperty(K_EXPAND_ALL, String.valueOf(expandAll));
        p.setProperty(K_TOP_K, String.valueOf(topK));
        p.setProperty(K_CFR_JAR, cfrJar == null ? "" : cfrJar);
        p.setProperty(K_HTTP_PROXY, httpProxy);
        p.setProperty(K_HTTPS_PROXY, httpsProxy);
        p.setProperty(K_BLOCK_PRIVATE_ENDPOINTS, String.valueOf(blockPrivateEndpoints));
        p.setProperty(K_PROJECT_CONTEXT_DIR, projectContextDir == null ? "" : projectContextDir);
        p.setProperty(K_PROJECT_CONTEXT_ENABLED, String.valueOf(projectContextEnabled));
        p.setProperty(K_FILTER_SEARCH, filterSearch == null ? "" : filterSearch);
        p.setProperty(K_FILTER_REGEX, String.valueOf(filterRegex));
        p.setProperty(K_FILTER_SHOW_MODIFIED, String.valueOf(filterShowModified));
        p.setProperty(K_FILTER_SHOW_ADDED, String.valueOf(filterShowAdded));
        p.setProperty(K_FILTER_SHOW_DELETED, String.valueOf(filterShowDeleted));
        p.setProperty(K_FILTER_SHOW_UNCHANGED, String.valueOf(filterShowUnchanged));
        p.setProperty(K_AUTO_AI_ON_COMPARE, String.valueOf(autoAiOnCompare));
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "BEMP Web Server Config (可能含 API Key，请勿提交/共享)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "保存服务端配置失败", e);
        }
    }

    /** 转成核心 ParseConfig。 */
    public ParseConfig toParseConfig() {
        ParseConfig c = new ParseConfig();
        c.setInternalPrefixes(java.util.Arrays.asList(internalPrefixes.split("[,;\\s]+")));
        c.setExpandInternalLib(expandAll);
        c.setExpandAllForPlainJar(expandAll);
        c.setMaxEntryBytes(8L * 1024 * 1024);
        return c;
    }

    /** 转成核心 AiConfig。 */
    public AiConfig toAiConfig() {
        AiConfig c = new AiConfig();
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

    /** 项目上下文目录（智能分类/AI 分析注入项目画像用）。 */
    public String getProjectContextDir() { return projectContextDir; }
    /** 是否启用项目上下文画像。 */
    public boolean isProjectContextEnabled() { return projectContextEnabled; }

    /** 序列化为 JSON（GET 不回显 apiKey 除非持久化开启）。 */
    public java.util.Map<String, Object> toJson() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put(K_AI_PROVIDER, aiProvider);
        m.put(K_AI_BASE_URL, aiBaseUrl);
        m.put(K_AI_MODEL, aiModel);
        m.put(K_AI_ENABLED, aiEnabled);
        m.put(K_STAGE_B_TOP_K, stageBTopK);
        m.put(K_COST_GATE_WARN_TOKENS, costGateWarnTokens);
        m.put(K_INTERNAL_PREFIXES, internalPrefixes);
        m.put(K_EXPAND_ALL, expandAll);
        m.put(K_TOP_K, topK);
        m.put(K_CFR_JAR, cfrJar);
        m.put(K_HTTP_PROXY, httpProxy);
        m.put(K_HTTPS_PROXY, httpsProxy);
        m.put(K_BLOCK_PRIVATE_ENDPOINTS, blockPrivateEndpoints);
        m.put(K_PERSIST_API_KEY, persistApiKey);
        m.put(K_PROJECT_CONTEXT_DIR, projectContextDir);
        m.put(K_PROJECT_CONTEXT_ENABLED, projectContextEnabled);
        m.put(K_FILTER_SEARCH, filterSearch);
        m.put(K_FILTER_REGEX, filterRegex);
        m.put(K_FILTER_SHOW_MODIFIED, filterShowModified);
        m.put(K_FILTER_SHOW_ADDED, filterShowAdded);
        m.put(K_FILTER_SHOW_DELETED, filterShowDeleted);
        m.put(K_FILTER_SHOW_UNCHANGED, filterShowUnchanged);
        m.put(K_AUTO_AI_ON_COMPARE, autoAiOnCompare);
        // 仅在用户开启「记住 API Key」时回显明文 Key：此时密钥本就落盘（明文存于 properties），
        // 回显到前端不增加额外暴露；未开启（默认安全模式）则只给 hasApiKey 标记，避免把内存态密钥泄露到 UI。
        m.put("hasApiKey", aiApiKey != null && !aiApiKey.isEmpty());
        if (persistApiKey && aiApiKey != null && !aiApiKey.isEmpty()) {
            m.put(K_AI_API_KEY, aiApiKey);
        }
        return m;
    }

    /** 从 JSON 更新（PUT），并就地保存。 */
    public void updateFrom(java.util.Map<String, Object> m) {
        applyAiFields(m);
        applyParseAndNetworkFields(m);
        applyProjectAndFilterFields(m);
        save();
    }

    private void applyAiFields(java.util.Map<String, Object> m) {
        if (m.containsKey(K_AI_PROVIDER)) aiProvider = Json.str(m, K_AI_PROVIDER, aiProvider);
        if (m.containsKey(K_AI_BASE_URL)) aiBaseUrl = Json.str(m, K_AI_BASE_URL, aiBaseUrl);
        if (m.containsKey(K_AI_MODEL)) aiModel = Json.str(m, K_AI_MODEL, aiModel);
        if (m.containsKey(K_AI_ENABLED)) aiEnabled = Json.bool(m, K_AI_ENABLED, aiEnabled);
        if (m.containsKey(K_STAGE_B_TOP_K)) stageBTopK = Json.intv(m, K_STAGE_B_TOP_K, stageBTopK);
        if (m.containsKey(K_COST_GATE_WARN_TOKENS)) costGateWarnTokens = Json.intv(m, K_COST_GATE_WARN_TOKENS, (int) costGateWarnTokens);
        if (m.containsKey(K_AI_API_KEY)) {
            String k = Json.str(m, K_AI_API_KEY, "");
            if (k != null && !k.isEmpty()) aiApiKey = k;
        }
    }

    private void applyParseAndNetworkFields(java.util.Map<String, Object> m) {
        if (m.containsKey(K_INTERNAL_PREFIXES)) internalPrefixes = Json.str(m, K_INTERNAL_PREFIXES, internalPrefixes);
        if (m.containsKey(K_EXPAND_ALL)) expandAll = Json.bool(m, K_EXPAND_ALL, expandAll);
        if (m.containsKey(K_TOP_K)) topK = Json.intv(m, K_TOP_K, topK);
        if (m.containsKey(K_CFR_JAR)) cfrJar = Json.str(m, K_CFR_JAR, cfrJar);
        if (m.containsKey(K_HTTP_PROXY)) httpProxy = Json.str(m, K_HTTP_PROXY, httpProxy);
        if (m.containsKey(K_HTTPS_PROXY)) httpsProxy = Json.str(m, K_HTTPS_PROXY, httpsProxy);
        if (m.containsKey(K_BLOCK_PRIVATE_ENDPOINTS)) blockPrivateEndpoints = Json.bool(m, K_BLOCK_PRIVATE_ENDPOINTS, blockPrivateEndpoints);
        if (m.containsKey(K_PERSIST_API_KEY)) persistApiKey = Json.bool(m, K_PERSIST_API_KEY, persistApiKey);
    }

    private void applyProjectAndFilterFields(java.util.Map<String, Object> m) {
        if (m.containsKey(K_PROJECT_CONTEXT_DIR)) projectContextDir = Json.str(m, K_PROJECT_CONTEXT_DIR, projectContextDir);
        if (m.containsKey(K_PROJECT_CONTEXT_ENABLED)) projectContextEnabled = Json.bool(m, K_PROJECT_CONTEXT_ENABLED, projectContextEnabled);
        if (m.containsKey(K_FILTER_SEARCH)) filterSearch = Json.str(m, K_FILTER_SEARCH, filterSearch);
        if (m.containsKey(K_FILTER_REGEX)) filterRegex = Json.bool(m, K_FILTER_REGEX, filterRegex);
        if (m.containsKey(K_FILTER_SHOW_MODIFIED)) filterShowModified = Json.bool(m, K_FILTER_SHOW_MODIFIED, filterShowModified);
        if (m.containsKey(K_FILTER_SHOW_ADDED)) filterShowAdded = Json.bool(m, K_FILTER_SHOW_ADDED, filterShowAdded);
        if (m.containsKey(K_FILTER_SHOW_DELETED)) filterShowDeleted = Json.bool(m, K_FILTER_SHOW_DELETED, filterShowDeleted);
        if (m.containsKey(K_FILTER_SHOW_UNCHANGED)) filterShowUnchanged = Json.bool(m, K_FILTER_SHOW_UNCHANGED, filterShowUnchanged);
        if (m.containsKey(K_AUTO_AI_ON_COMPARE)) autoAiOnCompare = Json.bool(m, K_AUTO_AI_ON_COMPARE, autoAiOnCompare);
    }
}