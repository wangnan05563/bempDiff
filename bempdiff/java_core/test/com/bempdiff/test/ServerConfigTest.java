package com.bempdiff.test;

import com.bempdiff.server.ServerConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ServerConfig 持久化回归测试。
 * 重点守护「记住 API Key（persistApiKey）」开关本身必须落盘，
 * 否则勾选后重启会丢失（复选框不显示已保存状态）。见 2026-08-18 修复。
 */
public final class ServerConfigTest {

    private static Path tempFile() throws Exception {
        Path p = Path.of(System.getProperty("java.io.tmpdir"),
                "bempdiff_servertest_" + UUID.randomUUID() + ".properties");
        Files.deleteIfExists(p);
        return p;
    }

    /** 勾选「记住 API Key」并填 key，保存；重启(新建实例)后开关与 key 都应恢复。 */
    public void testPersistApiKeySurvivesRestart() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aiProvider", "openai");
        m.put("aiBaseUrl", "https://api.openai.com/v1");
        m.put("aiModel", "gpt-4o");
        m.put("aiApiKey", "sk-secret-123");
        m.put("persistApiKey", true);
        a.updateFrom(m);

        ServerConfig b = new ServerConfig(f); // 模拟重启
        Map<String, Object> j = b.toJson();
        boolean flag = Boolean.TRUE.equals(j.get("persistApiKey"));
        boolean hasKey = Boolean.TRUE.equals(j.get("hasApiKey"));
        if (!flag) throw new AssertionError("persistApiKey 未持久化，重启后丢失");
        if (!hasKey) throw new AssertionError("apiKey 未持久化，重启后丢失");
        Files.deleteIfExists(f);
    }

    /** 取消「记住 API Key」后保存，key 必须从磁盘移除（避免密钥滞留）。 */
    public void testUncheckRemovesApiKeyFromDisk() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> on = new LinkedHashMap<>();
        on.put("aiProvider", "openai");
        on.put("aiBaseUrl", "https://api.openai.com/v1");
        on.put("aiModel", "gpt-4o");
        on.put("aiApiKey", "sk-secret-123");
        on.put("persistApiKey", true);
        a.updateFrom(on); // 先持久化 key

        // 取消勾选并重新保存
        ServerConfig b = new ServerConfig(f);
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("persistApiKey", false);
        b.updateFrom(off);

        ServerConfig c = new ServerConfig(f); // 再重启
        Map<String, Object> j = c.toJson();
        boolean flag = Boolean.TRUE.equals(j.get("persistApiKey"));
        boolean hasKey = Boolean.TRUE.equals(j.get("hasApiKey"));
        if (flag) throw new AssertionError("persistApiKey 应为 false");
        if (hasKey) throw new AssertionError("取消勾选后 apiKey 仍残留在磁盘");
        Files.deleteIfExists(f);
    }

    /** 「记住 API Key」开启时，GET(toJson) 应回显明文 key 供 UI 返显；关闭时仅给 hasApiKey 标记。 */
    public void testToJsonEchoesApiKeyOnlyWhenPersistOn() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aiApiKey", "sk-secret-xyz");
        m.put("persistApiKey", true);
        a.updateFrom(m); // 开启记住并落盘

        Map<String, Object> j1 = a.toJson();
        if (!"sk-secret-xyz".equals(j1.get("aiApiKey")))
            throw new AssertionError("persistApiKey=true 时应回显明文 apiKey");
        if (!Boolean.TRUE.equals(j1.get("hasApiKey")))
            throw new AssertionError("hasApiKey 应为 true");

        // 关闭记住开关（key 仍在内存，但落盘应移除且 GET 不再回显明文）
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("persistApiKey", false);
        a.updateFrom(off);
        Map<String, Object> j2 = a.toJson();
        if (j2.containsKey("aiApiKey"))
            throw new AssertionError("persistApiKey=false 时不应回显明文 apiKey");
        if (!Boolean.TRUE.equals(j2.get("hasApiKey")))
            throw new AssertionError("内存仍有 key，hasApiKey 应为 true");

        // 重启后：磁盘已无 key，GET 更不应回显
        ServerConfig b = new ServerConfig(f);
        Map<String, Object> j3 = b.toJson();
        if (j3.containsKey("aiApiKey"))
            throw new AssertionError("重启后 persistApiKey=false，不应回显 apiKey");
        if (Boolean.TRUE.equals(j3.get("hasApiKey")))
            throw new AssertionError("重启后 key 已从磁盘移除，hasApiKey 应为 false");
        Files.deleteIfExists(f);
    }
}
