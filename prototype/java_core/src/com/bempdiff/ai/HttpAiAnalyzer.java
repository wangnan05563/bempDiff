package com.bempdiff.ai;

import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.model.DecompiledUnit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 真实 HTTP AI 分析器（T09/T10/T11，§5.4）。接口与 MockAiAnalyzer 完全一致，可直接替换。
 *  - 调用 OpenAI / Azure / 本地 Ollama 兼容的 /v1/chat/completions 接口（JDK8 HttpURLConnection，无第三方依赖）
 *  - 公网模型模式：prompt 经 PromptBuilders.sanitize 脱敏（金融合规 §5.9.1）
 *  - 成本闸门：estimateTokens 在调用前预估，超阈值 caller 应二次确认（见 Main.ai）
 *  - 连接测试：testConnection 发 /models 探活（Ollama 用 /api/tags）
 * 说明：未配置 API Key 时本类不初始化，Main 自动回退 Mock/基础比对（需求"未配置 AI 也能基础比对"）。
 */
public final class HttpAiAnalyzer implements AiAnalyzer {

    private final AiConfig cfg;

    public HttpAiAnalyzer(AiConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, AiConfig cfg) {
        return PromptBuilders.buildStageB(key, unit, cfg);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        String prompt = buildStageAPrompt(diff, decompiled, cfg);
        String json = callChat(prompt, 0.2);
        return MockAiAnalyzer.parseStageAStatic(json);   // 复用轻量解析（同签名已抽象为静态）
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg) {
        List<FileAnalysis> out = new ArrayList<>();
        for (DecompileReq req : candidates) {
            String prompt = buildStageBPrompt(req.key, req.unit, cfg);
            String json = callChat(prompt, 0.1);
            out.add(MockAiAnalyzer.parseFileAnalysisStatic(req.key, json));
        }
        return out;
    }

