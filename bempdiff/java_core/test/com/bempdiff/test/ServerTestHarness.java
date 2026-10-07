package com.bempdiff.test;

import com.bempdiff.server.BempServer;
import com.bempdiff.server.Json;
import com.bempdiff.server.ServerConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * BempServer 集成测试支撑（非测试类，无 testXxx 方法，仅供同包测试复用）。
 *
 * <p>职责：在随机空闲端口用真实 {@link BempServer}（内嵌 com.sun.net.httpserver）拉起服务，
 * 用 {@link java.net.http.HttpClient} 真实发起请求；提供 JSON 解析与本地 AI 服务桩。
 *
 * <p>关于停止：{@link BempServer#start(int)} 内部持有 HttpServer 局部变量且阻塞调用线程，未暴露
 * stop 句柄，故本类把 start() 跑在守护线程上，服务线程为守护线程、不阻碍 JVM 退出；每个用例用
 * 独立空闲端口互不冲突，整个 TestRunner 进程结束由 {@code System.exit} 统一回收。AI 桩持引用可显式 stop。
 */
public final class ServerTestHarness {

    private ServerTestHarness() {}

    /** 一个已启动的测试服务句柄（仅暴露端口与便捷 HTTP 调用）。 */
    public static final class Server {
        public final int port;
        public final String base;
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();

        Server(int port) { this.port = port; this.base = "http://127.0.0.1:" + port; }

        HttpResponse<String> request(String method, String path, String body) throws Exception {
            HttpRequest.BodyPublisher pub = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30)).method(method, pub);
            return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
        HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
        HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body); }
        HttpResponse<String> del(String path) throws Exception { return request("DELETE", path, null); }
        HttpResponse<String> options(String path) throws Exception { return request("OPTIONS", path, null); }

        HttpResponse<byte[]> getRaw(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<byte[]> postRaw(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    /** 探测一个当前空闲端口（随后立即用于绑定）。 */
    public static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    /** 用给定配置与 webroot 拉起 BempServer，等待端口可连后返回句柄。 */
    public static Server launch(ServerConfig config, Path webroot) throws Exception {
        BempServer server = new BempServer(config, webroot);
        int port = freePort();
        final int boundPort = port;
        Thread t = new Thread(() -> {
            try {
                server.start(boundPort);
            } catch (Exception ignored) {
                // start() 正常情况下阻塞至进程退出；此处仅在端口冲突等异常时静默，awaitPort 会兜底超时
            }
        }, "bdf-test-server-" + port);
        t.setDaemon(true);
        t.start();
        awaitPort(port);
        return new Server(port);
    }

    /** 轮询直到端口可建立 TCP 连接（服务已 bind）。 */
    public static void awaitPort(int port) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            try (Socket sk = new Socket()) {
                sk.connect(new InetSocketAddress("127.0.0.1", port), 300);
                return;
            } catch (IOException e) {
                Thread.sleep(25);
            }
        }
        throw new IllegalStateException("BempServer 未能在端口启动: " + port);
    }

    /** 构造一个临时 ServerConfig（落盘到 tempRoot 下唯一文件）。 */
    public static ServerConfig newConfig(Path tempRoot) throws IOException {
        Path cfgFile = tempRoot.resolve("cfg-" + System.nanoTime() + ".properties");
        return new ServerConfig(cfgFile);
    }

    /** 解析 JSON 响应体为 Map。 */
    public static Map<String, Object> json(HttpResponse<String> r) {
        return Json.parseObject(r.body());
    }

    /**
     * 起一个本地 AI 服务桩：GET /v1/models 固定返回 {@code modelsJson}（OpenAI 兼容形态）。
     * 返回的 HttpServer 由调用方在 finally 中 {@code stop(0)}。base url 形如 http://127.0.0.1:port/v1。
     */
    public static com.sun.net.httpserver.HttpServer startAiStub(String modelsJson) throws IOException {
        int port = freePort();
        com.sun.net.httpserver.HttpServer stub =
                com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        stub.createContext("/v1/models", ex -> {
            byte[] b = modelsJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (java.io.OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        stub.setExecutor(null); // 默认串行分发线程（daemon），够用
        stub.start();
        return stub;
    }

    /** 生成 URL 查询参数用的编码值。 */
    public static String enc(String v) {
        try {
            return java.net.URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 用文件绝对路径拼一个 compare 请求 JSON（复用生产 Json 写器，转义正确）。 */
    public static String compareBody(String leftType, String leftPath, String rightPath) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("leftType", leftType);
        m.put("leftPath", leftPath);
        m.put("rightPath", rightPath);
        return Json.write(m);
    }
}
