package com.bempdiff.test;

import com.bempdiff.config.AiVendorConfigStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 厂商独立配置持久化的单元测试（AiVendorConfigStore）。
 * 仅依赖纯 Java 模型（不引入 JavaFX），与 core 测试同批编译运行。
 */
public class VendorConfigTest {

    public void testLoadAll_emptyWhenNoFile() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);
        Asserts.assertTrue("文件不存在时应返回空 Map", store.loadAll().isEmpty());
    }

    public void testSaveAndLoad_roundtrip() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);

        java.util.Map<String, AiVendorConfigStore.Snapshot> map = new java.util.LinkedHashMap<>();
        map.put("openai", new AiVendorConfigStore.Snapshot(
                "https://api.openai.com/v1", "sk-openai", "gpt-4o-mini"));
        map.put("deepseek", new AiVendorConfigStore.Snapshot(
                "https://api.deepseek.com", "sk-deep", "deepseek-chat"));
        store.saveAll(map, true);

        Map<String, AiVendorConfigStore.Snapshot> loaded = store.loadAll();
        Asserts.assertEquals("应有 2 个厂商", 2, loaded.size());
        Asserts.assertEquals("openai baseUrl", "https://api.openai.com/v1", loaded.get("openai").baseUrl());
        Asserts.assertEquals("openai model", "gpt-4o-mini", loaded.get("openai").model());
        Asserts.assertEquals("openai key 应保留", "sk-openai", loaded.get("openai").apiKey());
        Asserts.assertEquals("deepseek key 应保留", "sk-deep", loaded.get("deepseek").apiKey());
    }

    public void testVendors_independentNoOverwrite() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);

        // 模拟切换：先保存 openai，再保存 deepseek，二者应各自独立
        java.util.Map<String, AiVendorConfigStore.Snapshot> map = new java.util.LinkedHashMap<>();
        map.put("openai", new AiVendorConfigStore.Snapshot(
                "https://api.openai.com/v1", "sk-oa", "gpt-4o"));
        map.put("deepseek", new AiVendorConfigStore.Snapshot(
                "https://api.deepseek.com", "sk-ds", "deepseek-reasoner"));
        store.saveAll(map, true);

        // 模拟切回 openai：openai 不应被 deepseek 覆盖
        Map<String, AiVendorConfigStore.Snapshot> loaded = store.loadAll();
        Asserts.assertEquals("切回 openai model 应为 gpt-4o", "gpt-4o", loaded.get("openai").model());
        Asserts.assertEquals("openai key 应为 sk-oa", "sk-oa", loaded.get("openai").apiKey());
        Asserts.assertEquals("deepseek 仍独立", "deepseek-reasoner", loaded.get("deepseek").model());

        // 修改 openai 再存，deepseek 不应受影响（验证互不影响）
        map.put("openai", new AiVendorConfigStore.Snapshot(
                "https://api.openai.com/v1", "sk-oa2", "gpt-4o-mini"));
        store.saveAll(map, true);
        loaded = store.loadAll();
        Asserts.assertEquals("openai 更新为 gpt-4o-mini", "gpt-4o-mini", loaded.get("openai").model());
        Asserts.assertEquals("deepseek 不受影响仍为 deepseek-reasoner",
                "deepseek-reasoner", loaded.get("deepseek").model());
    }

    public void testPersistKey_falseOmitsApiKey() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);

        // persistKey=false：apiKey 不应落盘
        java.util.Map<String, AiVendorConfigStore.Snapshot> map = new java.util.LinkedHashMap<>();
        map.put("openai", new AiVendorConfigStore.Snapshot(
                "https://api.openai.com/v1", "sk-secret", "gpt-4o-mini"));
        store.saveAll(map, false);

        Map<String, AiVendorConfigStore.Snapshot> loaded = store.loadAll();
        Asserts.assertEquals("baseUrl 仍保留", "https://api.openai.com/v1", loaded.get("openai").baseUrl());
        Asserts.assertEquals("model 仍保留", "gpt-4o-mini", loaded.get("openai").model());
        Asserts.assertEquals("persistKey=false 时 apiKey 应为空", "", loaded.get("openai").apiKey());

        // persistKey=true：apiKey 应落盘
        store.saveAll(map, true);
        loaded = store.loadAll();
        Asserts.assertEquals("persistKey=true 时 apiKey 应保留", "sk-secret", loaded.get("openai").apiKey());
    }

    public void testNullSafe() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);
        // Snapshot 构造对 null 应归一为空串，避免 NullPointerException
        AiVendorConfigStore.Snapshot s = new AiVendorConfigStore.Snapshot(null, null, null);
        Asserts.assertEquals("null baseUrl -> 空串", "", s.baseUrl());
        Asserts.assertEquals("null apiKey -> 空串", "", s.apiKey());
        Asserts.assertEquals("null model -> 空串", "", s.model());
    }

    public void testLoadAll_corruptFileReturnsEmpty() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        // 写入非 Properties 格式的垃圾（不含 vendors= 键），loadAll 应解析为不含任何厂商的空 Map，且不抛异常
        java.nio.file.Files.writeString(file, "this-is-corrupted-config-data-not-valid-properties-at-all");
        AiVendorConfigStore store = new AiVendorConfigStore(file);
        Map<String, AiVendorConfigStore.Snapshot> loaded = store.loadAll();
        Asserts.assertTrue("损坏/无 vendors 键的文件应返回空 Map（不抛异常）", loaded.isEmpty());
    }

    public void testPersistKey_trueThenFalse_removesApiKey() throws Exception {
        Path dir = Files.createTempDirectory("bd-vendor-test");
        Path file = dir.resolve("ai-vendor-config.properties");
        AiVendorConfigStore store = new AiVendorConfigStore(file);

        java.util.Map<String, AiVendorConfigStore.Snapshot> map = new java.util.LinkedHashMap<>();
        map.put("openai", new AiVendorConfigStore.Snapshot(
                "https://api.openai.com/v1", "sk-secret", "gpt-4o-mini"));
        // 先 persistKey=true 落盘 Key
        store.saveAll(map, true);
        Asserts.assertEquals("true 时 Key 落盘", "sk-secret", store.loadAll().get("openai").apiKey());
        // 再 persistKey=false 保存（用户关闭「记住 Key」）→ 落盘 Key 必须被移除
        store.saveAll(map, false);
        Asserts.assertEquals("false 时 Key 应从落盘移除", "", store.loadAll().get("openai").apiKey());
    }

    public void testSnapshot_trimsWhitespace() throws Exception {
        // baseUrl/model 首尾空白应被裁剪（F5 归一化）；apiKey 不裁剪以免误改凭证
        AiVendorConfigStore.Snapshot s = new AiVendorConfigStore.Snapshot(
                "  https://api.openai.com/v1  ", "sk-keep-spaces ", "  gpt-4o-mini ");
        Asserts.assertEquals("baseUrl 首尾空白裁剪", "https://api.openai.com/v1", s.baseUrl());
        Asserts.assertEquals("model 首尾空白裁剪", "gpt-4o-mini", s.model());
        Asserts.assertEquals("apiKey 不裁剪（保留凭证原样）", "sk-keep-spaces ", s.apiKey());
    }
}
