package com.bempdiff.test;

import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.config.AiConfig;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

/** HttpAiAnalyzer 安全相关测试：SSRF 防护（云元数据/私网拦截）、JSON 转义、响应内容解析。 */
public final class SsrfTest {

    private static final Method IS_BLOCKED_HOST;
    private static final Method GUARD_ENDPOINT;
    private static final Method ESCAPE_JSON;
    private static final Method EXTRACT_CONTENT;

    static {
        try {
            IS_BLOCKED_HOST = HttpAiAnalyzer.class.getDeclaredMethod("isBlockedHost", String.class, boolean.class);
            GUARD_ENDPOINT = HttpAiAnalyzer.class.getDeclaredMethod("guardEndpoint", URL.class, AiConfig.class);
            ESCAPE_JSON = HttpAiAnalyzer.class.getDeclaredMethod("escapeJson", String.class);
            EXTRACT_CONTENT = HttpAiAnalyzer.class.getDeclaredMethod("extractContent", String.class);
            for (Method m : new Method[]{IS_BLOCKED_HOST, GUARD_ENDPOINT, ESCAPE_JSON, EXTRACT_CONTENT}) {
                m.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isBlocked(String host, boolean strict) throws Throwable {
        return (boolean) IS_BLOCKED_HOST.invoke(null, host, strict);
    }

    public void testIsBlockedHost_cloudMetadataAlwaysBlocked() throws Throwable {
        Asserts.assertTrue("169.254.169.254 必须拦截(非严格)", isBlocked("169.254.169.254", false));
        Asserts.assertTrue("169.254.1.1 必须拦截(非严格)", isBlocked("169.254.1.1", false));
    }

    public void testIsBlockedHost_loopbackStrictMode() throws Throwable {
        Asserts.assertFalse("localhost 非严格不拦截(本地 Ollama 可用)", isBlocked("localhost", false));
        Asserts.assertTrue("localhost 严格模式拦截", isBlocked("localhost", true));
        Asserts.assertFalse("127.0.0.1 非严格不拦截", isBlocked("127.0.0.1", false));
        Asserts.assertTrue("127.0.0.1 严格模式拦截", isBlocked("127.0.0.1", true));
    }

    public void testIsBlockedHost_privateStrictMode() throws Throwable {
        Asserts.assertFalse("10.0.0.1 非严格不拦截", isBlocked("10.0.0.1", false));
        Asserts.assertTrue("10.0.0.1 严格模式拦截", isBlocked("10.0.0.1", true));
        Asserts.assertFalse("公网 8.8.8.8 不拦截", isBlocked("8.8.8.8", false));
        Asserts.assertFalse("example.com 不拦截", isBlocked("example.com", false));
    }

    public void testGuardEndpoint_rejectsBadProtocol() throws Throwable {
        AiConfig cfg = new AiConfig();
        try {
            GUARD_ENDPOINT.invoke(null, URI.create("file:///etc/passwd").toURL(), cfg);
            Asserts.assertTrue("file:// 协议应被拒绝", false);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Asserts.assertTrue("应抛 IOException", e.getCause() instanceof java.io.IOException);
        }
    }

    public void testGuardEndpoint_rejectsMetadata() throws Throwable {
        AiConfig cfg = new AiConfig();
        try {
            GUARD_ENDPOINT.invoke(null, URI.create("http://169.254.169.254/latest/meta-data/").toURL(), cfg);
            Asserts.assertTrue("云元数据端点应被拒绝", false);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Asserts.assertTrue("应抛 IOException", e.getCause() instanceof java.io.IOException);
        }
    }

    public void testGuardEndpoint_allowsPublicHttps() throws Throwable {
        AiConfig cfg = new AiConfig();
        // 不应抛异常（仅做端点校验，不真正发起连接）
        GUARD_ENDPOINT.invoke(null, URI.create("https://api.openai.com/v1/chat/completions").toURL(), cfg);
        Asserts.assertTrue("公网 https 端点应放行", true);
    }

    public void testEscapeJson() throws Throwable {
        String out = (String) ESCAPE_JSON.invoke(null, "line\n\"quote\"");
        Asserts.assertTrue("应以双引号包裹", out.startsWith("\"") && out.endsWith("\""));
        Asserts.assertContains("换行应转义", out, "\\n");
        Asserts.assertContains("引号应转义", out, "\\\"");
    }

    public void testExtractContent_openAiStyle() throws Throwable {
        String resp = "{\"choices\":[{\"message\":{\"content\":\"风险 HIGH，建议回归\"}}]}";
        String content = (String) EXTRACT_CONTENT.invoke(null, resp);
        Asserts.assertEquals("应提取 content", "风险 HIGH，建议回归", content);
    }

    public void testExtractContent_withEscapedChars() throws Throwable {
        String resp = "{\"choices\":[{\"message\":{\"content\":\"行1\\n行2\\\"引号\\\"\"}}]}";
        String content = (String) EXTRACT_CONTENT.invoke(null, resp);
        Asserts.assertContains("应还原换行", content, "\n");
        Asserts.assertContains("应还原引号", content, "\"");
    }
}
