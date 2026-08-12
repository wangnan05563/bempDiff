package com.bempdiff.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 厂商（provider）独立 LLM 配置持久化。
 *
 * <p>解决「切换厂商预设会覆盖已保存配置」的问题：为每个 provider（openai / deepseek /
 * qwen / custom 等）各自保存一份连接三元组 {baseUrl, apiKey, model}，互不干扰。
 * 切换厂商时先保存当前厂商快照、再恢复目标厂商快照；切回原厂商能正确还原其名称 / Key / URL。
 *
 * <p>落盘到与 ui-config.properties 同目录的 {@code ai-vendor-config.properties}，
 * 使用 java.util.Properties（零依赖、与 UiConfig / AiProfilesStore 同源）。
 * 结构：
 * <pre>
 *   vendors=openai,deepseek,custom
 *   openai.baseUrl=https://api.openai.com/v1
 *   openai.apiKey=sk-xxx            # 仅当 persistKey=true 才落盘
 *   openai.model=gpt-4o-mini
 *   deepseek.baseUrl=https://api.deepseek.com
 *   deepseek.model=deepseek-chat
 *   ...
 * </pre>
 *
 * <p>安全：apiKey 默认不落盘（与 UiConfig.persistApiKey 约定一致，BR-SEC-01）。
 * 仅在 {@link #saveAll(Map, boolean)} 的 persistKey=true 时写入；内存快照始终保留，
 * 保证同一次会话内切换厂商可恢复 Key，跨重启则按用户开关决定。
 */
public final class AiVendorConfigStore {

    private static final Logger LOG = Logger.getLogger(AiVendorConfigStore.class.getName());

    /** 单个厂商的连接三元组快照。 */
    public record Snapshot(String baseUrl, String apiKey, String model) {
        public Snapshot {
            baseUrl = nn(baseUrl);
            apiKey = nn(apiKey);
            model = nn(model);
        }
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private final Path file;

    public AiVendorConfigStore(Path file) {
        this.file = file;
    }

    /** 读取全部厂商快照（文件不存在/损坏时返回空 Map，保留插入顺序）。 */
    public Map<String, Snapshot> loadAll() {
        Map<String, Snapshot> map = new LinkedHashMap<>();
        if (!Files.exists(file)) return map;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            String vendors = p.getProperty("vendors", "");
            for (String raw : vendors.split(",")) {
                String key = raw.trim();
                if (key.isEmpty()) continue;
                map.put(key, new Snapshot(
                        p.getProperty(key + ".baseUrl", ""),
                        p.getProperty(key + ".apiKey", ""),
                        p.getProperty(key + ".model", "")));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "读取厂商独立配置失败", e);
        }
        return map;
    }

    /**
     * 覆盖写入全部厂商快照。
     * @param persistKey 是否连 apiKey 一起持久化（对应「记住 API Key」开关）。
     *                    为 false 时每个厂商的 apiKey 均不落盘（仅内存态）。
     */
    public void saveAll(Map<String, Snapshot> map, boolean persistKey) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "创建厂商配置目录失败", e);
        }
        Properties p = new Properties();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : map.keySet()) {
            if (!first) sb.append(',');
            sb.append(key);
            first = false;
        }
        p.setProperty("vendors", sb.toString());
        for (Map.Entry<String, Snapshot> e : map.entrySet()) {
            String k = e.getKey();
            Snapshot s = e.getValue();
            p.setProperty(k + ".baseUrl", s.baseUrl());
            p.setProperty(k + ".model", s.model());
            // apiKey 仅在 persistKey=true 时落盘（安全约定）
            if (persistKey) p.setProperty(k + ".apiKey", s.apiKey());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "BEMP Diff Tool - 厂商独立 LLM 配置 (含 API Key 明文仅当「记住 API Key」开启)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "保存厂商独立配置失败", e);
        }
    }
}
