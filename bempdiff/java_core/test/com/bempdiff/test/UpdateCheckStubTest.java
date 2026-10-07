package com.bempdiff.test;

import com.bempdiff.server.UpdateCheckService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {@link UpdateCheckService} 补充桩测（本机 com.sun.net.httpserver + 空闲端口，零外网）。
 *
 * <p>与既有 UpdateCheckServiceContractTest 互补，专注该服务契约测试未覆盖的分支：
 * Windows 安装包资产解析（firstExeDownloadUrl：跳非 exe / 跳 http 非安全 / 跳 "null" 名 / 跳非 Map 项、
 * 无 assets 回退空串）、current 为空只查不比的消息分支、404 结果被缓存后的二次命中（latest=null 仍算命中）、
 * 单参 {@code fetchLatestRelease(repo)} 便捷入口、以及 {@code stripVPrefix}/{@code friendlyError} 的
 * 各错误码/网络/null-message 分支。全部经反射把 API_BASE 指向本地桩并在 finally 恢复生产值。</p>
 */
public final class UpdateCheckStubTest {

    private static volatile String scenario = "empty";
    private static volatile int hits = 0;

    private static final HttpServer SERVER;
    private static final int PORT;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PORT = SERVER.getAddress().getPort();
            SERVER.createContext("/", UpdateCheckStubTest::handle);
            SERVER.setExecutor(null);
            SERVER.start();
        } catch (IOException e) {
            throw new RuntimeException("无法启动假 GitHub 服务", e);
        }
    }

    private static int statusFor(String s) {
        return "noRelease".equals(s) ? 404 : 200;
    }

    private static String bodyFor(String s) {
        switch (s) {
            case "noRelease":
                return "{\"message\":\"Not Found\"}";
            case "withAssets":
                return "{\"tag_name\":\"v3.1.0\",\"name\":\"R3\","
                        + "\"html_url\":\"https://github.com/x/y/releases/tag/v3.1.0\","
                        + "\"published_at\":\"2026-09-01T00:00:00Z\",\"body\":\"notes\","
                        + "\"assets\":["
                        + "{\"name\":\"installer.exe\",\"browser_download_url\":\"https://dl/installer.exe\"},"
                        + "{\"name\":\"app.exe.blockmap\",\"browser_download_url\":\"https://dl/app.exe.blockmap\"},"
                        + "{\"name\":\"notes.txt\",\"browser_download_url\":\"https://dl/notes.txt\"}]}";
            case "skipUnsafeExe":
                // 首个 .exe 走 http（非 https）应被跳过；第二个 https .exe 才是结果
                return "{\"tag_name\":\"v1.0.0\",\"assets\":["
                        + "{\"name\":\"bad.exe\",\"browser_download_url\":\"http://insecure/bad.exe\"},"
                        + "{\"name\":\"null.exe\",\"browser_download_url\":null},"
                        + "\"not-a-map-item\","
                        + "{\"name\":\"good.exe\",\"browser_download_url\":\"https://dl/good.exe\"}]}";
            case "noAssets":
                return "{\"tag_name\":\"v1.0.0\",\"name\":\"N\",\"html_url\":\"u\"}";
            case "emptyArrayAssets":
                return "{\"tag_name\":\"v1.0.0\",\"assets\":[]}";
            default:
                return "{}";
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        hits++;
        byte[] body = bodyFor(scenario).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusFor(scenario), body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static void fresh(String s) {
        scenario = s;
        hits = 0;
        UpdateCheckService.clearLatestCache();
    }

    /** 反射把 API_BASE 指向本地桩执行 action，finally 恢复生产 HTTPS 默认值。 */
    private static Object withStubBase(ThrowingSupplier action) throws Throwable {
        Field f = UpdateCheckService.class.getDeclaredField("API_BASE");
        f.setAccessible(true);
        String orig = (String) f.get(null);
        try {
            f.set(null, "http://127.0.0.1:" + PORT + "/");
            return action.get();
        } finally {
            f.set(null, orig);
        }
    }

    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> latest(Map<String, Object> r) {
        Object v = r.get("latest");
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    // ---------------- 用例 ----------------

    public void testCheck_nullCurrent_onlyFetchNoComparison() throws Throwable {
        fresh("withAssets");
        Object resp = withStubBase(() -> UpdateCheckService.check(null));
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) resp;
        Asserts.assertEquals("null 当前仍成功", Boolean.TRUE, r.get("ok"));
        Asserts.assertEquals("current 归一为空串", "", r.get("current"));
        Asserts.assertNull("无当前版本不比较→upToDate=null", r.get("upToDate"));
        Asserts.assertContains("提示未比较", String.valueOf(r.get("message")), "当前版本未知");
        Asserts.assertContains("提示含最新 tag", String.valueOf(r.get("message")), "v3.1.0");
    }

    public void testCheck_downloadUrl_picksHttpsExe() throws Throwable {
        fresh("withAssets");
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("1.0.0"));
        Map<String, Object> lt = latest(r);
        Asserts.assertNotNull("有 release 时 latest 非空", lt);
        Asserts.assertEquals("installer.exe 作为下载地址", "https://dl/installer.exe", lt.get("downloadUrl"));
    }

    public void testCheck_downloadUrl_skipsUnsafeAndBadNames() throws Throwable {
        fresh("skipUnsafeExe");
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("0.1.0"));
        Map<String, Object> lt = latest(r);
        Asserts.assertNotNull("latest 非空", lt);
        // http:// 的 bad.exe 被跳过，null.exe（browser_download_url 为 null → "null"）被跳过，非 Map 项跳过，命中 good.exe
        Asserts.assertEquals("应跳过非 https 与坏名，选中 good.exe", "https://dl/good.exe", lt.get("downloadUrl"));
    }

    public void testCheck_downloadUrl_noAssetsFallsBackEmpty() throws Throwable {
        fresh("noAssets");
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("1.0.0"));
        Map<String, Object> lt = latest(r);
        Asserts.assertEquals("无 assets→空下载地址", "", lt.get("downloadUrl"));
    }

    public void testCheck_downloadUrl_emptyAssetsArrayFallsBackEmpty() throws Throwable {
        fresh("emptyArrayAssets");
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("1.0.0"));
        Map<String, Object> lt = latest(r);
        Asserts.assertEquals("空 assets 数组→空下载地址", "", lt.get("downloadUrl"));
    }

    public void testCheck_noRelease404_secondCallHitsCache() throws Throwable {
        fresh("noRelease");
        @SuppressWarnings("unchecked")
        Map<String, Object> r1 = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("1.0.0"));
        Asserts.assertEquals("404 首次仍 ok=true", Boolean.TRUE, r1.get("ok"));
        Asserts.assertNull("无 release latest=null", r1.get("latest"));
        Asserts.assertEquals("首次未缓存", Boolean.FALSE, r1.get("cached"));
        Asserts.assertEquals("首次真实请求 1 次", 1, hits);

        // 二次检查：404（latest=null）也命中缓存，不再打网络（cached 且 latest 仍为 null）
        @SuppressWarnings("unchecked")
        Map<String, Object> r2 = (Map<String, Object>) withStubBase(() -> UpdateCheckService.check("1.0.0"));
        Asserts.assertEquals("404 结果也被缓存", Boolean.TRUE, r2.get("cached"));
        Asserts.assertNull("缓存命中后 latest 仍 null", r2.get("latest"));
        Asserts.assertEquals("二次不应再打网络", 1, hits);
    }

    public void testFetchLatestRelease_singleArgConvenience() throws Throwable {
        fresh("withAssets");
        Object out = withStubBase(() -> {
            Method m = UpdateCheckService.class.getDeclaredMethod("fetchLatestRelease", String.class);
            m.setAccessible(true);
            return m.invoke(null, UpdateCheckService.repoOf());
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        Asserts.assertNotNull("单参 fetchLatestRelease 返回解析后的 release", map);
        Asserts.assertEquals("tag 取自 tag_name", "v3.1.0", map.get("tag"));
        Asserts.assertEquals("name 字段映射", "R3", map.get("name"));
    }

    public void testStripVPrefix_allBranches() throws Throwable {
        Method m = UpdateCheckService.class.getDeclaredMethod("stripVPrefix", String.class);
        m.setAccessible(true);
        Asserts.assertEquals("null→空串", "", m.invoke(null, (Object) null));
        Asserts.assertEquals("小写 v 剥离", "1.2.3", m.invoke(null, "v1.2.3"));
        Asserts.assertEquals("大写 V 剥离", "1.2.3", m.invoke(null, "V1.2.3"));
        Asserts.assertEquals("无前缀原样", "1.2.3", m.invoke(null, "1.2.3"));
        Asserts.assertEquals("仅前导 v/V 剥离(其后字符保留)", "alue", m.invoke(null, "value"));
    }

    public void testFriendlyError_allBranches() throws Throwable {
        Method m = UpdateCheckService.class.getDeclaredMethod("friendlyError", Exception.class);
        m.setAccessible(true);
        String e403 = (String) m.invoke(null, new IllegalStateException("HTTP 403 - rate limited"));
        Asserts.assertContains("403→限流提示", e403, "限流");
        Asserts.assertContains("403→提示 GITHUB_TOKEN", e403, "GITHUB_TOKEN");
        String e429 = (String) m.invoke(null, new IllegalStateException("HTTP 429 too many"));
        Asserts.assertContains("429→限流提示", e429, "限流");
        String e401 = (String) m.invoke(null, new IllegalStateException("HTTP 401 Unauthorized"));
        Asserts.assertContains("401→令牌提示", e401, "GITHUB_TOKEN");
        String e404 = (String) m.invoke(null, new IllegalStateException("HTTP 404 not found"));
        Asserts.assertContains("404→仓库提示", e404, "仓库不存在");
        String eNet = (String) m.invoke(null, new java.io.IOException("connection reset"));
        Asserts.assertContains("网络→检查网络提示", eNet, "api.github.com");
        // 无 message（null）→ 回退用 toString()，仍应给出网络兜底文案且不抛
        String eNull = (String) m.invoke(null, new RuntimeException());
        Asserts.assertContains("null message 仍走兜底", eNull, "api.github.com");
        Asserts.assertContains("null message 含异常类名", eNull, "RuntimeException");
    }

    public void testRepoOf_matchesOwnerSlashRepo() {
        String repo = UpdateCheckService.repoOf();
        Asserts.assertNotNull("repoOf 非空", repo);
        Asserts.assertTrue("repoOf 形如 owner/repo: " + repo, repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"));
    }
}
