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

    /** 比对级忽略扩展名（ignoreExtensions）必须跨重启持久化：保存的 .mf 在 GET(toJson) 中应原样返回。 */
    public void testIgnoreExtensionsSurvivesRestart() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> m = new LinkedHashMap<>();
        java.util.List<String> exts = new java.util.ArrayList<>();
        exts.add(".mf");
        exts.add("properties");
        m.put("ignoreExtensions", exts);
        a.updateFrom(m);

        ServerConfig b = new ServerConfig(f); // 模拟重启：读磁盘
        Object j = b.toJson().get("ignoreExtensions");
        if (!(j instanceof java.util.List)) throw new AssertionError("toJson 应输出 ignoreExtensions 数组");
        java.util.List<?> got = (java.util.List<?>) j;
        if (!got.contains(".mf") || !got.contains("properties"))
            throw new AssertionError("ignoreExtensions 重启后丢失: " + got);
        Files.deleteIfExists(f);
    }

    /** 自动逐层解包配置（unpackNested/Threads/MaxDepth）必须跨重启持久化，且默认开启。 */
    public void testUnpackNestedSurvivesRestart() throws Exception {
        Path f = tempFile();
        // 默认值：自动解包默认开启（默认行为变化对本功能至关重要，须守护默认态）
        ServerConfig d = new ServerConfig(f);
        if (!Boolean.TRUE.equals(d.toJson().get("unpackNested")))
            throw new AssertionError("unpackNested 默认应为开启（true）");
        // 关闭并自定义线程/深度
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unpackNested", false);
        m.put("unpackThreads", 8);
        m.put("unpackMaxDepth", 3);
        d.updateFrom(m);

        ServerConfig b = new ServerConfig(f); // 模拟重启：读磁盘
        java.util.Map<String, Object> j = b.toJson();
        if (Boolean.TRUE.equals(j.get("unpackNested")))
            throw new AssertionError("unpackNested=false 未持久化，重启后被还原");
        if (!Integer.valueOf(8).equals(j.get("unpackThreads")))
            throw new AssertionError("unpackThreads 重启后丢失/不符: " + j.get("unpackThreads"));
        if (!Integer.valueOf(3).equals(j.get("unpackMaxDepth")))
            throw new AssertionError("unpackMaxDepth 重启后丢失/不符: " + j.get("unpackMaxDepth"));
        Files.deleteIfExists(f);
    }

    /**
     * 阶段A 截断参数（stageATopK/stageAFileSampleLines）与上下文窗口护栏（maxPromptTokens）
     * 必须跨重启持久化，且 toAiConfig 正确透传。
     * 历史缺陷：stageATopK/stageAFileSampleLines 从未映射进 AiConfig，
     * 配置中心改了也不生效（token 超限事故的加重因子，2026-08-31 修复）。
     */
    public void testStageAParamsAndMaxPromptTokensSurviveRestartAndToAiConfig() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stageATopK", 10);
        m.put("stageAFileSampleLines", 40);
        m.put("maxPromptTokens", 32000);
        a.updateFrom(m);

        ServerConfig b = new ServerConfig(f); // 模拟重启：读磁盘
        java.util.Map<String, Object> j = b.toJson();
        if (!Integer.valueOf(10).equals(j.get("stageATopK")))
            throw new AssertionError("stageATopK 重启后丢失/不符: " + j.get("stageATopK"));
        if (!Integer.valueOf(40).equals(j.get("stageAFileSampleLines")))
            throw new AssertionError("stageAFileSampleLines 重启后丢失/不符: " + j.get("stageAFileSampleLines"));
        if (!Integer.valueOf(32000).equals(j.get("maxPromptTokens")))
            throw new AssertionError("maxPromptTokens 重启后丢失/不符: " + j.get("maxPromptTokens"));

        // toAiConfig 必须透传三参（核心守护：此前 stageATopK 在此被遗漏，配置改了也不生效）
        com.bempdiff.config.AiConfig ai = b.toAiConfig();
        if (ai.getStageATopK() != 10)
            throw new AssertionError("toAiConfig 未透传 stageATopK（配置中心改动不生效）: " + ai.getStageATopK());
        if (ai.getStageAFileSampleLines() != 40)
            throw new AssertionError("toAiConfig 未透传 stageAFileSampleLines: " + ai.getStageAFileSampleLines());
        if (ai.getMaxPromptTokens() != 32000)
            throw new AssertionError("toAiConfig 未透传 maxPromptTokens: " + ai.getMaxPromptTokens());
        Files.deleteIfExists(f);
    }

    /** PUT 非法值（≤0 / 低于下限）应被钳制到安全下限，load 手改磁盘非法值同样被钳制。 */
    public void testStageAParamsRejectNonPositive() throws Exception {
        Path f = tempFile();
        ServerConfig a = new ServerConfig(f);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stageATopK", 0);
        m.put("stageAFileSampleLines", -5);
        m.put("maxPromptTokens", 0);
        a.updateFrom(m);
        if ((Integer) a.toJson().get("stageATopK") < 1)
            throw new AssertionError("stageATopK 应钳制到 ≥1: " + a.toJson().get("stageATopK"));
        if ((Integer) a.toJson().get("stageAFileSampleLines") < 1)
            throw new AssertionError("stageAFileSampleLines 应钳制到 ≥1");
        if ((Integer) a.toJson().get("maxPromptTokens") < 1000)
            throw new AssertionError("maxPromptTokens 应钳制到 ≥1000");

        // 重启（load）后仍保持钳制后的合法值
        ServerConfig b = new ServerConfig(f);
        if ((Integer) b.toJson().get("stageATopK") < 1
                || (Integer) b.toJson().get("stageAFileSampleLines") < 1
                || (Integer) b.toJson().get("maxPromptTokens") < 1000)
            throw new AssertionError("重启后配置应保持合法下限之内");
        Files.deleteIfExists(f);
    }
}
