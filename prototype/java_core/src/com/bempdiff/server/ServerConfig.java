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
            aiProvider = p.getProperty("aiProvider", aiProvider);
            aiBaseUrl = p.getProperty("aiBaseUrl", aiBaseUrl);
            aiApiKey = p.getProperty("aiApiKey", aiApiKey);
            aiModel = p.getProperty("aiModel", aiModel);
            aiEnabled = Boolean.parseBoolean(p.getProperty("aiEnabled", "false"));
            stageBTopK = Integer.parseInt(p.getProperty("stageBTopK", "15"));
            costGateWarnTokens = Double.parseDouble(p.getProperty("costGateWarnTokens", "8000"));
            internalPrefixes = p.getProperty("internalPrefixes", internalPrefixes);
            expandAll = Boolean.parseBoolean(p.getProperty("expandAll", "false"));
            topK = Integer.parseInt(p.getProperty("topK", "15"));
            cfrJar = p.getProperty("cfrJar", "");
            httpProxy = p.getProperty("httpProxy", "");
            httpsProxy = p.getProperty("httpsProxy", "");
            blockPrivateEndpoints = Boolean.parseBoolean(p.getProperty("blockPrivateEndpoints", "false"));
            persistApiKey = Boolean.parseBoolean(p.getProperty("persistApiKey", "false"));
            projectContextDir = p.getProperty("projectContextDir", "");
            projectContextEnabled = Boolean.parseBoolean(p.getProperty("projectContextEnabled", "false"));
            filterSearch = p.getProperty("filterSearch", "");
            filterRegex = Boolean.parseBoolean(p.getProperty("filterRegex", "false"));
            filterShowModified = Boolean.parseBoolean(p.getProperty("filterShowModified", "true"));
            filterShowAdded = Boolean.parseBoolean(p.getProperty("filterShowAdded", "true"));
            filterShowDeleted = Boolean.parseBoolean(p.getProperty("filterShowDeleted", "true"));
            filterShowUnchanged = Boolean.parseBoolean(p.getProperty("filterShowUnchanged", "true"));
            autoAiOnCompare = Boolean.parseBoolean(p.getProperty("autoAiOnCompare", "true"));
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
        p.setProperty("aiProvider", aiProvider);
        p.setProperty("aiBaseUrl", aiBaseUrl);
        if (persistApiKey && aiApiKey != null && !aiApiKey.isEmpty()) {
            p.setProperty("aiApiKey", aiApiKey);
        }
        p.setProperty("aiModel", aiModel);
        p.setProperty("aiEnabled", String.valueOf(aiEnabled));
        p.setProperty("stageBTopK", String.valueOf(stageBTopK));
        p.setProperty("costGateWarnTokens", String.valueOf(costGateWarnTokens));
        p.setProperty("internalPrefixes", internalPrefixes);
        p.setProperty("expandAll", String.valueOf(expandAll));
        p.setProperty("topK", String.valueOf(topK));
        p.setProperty("cfrJar", cfrJar == null ? "" : cfrJar);
        p.setProperty("httpProxy", httpProxy);
        p.setProperty("httpsProxy", httpsProxy);
        p.setProperty("blockPrivateEndpoints", String.valueOf(blockPrivateEndpoints));
        p.setProperty("projectContextDir", projectContextDir == null ? "" : projectContextDir);
        p.setProperty("projectContextEnabled", String.valueOf(projectContextEnabled));
        p.setProperty("filterSearch", filterSearch == null ? "" : filterSearch);
        p.setProperty("filterRegex", String.valueOf(filterRegex));
        p.setProperty("filterShowModified", String.valueOf(filterShowModified));
        p.setProperty("filterShowAdded", String.valueOf(filterShowAdded));
        p.setProperty("filterShowDeleted", String.valueOf(filterShowDeleted));
        p.setProperty("filterShowUnchanged", String.valueOf(filterShowUnchanged));
        p.setProperty("autoAiOnCompare", String.valueOf(autoAiOnCompare));
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

    /** 序列化为 JSON（GET 不回显 apiKey 除非持久化开启）。 */
    public java.util.Map<String, Object> toJson() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("aiProvider", aiProvider);
        m.put("aiBaseUrl", aiBaseUrl);
        m.put("aiModel", aiModel);
        m.put("aiEnabled", aiEnabled);
        m.put("stageBTopK", stageBTopK);
        m.put("costGateWarnTokens", costGateWarnTokens);
        m.put("internalPrefixes", internalPrefixes);
        m.put("expandAll", expandAll);
        m.put("topK", topK);
        m.put("cfrJar", cfrJar);
        m.put("httpProxy", httpProxy);
        m.put("httpsProxy", httpsProxy);
        m.put("blockPrivateEndpoints", blockPrivateEndpoints);
        m.put("persistApiKey", persistApiKey);
        m.put("projectContextDir", projectContextDir);
        m.put("projectContextEnabled", projectContextEnabled);
        m.put("filterSearch", filterSearch);
        m.put("filterRegex", filterRegex);
        m.put("filterShowModified", filterShowModified);
        m.put("filterShowAdded", filterShowAdded);
        m.put("filterShowDeleted", filterShowDeleted);
        m.put("filterShowUnchanged", filterShowUnchanged);
        m.put("autoAiOnCompare", autoAiOnCompare);
        m.put("hasApiKey", aiApiKey != null && !aiApiKey.isEmpty());
        return m;
    }

    /** 从 JSON 更新（PUT），并就地保存。 */
    public void updateFrom(java.util.Map<String, Object> m) {
        if (m.containsKey("aiProvider")) aiProvider = Json.str(m, "aiProvider", aiProvider);
        if (m.containsKey("aiBaseUrl")) aiBaseUrl = Json.str(m, "aiBaseUrl", aiBaseUrl);
        if (m.containsKey("aiModel")) aiModel = Json.str(m, "aiModel", aiModel);
        if (m.containsKey("aiEnabled")) aiEnabled = Json.bool(m, "aiEnabled", aiEnabled);
        if (m.containsKey("stageBTopK")) stageBTopK = Json.intv(m, "stageBTopK", stageBTopK);
        if (m.containsKey("costGateWarnTokens")) costGateWarnTokens = Json.intv(m, "costGateWarnTokens", (int) costGateWarnTokens);
        if (m.containsKey("internalPrefixes")) internalPrefixes = Json.str(m, "internalPrefixes", internalPrefixes);
        if (m.containsKey("expandAll")) expandAll = Json.bool(m, "expandAll", expandAll);
        if (m.containsKey("topK")) topK = Json.intv(m, "topK", topK);
        if (m.containsKey("cfrJar")) cfrJar = Json.str(m, "cfrJar", cfrJar);
        if (m.containsKey("httpProxy")) httpProxy = Json.str(m, "httpProxy", httpProxy);
        if (m.containsKey("httpsProxy")) httpsProxy = Json.str(m, "httpsProxy", httpsProxy);
        if (m.containsKey("blockPrivateEndpoints")) blockPrivateEndpoints = Json.bool(m, "blockPrivateEndpoints", blockPrivateEndpoints);
        if (m.containsKey("persistApiKey")) persistApiKey = Json.bool(m, "persistApiKey", persistApiKey);
        if (m.containsKey("aiApiKey")) {
            String k = Json.str(m, "aiApiKey", "");
            if (k != null && !k.isEmpty()) aiApiKey = k;
        }
        if (m.containsKey("projectContextDir")) projectContextDir = Json.str(m, "projectContextDir", projectContextDir);
        if (m.containsKey("projectContextEnabled")) projectContextEnabled = Json.bool(m, "projectContextEnabled", projectContextEnabled);
        if (m.containsKey("filterSearch")) filterSearch = Json.str(m, "filterSearch", filterSearch);
        if (m.containsKey("filterRegex")) filterRegex = Json.bool(m, "filterRegex", filterRegex);
        if (m.containsKey("filterShowModified")) filterShowModified = Json.bool(m, "filterShowModified", filterShowModified);
        if (m.containsKey("filterShowAdded")) filterShowAdded = Json.bool(m, "filterShowAdded", filterShowAdded);
        if (m.containsKey("filterShowDeleted")) filterShowDeleted = Json.bool(m, "filterShowDeleted", filterShowDeleted);
        if (m.containsKey("filterShowUnchanged")) filterShowUnchanged = Json.bool(m, "filterShowUnchanged", filterShowUnchanged);
        if (m.containsKey("autoAiOnCompare")) autoAiOnCompare = Json.bool(m, "autoAiOnCompare", autoAiOnCompare);
        save();
    }
}
