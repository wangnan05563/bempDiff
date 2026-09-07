package com.bempdiff.test;

import com.bempdiff.server.UpdateCheckService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 锁定 POST /api/update/check 的契约（更新检查服务层）。
 *
 * <p>不依赖外网：启动一个进程内假 GitHub（HttpServer），把 UpdateCheckService.API_BASE
 * 通过反射临时指向本地地址，serve 各场景的 releases/latest 响应，断言 check() 的
 * 响应结构（字段、upToDate 口径、message 语义、错误降级）。用例结束后恢复 API_BASE。
 *
 * <p>端点层（BempServer.handleUpdateCheck）仅做薄封装：读 body.current、失败兜底 200，
 * 契约主体即本服务的 check() 返回值，故在此锁住即可。
 */
public final class UpdateCheckServiceContractTest {

    // 当前场景：决定假 GitHub 返回的状态码与响应体（每个用例先设定再断言）
    private static volatile String scenario = "empty";
    // 观测量：假服务收到请求的次数（断言缓存命中不触发二次请求）+ 最近一次 Authorization 头
    static volatile int hits = 0;
    static volatile String capturedAuth = null;

    private static final HttpServer SERVER;
    private static final int PORT;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PORT = SERVER.getAddress().getPort();
            SERVER.createContext("/", UpdateCheckServiceContractTest::handle);
            SERVER.setExecutor(null);
            SERVER.start();
        } catch (IOException e) {
            throw new RuntimeException("无法启动假 GitHub 服务", e);
        }
    }

    private static int statusFor(String s) {
        switch (s) {
            case "noRelease": return 404;
            case "http500":   return 500;
            case "rate403":   return 403;
            default:          return 200;
        }
    }

    private static String bodyFor(String s) {
        switch (s) {
            case "noRelease": return "{\"message\":\"Not Found\"}";
            case "http500":   return "{\"message\":\"boom\"}";
            case "rate403":   return "{\"message\":\"API rate limit exceeded for 1.2.3.4.\"}";
            case "hasNew":    return "{\"tag_name\":\"v2.0.0\",\"name\":\"Release 2.0.0\","
                    + "\"html_url\":\"https://github.com/x/bempDiff/releases/tag/v2.0.0\","
                    + "\"published_at\":\"2026-08-31T00:00:00Z\",\"body\":\"- feature\\n- fix\"}";
            case "upToDate":  return "{\"tag_name\":\"v0.5.0\",\"name\":\"\","
                    + "\"html_url\":\"https://github.com/x/bempDiff/releases/tag/v0.5.0\","
                    + "\"published_at\":\"\",\"body\":\"\"}";
            default:          return "{}";
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        hits++;
        capturedAuth = ex.getRequestHeaders().getFirst("Authorization");
        byte[] body = bodyFor(scenario).getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusFor(scenario), body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    /** 每个用例起手：设场景 + 清掉上条用例残留的进程内缓存 + 重置观测量，保证隔离。 */
    private static void freshScenario(String s) {
        scenario = s;
        hits = 0;
        capturedAuth = null;
        UpdateCheckService.clearLatestCache();
    }

    /** 把 API_BASE 指向本地假 GitHub 后调用 check()，finally 恢复生产 HTTPS 值。 */
    private static Map<String, Object> runCheck(String current) throws Throwable {
        return runCheck(current, null);
    }

    private static Map<String, Object> runCheck(String current, String token) throws Throwable {
        Field f = UpdateCheckService.class.getDeclaredField("API_BASE");
        f.setAccessible(true);
        String orig = (String) f.get(null);
        try {
            f.set(null, "http://127.0.0.1:" + PORT + "/");
            return UpdateCheckService.check(current, token);
        } finally {
            f.set(null, orig);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> latest(Map<String, Object> r) {
        Object v = r.get("latest");
        return (v instanceof Map) ? (Map<String, Object>) v : null;
    }

    // ---------------- 用例 ----------------

    public void testContract_newVersion_reportsAndMaterializesLatest() throws Throwable {
        freshScenario("hasNew");
        Map<String, Object> r = runCheck("1.0.0");
        Asserts.assertTrue("成功应在 ok=true", Boolean.TRUE.equals(r.get("ok")));
        Asserts.assertEquals("检测到新版本 upToDate=false", Boolean.FALSE, r.get("upToDate"));
        Asserts.assertEquals("当前版本原样返回", "1.0.0", r.get("current"));
        Asserts.assertEquals("仓库地址与生效掩码一致(非硬编码仓库名)",
                UpdateCheckService.repoOf(), r.get("repo"));
        Asserts.assertEquals("首次检查 cached=false", Boolean.FALSE, r.get("cached"));
        Map<String, Object> lt = latest(r);
        Asserts.assertNotNull("有最新版本时 latest 非空", lt);
        Asserts.assertEquals("tag 取自 tag_name", "v2.0.0", lt.get("tag"));
        Asserts.assertEquals("name 取自 name", "Release 2.0.0", lt.get("name"));
        Asserts.assertContains("html_url 映射为 url",
                String.valueOf(lt.get("url")), "/releases/tag/v2.0.0");
        Asserts.assertTrue("publishedAt 原样带回", !String.valueOf(lt.get("publishedAt")).isEmpty());
        Asserts.assertContains("提示语含发现新版本", String.valueOf(r.get("message")), "发现新版本");
    }

    public void testContract_upToDate_whenCurrentGeLatest() throws Throwable {
        freshScenario("upToDate");
        // tag=v0.5.0，当前 1.0.0 → 已最新
        Map<String, Object> r = runCheck("1.0.0");
        Asserts.assertEquals("当前>最新 upToDate=true", Boolean.TRUE, r.get("upToDate"));
        Asserts.assertContains("提示已最新", String.valueOf(r.get("message")), "当前已是最新版本");
        // 当前与 tag 相等且 tag 带前导 v：剥离 v 后判为已最新
        Map<String, Object> r2 = runCheck("v0.5.0");
        Asserts.assertEquals("剥离 v 前缀后相等 upToDate=true", Boolean.TRUE, r2.get("upToDate"));
    }

    public void testContract_newerCurrent_stripVPrefix() throws Throwable {
        freshScenario("upToDate");
        Map<String, Object> r = runCheck("0.5.0"); // tag v0.5.0 → 剥离后相等
        Asserts.assertEquals("0.5.0>=v0.5.0 upToDate=true", Boolean.TRUE, r.get("upToDate"));
    }

    public void testContract_noRelease_okTrueNullLatest() throws Throwable {
        freshScenario("noRelease");
        Map<String, Object> r = runCheck("1.0.0");
        Asserts.assertEquals("无 Release 仍 ok=true", Boolean.TRUE, r.get("ok"));
        Asserts.assertNull("latest 为 null", r.get("latest"));
        Asserts.assertNull("upToDate 为 null(未比较)", r.get("upToDate"));
        Asserts.assertContains("提示创建 Release",
                String.valueOf(r.get("message")), "还没有发布任何 Release");
    }

    public void testContract_emptyBody_currentUnknown() throws Throwable {
        freshScenario("empty");
        Map<String, Object> r = runCheck("");
        Asserts.assertEquals("空 body 仍 ok=true", Boolean.TRUE, r.get("ok"));
        Asserts.assertNotNull("latest 有对象但字段为空", latest(r));
        Asserts.assertNull("upToDate 为 null(未比较)", r.get("upToDate"));
        Asserts.assertContains("提示当前版本未知", String.valueOf(r.get("message")), "当前版本未知");
    }

    public void testContract_http500_degradesWithLastError() throws Throwable {
        freshScenario("http500");
        Map<String, Object> r = runCheck("1.0.0");
        Asserts.assertEquals("非 2xx/404 应 ok=false", Boolean.FALSE, r.get("ok"));
        Asserts.assertNull("失败时 latest 为 null", r.get("latest"));
        Asserts.assertContains("lastError 携带状态码", String.valueOf(r.get("lastError")), "HTTP 500");
        Asserts.assertContains("message 以检查更新失败开头",
                String.valueOf(r.get("message")), "检查更新失败");
    }

    public void testContract_rateLimit_friendlyMessage() throws Throwable {
        freshScenario("rate403");
        Map<String, Object> r = runCheck("1.0.0");
        Asserts.assertEquals("限流应 ok=false", Boolean.FALSE, r.get("ok"));
        Asserts.assertContains("有限流排查提示", String.valueOf(r.get("message")), "限流");
        Asserts.assertContains("lastError 含 HTTP 403", String.valueOf(r.get("lastError")), "HTTP 403");
    }

    // ---------- 新增：令牌透传 + 进程内缓存（本轮需求） ----------

    public void testToken_configuredTokenUsedAsBearer() throws Throwable {
        freshScenario("hasNew");
        runCheck("1.0.0", "ghp_secret");
        Asserts.assertEquals("显式令牌应以 Bearer 透传", "Bearer ghp_secret", capturedAuth);
    }

    public void testCache_secondCheckWithinTtlSkipsNetwork() throws Throwable {
        freshScenario("hasNew");
        Map<String, Object> r1 = runCheck("1.0.0");
        Asserts.assertEquals("首次 cached=false", Boolean.FALSE, r1.get("cached"));
        Map<String, Object> r2 = runCheck("1.0.0");
        Asserts.assertEquals("TTL 内第二次 cached=true", Boolean.TRUE, r2.get("cached"));
        Asserts.assertEquals("第二次命中缓存不再请求假服务", 1, hits);
    }

    public void testCache_tokenChangeInvalidatesCache() throws Throwable {
        freshScenario("hasNew");
        runCheck("1.0.0", "tokA");
        int afterA = hits;
        runCheck("1.0.0", "tokB"); // 令牌不同 → 视为不同数据源，应重新请求
        Asserts.assertEquals("令牌变化后应重新拉取", hits, afterA + 1);
    }
}