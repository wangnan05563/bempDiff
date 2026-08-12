package com.bempdiff.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 命名 LLM 配置（BYOK 多配置）持久化。
 *
 * <p>落盘到与 ui-config.properties 同目录的 {@code ai-profiles.properties}，
 * 使用 java.util.Properties（零依赖、与 UiConfig 同源，避免引入 JSON 库）。
 * 结构：
 * <pre>
 *   count=2
 *   p0.name=我的DeepSeek
 *   p0.provider=deepseek
 *   p0.baseUrl=https://api.deepseek.com
 *   p0.apiKey=sk-xxx
 *   p0.model=deepseek-chat
 *   p1.name=公司Azure
 *   ...
 * </pre>
 *
 * <p>注意：apiKey 明文落盘存在泄露风险，仅当用户在设置中显式开启「记住 API Key」
 * 时才写入；否则仅保存连接三元组（baseUrl/model/provider），apiKey 留空由用户每次手填。
 */
public final class AiProfilesStore {

    private static final Logger LOG = Logger.getLogger(AiProfilesStore.class.getName());
    private static final String PREFIX = "p";

    private final Path file;

    public AiProfilesStore(Path file) {
        this.file = file;
    }

    /** 读取全部已保存配置（文件不存在/损坏时返回空列表）。 */
    public List<AiProfile> load() {
        List<AiProfile> list = new ArrayList<>();
        if (!Files.exists(file)) return list;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            int count = Integer.parseInt(p.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String name = p.getProperty(PREFIX + i + ".name");
                if (name == null) continue; // 跳过损坏条目
                AiProfile pf = new AiProfile();
                pf.setName(name);
                pf.setProvider(p.getProperty(PREFIX + i + ".provider", ""));
                pf.setBaseUrl(p.getProperty(PREFIX + i + ".baseUrl", ""));
                pf.setApiKey(p.getProperty(PREFIX + i + ".apiKey", ""));
                pf.setModel(p.getProperty(PREFIX + i + ".model", ""));
                list.add(pf);
            }
        } catch (IOException | NumberFormatException e) {
            LOG.log(Level.WARNING, "读取 AI 命名配置失败", e);
        }
        return list;
    }

    /** 覆盖写入全部配置。 */
    public void saveAll(List<AiProfile> list) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "创建 AI 配置目录失败", e);
        }
        Properties p = new Properties();
        p.setProperty("count", String.valueOf(list.size()));
        for (int i = 0; i < list.size(); i++) {
            AiProfile pf = list.get(i);
            p.setProperty(PREFIX + i + ".name", pf.getName() == null ? "" : pf.getName());
            p.setProperty(PREFIX + i + ".provider", pf.getProvider() == null ? "" : pf.getProvider());
            p.setProperty(PREFIX + i + ".baseUrl", pf.getBaseUrl() == null ? "" : pf.getBaseUrl());
            p.setProperty(PREFIX + i + ".apiKey", pf.getApiKey() == null ? "" : pf.getApiKey());
            p.setProperty(PREFIX + i + ".model", pf.getModel() == null ? "" : pf.getModel());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "BEMP Diff Tool - AI 命名配置 (注意: 可能含 API Key 明文，请勿提交/共享)");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "保存 AI 命名配置失败", e);
        }
    }

    /**
     * 新增或覆盖一个命名配置（按 name 去重），并落盘。
     * @param persistKey 是否连 apiKey 一起持久化（对应「记住 API Key」开关）
     */
    public void upsert(AiProfile incoming, boolean persistKey) {
        List<AiProfile> list = load();
        AiProfile toSave = new AiProfile(incoming.getName(), incoming.getProvider(),
                incoming.getBaseUrl(), persistKey ? incoming.getApiKey() : "", incoming.getModel());
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(incoming.getName())) {
                list.set(i, toSave);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(toSave);
        saveAll(list);
    }

    /** 按 name 删除一个命名配置并落盘。 */
    public void delete(String name) {
        if (name == null) return;
        List<AiProfile> list = load();
        list.removeIf(pf -> name.equals(pf.getName()));
        saveAll(list);
    }

    /** 是否存在同名配置。 */
    public boolean exists(String name) {
        if (name == null) return false;
        for (AiProfile pf : load()) {
            if (name.equals(pf.getName())) return true;
        }
        return false;
    }
}
