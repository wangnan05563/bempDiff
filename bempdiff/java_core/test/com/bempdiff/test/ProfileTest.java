package com.bempdiff.test;

import com.bempdiff.config.AiProfile;
import com.bempdiff.config.AiProfilesStore;
import com.bempdiff.config.LlmPreset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AI 多配置持久化 + 厂商预设的单元测试。
 * 仅依赖纯 Java 模型（AiProfile/AiProfilesStore/LlmPreset 均不引入 JavaFX），
 * 与 core 测试同批编译运行。
 */
public class ProfileTest {

    public void testLlmPreset_builtinCountAndApiKeyUrl() {
        List<LlmPreset> presets = LlmPreset.builtinPresets();
        Asserts.assertTrue("应至少有 8 个内置预设", presets.size() >= 8);
        Asserts.assertNotNull("openai 预设应存在", LlmPreset.byKey("openai"));
        Asserts.assertNotNull("deepseek 应有 apiKeyUrl", LlmPreset.byKey("deepseek").getApiKeyUrl());
        Asserts.assertEquals("byKey 未知返回 null", null, LlmPreset.byKey("no-such"));
    }

    public void testProfilesStore_upsertOverwriteDelete() throws Exception {
        Path dir = Files.createTempDirectory("bd-profile-test");
        Path file = dir.resolve("ai-profiles.properties");
        AiProfilesStore store = new AiProfilesStore(file);

        // 初始为空
        Asserts.assertEquals("初始应为空", 0, store.load().size());

        // 新增
        AiProfile p1 = new AiProfile("我的DeepSeek", "deepseek",
                "https://api.deepseek.com", "sk-abc", "deepseek-chat");
        store.upsert(p1, true);
        List<AiProfile> list = store.load();
        Asserts.assertEquals("新增后应有 1 条", 1, list.size());
        Asserts.assertEquals("name 应保留", "我的DeepSeek", list.get(0).getName());
        Asserts.assertEquals("apiKey 应随 persist=true 落盘", "sk-abc", list.get(0).getApiKey());

        // 覆盖同名（persist=false 应清空 key）
        AiProfile p1b = new AiProfile("我的DeepSeek", "deepseek",
                "https://api.deepseek.com", "sk-new", "deepseek-chat");
        store.upsert(p1b, false);
        list = store.load();
        Asserts.assertEquals("覆盖后仍为 1 条", 1, list.size());
        Asserts.assertEquals("persist=false 时 apiKey 应清空", "", list.get(0).getApiKey());

        // 再次新增第二个
        store.upsert(new AiProfile("公司Azure", "azure",
                "https://x.openai.azure.com", "sk-2", "gpt-4o"), true);
        Asserts.assertEquals("应有 2 条", 2, store.load().size());
        Asserts.assertTrue("exists 应命中", store.exists("公司Azure"));

        // 删除
        store.delete("我的DeepSeek");
        list = store.load();
        Asserts.assertEquals("删除后应剩 1 条", 1, list.size());
        Asserts.assertEquals("剩余应为公司Azure", "公司Azure", list.get(0).getName());
        Asserts.assertFalse("exists 应不命中已删除", store.exists("我的DeepSeek"));
    }
}