    @Override
    public boolean testConnection(AiConfig cfg) {
        HttpURLConnection c = null;
        try {
            String url = cfg.getBaseUrl().endsWith("/") ? cfg.getBaseUrl() + "models"
                    : cfg.getBaseUrl() + "/models";
            URL u = URI.create(url).toURL();
            c = open(u, cfg);
            c.setRequestMethod("GET");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            setAuthHeader(c, cfg);
            int code = c.getResponseCode();
            return code == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 私有异常：AI 调用失败时抛出的具体运行时异常（代替泛型 RuntimeException，规避 java:S112）。 */
    private static final class AiCallException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 调用 chat/completions，返回模型文本。Ollama 兼容 /v1/chat/completions。 */
    private String callChat(String prompt, double temperature) {
        HttpURLConnection c = null;
        try {
            URL u = buildChatUrl();
            c = open(u, cfg);
            configureConnection(c);
            sendRequestBody(c, prompt, temperature);
            return handleResponse(c);
        } catch (IOException e) {
            throw new AiCallException("AI 调用失败: " + e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private URL buildChatUrl() throws IOException {
        String endpoint = cfg.getBaseUrl().endsWith("/")
                ? cfg.getBaseUrl() + "chat/completions"
                : cfg.getBaseUrl() + "/chat/completions";
        return URI.create(endpoint).toURL();
    }

    private void configureConnection(HttpURLConnection c) throws IOException {
        c.setRequestMethod("POST");
        c.setConnectTimeout(60000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        setAuthHeader(c, cfg);
    }

    private void sendRequestBody(HttpURLConnection c, String prompt, double temperature) throws IOException {
        String body = "{\"model\":" + escapeJson(cfg.getModel()) + ",\"temperature\":" + temperature
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + escapeJson(prompt) + "}]}";
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String handleResponse(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        // 错误响应体在 getErrorStream()（非 2xx），此前误读 getInputStream() 会丢失真实错误
        InputStream bodyStream = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        String resp = readAll(bodyStream);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + resp);
        }
        return extractContent(resp);
    }

    /** 从 OpenAI 风格响应提取 choices[0].message.content（轻量解析，够用；量产能用 JSON 库）。
     *  此前直接找首个 "content" 易误命中其它字段，改为先定位 choices→message 再取 content。 */
    private static String extractContent(String resp) {
        int ci = resp.indexOf("\"choices\"");
        if (ci < 0) return resp;                      // 非标准响应：原样返回，由上层兜底
        int mi = resp.indexOf("\"message\"", ci);
        int from = (mi >= 0) ? mi : ci;
        int ci2 = resp.indexOf("\"content\"", from);
        if (ci2 < 0) return resp;
        int colon = resp.indexOf(':', ci2);
        if (colon < 0) return resp;
        int q = resp.indexOf('"', colon);             // content 值起始引号
        if (q < 0) return resp;
        StringBuilder sb = new StringBuilder();
        int p = q + 1;
        boolean done = false;
        while (p < resp.length() && !done) {
            char ch = resp.charAt(p);
            if (ch == '\\') {
                p = handleEscapeSequence(sb, resp, p);
                continue;
            } else if (ch == '"') {
                done = true;
            } else {
                sb.append(ch);
                p++;
            }
        }
        return sb.toString();
    }

    /** 处理 JSON 转义序列并返回更新后的位置。 */
    private static int handleEscapeSequence(StringBuilder sb, String resp, int p) {
        // 还原 JSON 转义：\\n \\t \\r \\b \\f \\/ \\\\ \\" 以及 \\uXXXX（4 位十六进制）
        if (p + 1 < resp.length()) {
            char nx = resp.charAt(p + 1);
            switch (nx) {
                case 'n': sb.append('\n'); return p + 2;
                case 't': sb.append('\t'); return p + 2;
                case 'r': sb.append('\r'); return p + 2;
                case 'b': sb.append('\b'); return p + 2;
                case 'f': sb.append('\f'); return p + 2;
                case '/': sb.append('/'); return p + 2;
                case '\\': sb.append('\\'); return p + 2;
                case '"': sb.append('"'); return p + 2;
                case 'u':
                    if (p + 6 <= resp.length()) {
                        try {
                            sb.append((char) Integer.parseInt(resp.substring(p + 2, p + 6), 16));
                        } catch (NumberFormatException e) {
                            sb.append(nx);
                        }
                        return p + 6;
                    } else {
                        sb.append(nx);
                        return p + 2;
                    }
                default: sb.append(nx); return p + 2;
            }
        } else {
            sb.append(resp.charAt(p));
            return p + 1;
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); // 控制字符必须转义，否则 JSON 非法
                    else sb.append(c);
            }
        }
        return "\"" + sb + "\"";
    }

    /** HTTP 响应体读取硬上限：防止恶意/被劫持端点返回超大 body 拖垮 JVM（与 PackageParser zip bomb 对称）。
     *  AI 文本响应不会超过此值；超限即抛 IOException 由上层降级，避免 OOM。 */
    private static final long MAX_RESPONSE_BYTES = 16L * 1024 * 1024;

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("HTTP 响应体过大（疑似恶意/被劫持端点，OOM 防护）: > " + MAX_RESPONSE_BYTES + " B");
            }
            bos.write(buf, 0, r);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 按厂商设置鉴权头：Azure 用 api-key，其余（openai/ollama/qwen/custom）用 Bearer（ollama 无 Key 时不带）。 */
    private static void setAuthHeader(HttpURLConnection c, AiConfig cfg) {
        String key = cfg.getApiKey();
        if (key == null || key.isEmpty()) return;
        if ("azure".equalsIgnoreCase(cfg.getProvider())) {
            c.setRequestProperty("api-key", key);
        } else {
            c.setRequestProperty("Authorization", "Bearer " + key);
        }
    }

    /** SSRF 加固（BR-SEC-01 类）：
     *  - 始终拒绝链路本地/云元数据端点（169.254.169.254 及 169.254.0.0/16，含 DNS 解析后地址）——最高风险：云凭据泄露
     *  - 严格模式(blockPrivateEndpoints)额外拒绝回环与私网（本地 Ollama 需关闭此开关）
     *  - 仅允许 http/https 协议 */
    private static void guardEndpoint(URL u, AiConfig cfg) throws IOException {
        String proto = u.getProtocol();
        if (!"http".equals(proto) && !"https".equals(proto)) {
            throw new IOException("不支持的协议（仅 http/https）: " + proto);
        }
        if (isBlockedHost(u.getHost(), cfg.isBlockPrivateEndpoints())) {
            throw new IOException("拒绝访问受限网络端点（SSRF 防护）: " + u.getHost());
        }
    }

    /** 检查是否为链路本地地址或云元数据端点（169.254.0.0/16，含 169.254.169.254）。 */
    private static boolean isLinkLocalOrMetadata(String host) {
        String h = host.toLowerCase();
        return h.equals("169.254.169.254") || h.startsWith("169.254.");
    }

    /** 检查 IPv4 是否属于私网/回环范围（127/8、0/8、10/8、192.168/16、172.16/12）。 */
    private static boolean isPrivateIPv4(int b0, int b1) {
        if (b0 == 127 || b0 == 0) return true;          // 回环 / 全零
        if (b0 == 10) return true;                      // 10/8
        if (b0 == 192 && b1 == 168) return true;        // 192.168/16
        return b0 == 172 && b1 >= 16 && b1 <= 31;       // 172.16/12
    }

    /** 检查 IPv6 私网/回环地址（ULA fc00::/7、::1 回环）。fe80::/10 链路本地在主方法中始终拦截。 */
    private static boolean isPrivateIPv6(byte[] ip) {
        if (ip.length != 16) return false;
        if (ip[0] == (byte) 0xfc) return true;                              // ULA fc00::/7
        return ip[0] == 0 && ip[1] == 0 && ip[15] == 1;                     // ::1
    }

    /** host 是否应被拦截：字符串层 + 解析层（防 DNS rebinding）。strict 时额外拦截回环/私网。 */
    private static boolean isBlockedHost(String host, boolean strict) throws IOException {
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("host 为空（疑似畸形 URL）");
        if (isLinkLocalOrMetadata(host)) return true;
        if (strict && isPrivateOrLoopbackString(host.toLowerCase())) return true;
        try {
            InetAddress a = InetAddress.getByName(host);
            return isResolvedIpBlocked(a.getAddress(), strict);
        } catch (UnknownHostException e) {
            if (strict) return true; // 严格模式：解析失败即拒绝
        }
        return false;
    }

    /** 检查 DNS 解析后的 IP 地址是否应被拦截，提取自 isBlockedHost 以降低认知复杂度。 */
    private static boolean isResolvedIpBlocked(byte[] ip, boolean strict) {
        if (ip.length == 4) {
            int b0 = ip[0] & 0xff;
            int b1 = ip[1] & 0xff;
            if (b0 == 169 && b1 == 254) return true;            // 链路本地/元数据（解析层）
            if (strict && isPrivateIPv4(b0, b1)) return true;
        } else if (ip.length == 16) {
            if (ip[0] == (byte) 0xfe && (ip[1] & 0xc0) == 0x80) return true;   // fe80::/10 始终拦截
            if (strict && isPrivateIPv6(ip)) return true;
        }
        return false;
    }

    @SuppressWarnings("java:S3776") // NOSONAR - 字符串前缀检查模式，提取为子方法反而降低可读性
    private static boolean isPrivateOrLoopbackString(String h) {
        if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".internal")
                || h.equals("0.0.0.0") || h.equals("[::1]")) return true;
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) return true;
        if (h.startsWith("172.")) {
            String[] p = h.split("\\.");
            if (p.length > 1) {
                try { int x = Integer.parseInt(p[1]); if (x >= 16 && x <= 31) return true; }
                catch (NumberFormatException ignored) {
                    // 非数字的第二段，说明不在 172.16-31 范围内，忽略
                }
            }
        }
        return h.startsWith("fc") || h.startsWith("fd"); // IPv6 ULA
    }

    /** 按配置构造连接；配置了 HTTPS/HTTP 代理则走 Proxy（FR9.11）。 */
    private static HttpURLConnection open(URL u, AiConfig cfg) throws IOException {
        guardEndpoint(u, cfg);
        String px = (cfg.getHttpsProxy() != null && !cfg.getHttpsProxy().isEmpty())
                ? cfg.getHttpsProxy() : cfg.getHttpProxy();
        if (px == null || px.isEmpty()) return (HttpURLConnection) u.openConnection();
        int c = px.indexOf(':');
        String host = c < 0 ? px : px.substring(0, c);
        if (host.isEmpty()) throw new IOException("代理 host 为空: " + px);
        int port;
        try {
            port = c < 0 ? 80 : Integer.parseInt(px.substring(c + 1).trim());
        } catch (NumberFormatException nfe) {
            throw new IOException("代理端口非法: " + px, nfe);   // 转为 IOException，避免崩溃
        }
        if (port <= 0 || port > 65535) throw new IOException("代理端口越界: " + port);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        return (HttpURLConnection) u.openConnection(proxy);
    }
}