package com.bempdiff.test;

import com.bempdiff.ai.HttpAiAnalyzer;

import java.lang.reflect.Method;

/**
 * FR-013 实际消耗监控测试：usage 段解析累计（prompt/completion 分段、多次调用累计、
 * 缺段静默、坏段不抛）。accumulateUsage 为 private 实例方法——经反射驱动（不触网）。
 */
public final class AiUsageTest {

    private static HttpAiAnalyzer newAnalyzer() {
        // 仅用到 usage 累计逻辑（不发起网络调用）；配置默认值即可
        return new HttpAiAnalyzer(new com.bempdiff.config.AiConfig());
    }

    private static void accumulate(HttpAiAnalyzer a, String resp) throws Exception {
        Method m = HttpAiAnalyzer.class.getDeclaredMethod("accumulateUsage", String.class);
        m.setAccessible(true);
        m.invoke(a, resp);
    }

    public void testUsageParsedAndAccumulated() throws Exception {
        HttpAiAnalyzer a = newAnalyzer();
        String resp1 = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}],"
                + "\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":50,\"total_tokens\":150}}";
        accumulate(a, resp1);
        String resp2 = "{\"choices\":[],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":20}}";
        accumulate(a, resp2);
        Asserts.assertEquals("输入分段累计", 130L, a.getUsagePromptTokens());
        Asserts.assertEquals("输出分段累计", 70L, a.getUsageCompletionTokens());
        Asserts.assertEquals("调用次数累计", 2, a.getUsageCalls());
    }

    public void testMissingUsageSilentlySkipped() throws Exception {
        HttpAiAnalyzer a = newAnalyzer();
        accumulate(a, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"); // 无 usage 段
        Asserts.assertEquals("缺段不计入", 0L, a.getUsagePromptTokens());
        Asserts.assertEquals("调用数不增", 0, a.getUsageCalls());
    }

    public void testMalformedUsageDoesNotThrow() throws Exception {
        HttpAiAnalyzer a = newAnalyzer();
        // usage 段定位错误（截断至 400 字符窗口外）/ 非数字值——均应静默
        StringBuilder big = new StringBuilder("{\"usage\":{\"prompt_tokens\":");
        for (int i = 0; i < 100; i++) big.append("x,");
        big.append("}}");
        accumulate(a, big.toString());
        accumulate(a, "{\"usage\":{\"prompt_tokens\":\"abc\",\"completion_tokens\":7}}");
        // completion 仍应解析（extractLong 对首个字段失败不影响第二个）
        Asserts.assertTrue("坏段不抛且部分字段可解析", a.getUsageCompletionTokens() >= 0);
    }

    public void testPartialFieldsCounted() throws Exception {
        HttpAiAnalyzer a = newAnalyzer();
        // 供应商可能只回 total_tokens：prompt/completion 都缺失 → 不计入（不猜数）
        accumulate(a, "{\"usage\":{\"total_tokens\":999}}");
        Asserts.assertEquals("仅 total 时不猜分段", 0L, a.getUsagePromptTokens());
        Asserts.assertEquals("仅 total 时不猜分段", 0L, a.getUsageCompletionTokens());
    }
}
