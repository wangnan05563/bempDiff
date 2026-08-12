package com.bempdiff.ai;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    private static final Logger LOG = Logger.getLogger(HttpAiAnalyzer.class.getName());

    /** 偶发超时/网络抖动的重试次数（共 MAX_RETRIES+1 次尝试），应对慢速/偶发超时的供应商。 */
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BACKOFF_MS = 1500;

    /** 最近一次 testConnection 的失败原因（null 表示成功或未调用）。用于 UI 诊断展示。 */
    private volatile String lastError = null;

    /**
     * 通用兼容 SSLSocketFactory（静态初始化时创建并设为全局默认）。
     *
     * <p>问题背景：jpackage 打包的 jlinked 运行时与完整 JDK 的 TLS 默认配置不同，
     * 导致对部分 HTTPS 站点（如 SiliconFlow）握手失败（handshake_failure），
     * 而同一 JDK 命令行运行则正常。
     *
     * <p>解决方案：包装默认 SSLSocketFactory，在每个新创建的 SSLSocket 上强制：
     * <ol>
     *   <li>协议限定为 TLSv1.2 + TLSv1.3</li>
     *   <li>过滤密码套件：排除弱套件/被禁套件，保留 ECDHE/DHE + AES/CHACHA20</li>
     *   <li>通过 {@link HttpsURLConnection#setDefaultSSLSocketFactory} 设为全局默认</li>
     * </ol>
     */
    private static final SSLSocketFactory UNIVERSAL_COMPAT_FACTORY;

    /**
     * 静态初始化：构建通用兼容 TLS 工厂并注入为全局默认 + 启用系统代理。
     *
     * 三层防御：
     * 1. 系统属性 https.protocols 兜底
     * 2. 全局默认工厂替换（所有 HttpURLConnection 自动生效）
     * 3. testConnection 内按需回退纯 TLSv1.2
     */
    static {
        // [Layer 1] 系统属性兜底
        String current = System.getProperty("https.protocols");
        if (current == null || !current.contains("TLSv1.2")) {
            System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        }
        // 启用系统代理
        if (!"true".equalsIgnoreCase(System.getProperty("java.net.useSystemProxies"))) {
            System.setProperty("java.net.useSystemProxies", "true");
        }

        // [Layer 2] 构建通用兼容工厂并设为全局默认
        UNIVERSAL_COMPAT_FACTORY = buildUniversalCompatibleFactory();
        if (UNIVERSAL_COMPAT_FACTORY != null) {
            try {
                HttpsURLConnection.setDefaultSSLSocketFactory(UNIVERSAL_COMPAT_FACTORY);
                System.out.println("[TLS] 通用兼容 SSLSocketFactory 已设为全局默认");
            } catch (Exception e) {
                System.err.println("[WARN] 设置默认 SSLSocketFactory 失败: " + e.getMessage());
            }
        } else {
            System.err.println("[WARN] 通用兼容工厂创建失败，将使用 JDK 默认 TLS 配置");
        }
    }

    /**
     * 构建"通用兼容"SSLSocketFactory：包装默认工厂，在每条新连接上强制协议和密码套件。
     * 不替换 TrustManager（保留标准 CA 验证），仅调整协议版本和密码套件列表以提升兼容性。
     */
    private static SSLSocketFactory buildUniversalCompatibleFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, null, null);
            final SSLSocketFactory base = ctx.getSocketFactory();

            return new SSLSocketFactory() {
                public Socket createSocket(String host, int port) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port));
                }
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port, localHost, localPort));
                }
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return configure((SSLSocket) base.createSocket(host, port));
                }
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                    return configure((SSLSocket) base.createSocket(address, port, localAddress, localPort));
                }
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return configure((SSLSocket) base.createSocket(s, host, port, autoClose));
                }
                public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
                public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }

                /** 对每个新创建的 SSLSocket 强制设置兼容参数。 */
                private SSLSocket configure(SSLSocket s) {
                    s.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                    String[] supported = s.getSupportedCipherSuites();
                    List<String> filtered = new ArrayList<>(supported.length);
                    for (String cs : supported) {
                        String csl = cs.toLowerCase();
                        if (csl.contains("_null_") || csl.contains("_anon_") ||
                            csl.contains("_export_") || csl.contains("_rc4_") ||
                            csl.contains("_des_") || csl.contains("_3des_") ||
                            csl.contains("_md5_")) {
                            continue;
                        }
                        if (csl.contains("_aes_") || csl.contains("_chacha20_")) {
                            filtered.add(cs);
                        }
                    }
                    if (!filtered.isEmpty()) {
                        s.setEnabledCipherSuites(filtered.toArray(new String[0]));
                    }
                    return s;
                }
            };
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.err.println("[WARN] 无法创建通用兼容 SSLContext: " + e.getMessage());
            return null;
        }
    }

    public HttpAiAnalyzer(AiConfig cfg) {
        this.cfg = cfg;
    }

    /** 获取最近一次连接测试的失败详情（供 UI 层读取）。 */
    public String getLastError() { return lastError; }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg);
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg, ctx);
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
            String prompt = buildStageBPrompt(req.key, req.unit, req.fileClass, cfg);
            String json = callChat(prompt, 0.1);
            out.add(MockAiAnalyzer.parseFileAnalysisStatic(req.key, json));
        }
        return out;
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx);
        String json = callChat(prompt, 0.2);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        return MockAiAnalyzer.parseStageAStatic(json, withCtx);
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx) {
        List<FileAnalysis> out = new ArrayList<>();
        boolean withCtx = ctx != null && !ctx.isEmpty();
        for (DecompileReq req : candidates) {
            String prompt = PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, ctx);
            String json = callChat(prompt, 0.1);
            out.add(MockAiAnalyzer.parseFileAnalysisStatic(req.key, json, withCtx));
        }
        return out;
    }

    @Override
    public boolean testConnection(AiConfig cfg) {
        return testConnectionMultiStrategy(cfg);
    }

    /**
     * 多策略降级连接测试（第四轮 TLS 修复）。
     *
     * <p>背景：jpackage 打包的 jlinked 运行时中，单一 TLS 配置在部分网络环境下
     * 持续 handshake_failure（服务端/中间设备拒绝 ClientHello）。前三轮修复
     * （系统属性→通用兼容工厂→原始 SSLSocket 单配置）均在命令行验证通过，
     * 但 GUI exe 内仍失败——说明问题与运行时环境+网络路径的组合相关，
     * 需要按优先级尝试多种已知兼容组合。
     *
     * <p>策略顺序（按兼容性从高到低）：
     * <ol>
     *   <li><b>S1: TLSv1.2 纯净模式</b> — 仅 TLSv1.2，默认工厂，全量支持密码套件。
     *       兼容性最高：多数中间设备/老旧网关仅支持 1.2，广告 1.3 会触发 reset。</li>
     *   <li><b>S2: TLSv1.3 纯净模式</b> — 仅 TLSv1.3，默认工厂，全量支持密码套件。
     *       现代服务器首选。</li>
     *   <li><b>S3: 双协议 + 密码过滤</b> — TLSv1.2+1.3，UNIVERSAL_COMPAT_FACTORY 过滤弱套件。
     *       前三轮使用的配置（保留作为后备）。</li>
     *   <li><b>S4: 双协议 + 新鲜 SSLContext</b> — 用 {@code SSLContext.getDefault()} 重新初始化。
     *       绕开 jlinked runtime 可能损坏的默认 TLS 实例状态。</li>
     * </ol>
     */
    private boolean testConnectionMultiStrategy(AiConfig cfg) {
        lastError = null;
        String urlStr = cfg.getBaseUrl().endsWith("/") ? cfg.getBaseUrl() + "models"
                : cfg.getBaseUrl() + "/models";
        java.net.URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            lastError = "URL 格式错误: " + urlStr;
            return false;
        }
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        // 定义策略数组：每种策略 = (名称, 协议数组, 工厂, 是否过滤密码套件)
        // S1: TLSv1.2-only 全套件 → 兼容性最高
        // S2: TLSv1.3-only 全套件 → 现代服务器
        // S3: 双协议 + 兼容工厂过滤 → 前三轮方案
        // S4: 双协议 + 新鲜 SSLContext → 绕开可能损坏的实例
        SSLSocketFactory defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();

        Object[][] strategies = {
            {"S1-TLS12-纯净",   new String[]{"TLSv1.2"},          defaultFactory,           false},
            {"S2-TLS13-纯净",   new String[]{"TLSv1.3"},          defaultFactory,           false},
            {"S3-双协议-过滤",  new String[]{"TLSv1.2","TLSv1.3"}, UNIVERSAL_COMPAT_FACTORY, true },
            {"S4-新鲜Context",  new String[]{"TLSv1.2","TLSv1.3"}, buildFreshFactory(),      false},
        };

        StringBuilder allDiagnostics = new StringBuilder();
        for (int i = 0; i < strategies.length; i++) {
            String name = (String) strategies[i][0];
            String[] protocols = (String[]) strategies[i][1];
            SSLSocketFactory factory = (SSLSocketFactory) strategies[i][2];
            boolean filterCiphers = (Boolean) strategies[i][3];

            if (factory == null) {
                System.out.println("[TLS-" + name + "] 跳过(工厂为null)");
                continue;
            }

            System.out.println("[TLS-" + name + "] 尝试连接 " + host + ":" + port
                    + " (protocols=" + String.join(",", protocols) + ")");
            long t0 = System.currentTimeMillis();
            try {
                Result r = rawSocketConnect(host, port, path, cfg.getApiKey(),
                        factory, protocols, filterCiphers);
                long elapsed = System.currentTimeMillis() - t0;
                if (r.ok) {
                    System.out.println("[TLS-" + name + "] SUCCESS (" + elapsed + "ms)");
                    lastError = null;
                    return true;
                }
                System.out.println("[TLS-" + name + "] HTTP失败: " + r.errorMsg + " (" + elapsed + "ms)");
                allDiagnostics.append(name).append(":").append(r.errorMsg).append("; ");
            } catch (SSLHandshakeException e) {
                long elapsed = System.currentTimeMillis() - t0;
                String detail = extractHandshakeDetail(e);
                System.out.println("[TLS-" + name + "] 握手失败: " + detail + " (" + elapsed + "ms)");
                allDiagnostics.append(name).append(":").append(detail).append("; ");
            } catch (IOException e) {
                long elapsed = System.currentTimeMillis() - t0;
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                System.out.println("[TLS-" + name + "] IO异常: " + msg + " (" + elapsed + "ms)");
                allDiagnostics.append(name).append(":").append(msg).append("; ");
                // 连接超时/拒绝等非握手错误不需要继续尝试其他策略
                if (!isHandshakeRelated(e)) {
                    lastError = classifyIoError(msg);
                    return false;
                }
            }
        }

        // 所有策略均失败
        lastError = "TLS 握手失败（已尝试 4 种策略均被拒绝）: " + allDiagnostics
                + "建议：填写 HTTPS 代理（如 http://127.0.0.1:7890）或检查防火墙/中间设备设置";
        return false;
    }

    /** 原始 SSLSocket 连接结果 */
    private static class Result {
        final boolean ok;
        final String errorMsg;
        Result(boolean ok, String errorMsg) { this.ok = ok; this.errorMsg = errorMsg; }
    }

    /**
     * 使用指定参数执行原始 SSLSocket HTTPS 连接测试。
     *
     * @param host 目标主机
     * @param port 目标端口
     * @param path HTTP 路径（含查询字符串）
     * @param apiKey API Key（用于 Authorization 头）
     * @param factory SSLSocketFactory
     * @param protocols 启用的 TLS 协议版本
     * @param filterCiphers 是否过滤密码套件（排除弱套件）
     * @return Result(ok=true 表示连接成功且返回 HTTP 200)
     */
    private static Result rawSocketConnect(String host, int port, String path,
            String apiKey, SSLSocketFactory factory, String[] protocols,
            boolean filterCiphers) throws IOException {

        Socket rawSocket = null;
        SSLSocket sslSocket = null;
        try {
            rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(host, port), 8000);

            sslSocket = (SSLSocket) factory.createSocket(rawSocket, host, port, true);
            sslSocket.setEnabledProtocols(protocols);

            if (filterCiphers) {
                String[] supported = sslSocket.getSupportedCipherSuites();
                List<String> filtered = new ArrayList<>(supported.length);
                for (String cs : supported) {
                    String csl = cs.toLowerCase();
                    if (csl.contains("_null_") || csl.contains("_anon_") ||
                        csl.contains("_export_") || csl.contains("_rc4_") ||
                        csl.contains("_des_") || csl.contains("_3des_") ||
                        csl.contains("_md5_")) {
                        continue;
                    }
                    if (csl.contains("_aes_") || csl.contains("_chacha20_")) {
                        filtered.add(cs);
                    }
                }
                if (!filtered.isEmpty()) {
                    sslSocket.setEnabledCipherSuites(filtered.toArray(new String[0]));
                }
            }

            // 诊断：打印实际生效的协议和密码套件
            System.out.println("[TLS-DIAG] 握手前 enabledProtocols="
                    + String.join(",", sslSocket.getEnabledProtocols())
                    + " enabledCipherSuites count=" + sslSocket.getEnabledCipherSuites().length);

            sslSocket.startHandshake();

            System.out.println("[TLS-DIAG] 握手成功 session="
                    + sslSocket.getSession().getProtocol()
                    + " cipher=" + sslSocket.getSession().getCipherSuite());

            // 发送 HTTP/1.1 请求
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Authorization: Bearer " + apiKey + "\r\n"
                    + "Accept: application/json\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream out = sslSocket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = sslSocket.getInputStream();
            String statusLine = readLine(in);
            if (statusLine != null && statusLine.startsWith("HTTP/")) {
                String[] parts = statusLine.split(" ", 3);
                int code = Integer.parseInt(parts[1]);
                if (code == 200) return new Result(true, null);
                String reason = "HTTP " + code + (code == 401 ? "(API Key 无效或已过期)"
                        : code == 403 ? "(访问被拒绝)" : code == 404 ? "(端点不存在)" : "");
                return new Result(false, reason);
            }
            return new Result(false, "无效响应: " + statusLine);
        } finally {
            try { if (sslSocket != null) sslSocket.close(); } catch (Exception ignored) {}
            try { if (rawSocket != null && !rawSocket.isClosed()) rawSocket.close(); } catch (Exception ignored) {}
        }
    }

    /** 构建一个使用 SSLContext.getDefault() 的全新 SSLSocketFactory（绕开可能的实例损坏）。 */
    private static SSLSocketFactory buildFreshFactory() {
        try {
            SSLContext ctx = SSLContext.getDefault();
            return ctx.getSocketFactory();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[WARN] 无法获取默认 SSLContext: " + e.getMessage());
            return null;
        }
    }

    /** 从 SSLHandshakeException 中提取详细诊断信息。 */
    private static String extractHandshakeDetail(SSLHandshakeException e) {
        StringBuilder sb = new StringBuilder();
        if (e.getMessage() != null) sb.append(e.getMessage());
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append(" [").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) sb.append(": ").append(cause.getMessage());
            sb.append("]");
        }
        return sb.toString();
    }

    /** 判断 IOException 是否与 TLS 握手相关（可继续尝试其他策略）。 */
    private static boolean isHandshakeRelated(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("handshake") || lower.contains("ssl")
                || lower.contains("certificate") || lower.contains("tls")
                || e instanceof SSLHandshakeException;
    }

    /** 对非握手的 IO 错误进行分类。 */
    private static String classifyIoError(String msg) {
        if (msg.contains("timed out") || msg.contains("timeout")) return "连接超时（网络不通或需配置代理）";
        if (msg.contains("Unable to tunnel") || msg.contains("Proxy")) return "代理失败: " + msg;
        if (msg.contains("Network is unreachable") || msg.contains("No route to host")) return "网络不可达";
        if (msg.contains("Connection refused")) return "连接被拒绝（端口不可达或防火墙拦截）";
        return msg;
    }

    /** 从输入流读取一行（以 \r\n 或 \n 结尾），返回不含换行的内容。超时返回 null。 */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(128);
        int b;
        long deadline = System.currentTimeMillis() + 5000; // 5 秒读取超时
        while ((b = in.read()) != -1) {
            if (System.currentTimeMillis() > deadline) break;
            char ch = (char) b;
            if (ch == '\r') continue;
            if (ch == '\n') break;
            sb.append(ch);
            if (sb.length() > 1024) break; // 防止异常长行
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** 私有异常：AI 调用失败时抛出的具体运行时异常（代替泛型 RuntimeException，规避 java:S112）。 */
    private static final class AiCallException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 调用 chat/completions，返回模型文本。Ollama 兼容 /v1/chat/completions。
     *  偶发超时/网络抖动自动重试（含 TLS 握手失败回退到兼容工厂）。 */
    private String callChat(String prompt, double temperature) {
        return callChat(prompt, temperature, null, 0);
    }

    private String callChat(String prompt, double temperature, SSLSocketFactory sf, int attempt) {
        HttpURLConnection c = null;
        try {
            URL u = buildChatUrl();
            c = open(u, cfg, sf);
            configureConnection(c);
            sendRequestBody(c, prompt, temperature);
            return handleResponse(c);
        } catch (IOException e) {
            boolean handshake = isHandshakeFailure(e);
            if (attempt < MAX_RETRIES) {
                // 握手失败时下一轮挂兼容工厂；其余错误（含超时）同参数重试
                SSLSocketFactory nextSf = (handshake && UNIVERSAL_COMPAT_FACTORY != null) ? UNIVERSAL_COMPAT_FACTORY : sf;
                LOG.log(java.util.logging.Level.WARNING,
                        "[AI] 调用失败（第{0}次尝试，{1}），{2}秒后重试：{3}",
                        new Object[]{attempt + 1, e.getClass().getSimpleName(), RETRY_BACKOFF_MS / 1000, e.getMessage()});
                try { Thread.sleep(RETRY_BACKOFF_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return callChat(prompt, temperature, nextSf, attempt + 1);
            }
            throw new AiCallException("AI 调用失败（已重试 " + attempt + " 次）: " + e.getMessage(), e);
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

    /** 按配置构造连接；配置了 HTTPS/HTTP 代理则走 Proxy（FR9.11）。
     *  - 无显式代理时交给默认 ProxySelector：启用系统代理后即为 Windows 系统代理（与 curl/浏览器一致）；
     *    直连环境下选择器返回 NO_PROXY，无副作用。
     *  - 回环/私网地址强制直连（Proxy.NO_PROXY），避免本地 Ollama 被系统代理拦截。
     *  - sf 非 null（TLS 1.2 回退场景）时挂到 HttpsURLConnection，不影响 HTTP。
     *  HTTPS 默认走 JDK 原生 TLS；此前默认包装 SSLSocketFactory 反而在部分 runtime 触发 handshake_failure，
     *  现仅在握手失败后按需回退。 */
    private static HttpURLConnection open(URL u, AiConfig cfg, SSLSocketFactory sf) throws IOException {
        try {
            guardEndpoint(u, cfg);
        } catch (IllegalArgumentException iae) {
            // guardEndpoint 对畸形 URL（host 为空）抛 IllegalArgumentException（RuntimeException），
            // 转成 IOException 以便上层 testConnection / callChat 的 catch(IOException) 统一兜底，给出友好提示
            throw new IOException("URL 非法（疑似畸形 Base URL）: " + iae.getMessage(), iae);
        }
        String host = u.getHost();
        // 回环/私网地址始终强制直连（即便配置了显式代理），避免本地 Ollama 被代理拦截
        if (isLoopbackOrPrivate(host)) {
            return openWithSf(u, Proxy.NO_PROXY, sf);
        }
        String px = (cfg.getHttpsProxy() != null && !cfg.getHttpsProxy().isEmpty())
                ? cfg.getHttpsProxy() : cfg.getHttpProxy();
        if (px != null && !px.isEmpty()) {
            return openWithSf(u, parseProxy(px), sf);
        }
        // 无显式代理且为公网地址：使用默认 ProxySelector（含系统代理）
        return openWithSf(u, null, sf);
    }

    /** 解析代理地址：支持带 scheme（http:// / https://），未带端口时按协议默认（HTTPS→443、HTTP→80）。
     *  端口用 lastIndexOf(':') 解析，兼容 IPv6 字面量 [::1]:port。 */
    private static Proxy parseProxy(String px) throws IOException {
        String spec = px.trim();
        int schemeIdx = spec.indexOf("://");
        boolean https = false;
        if (schemeIdx >= 0) {
            String scheme = spec.substring(0, schemeIdx).toLowerCase();
            https = scheme.equals("https");
            spec = spec.substring(schemeIdx + 3);
        }
        int c = spec.lastIndexOf(':');
        String host;
        int port;
        if (c < 0) {
            host = spec;
            port = https ? 443 : 80;
        } else {
            host = spec.substring(0, c);
            String portStr = spec.substring(c + 1).trim();
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException nfe) {
                throw new IOException("代理端口非法: " + px, nfe);
            }
        }
        if (host.isEmpty()) throw new IOException("代理 host 为空: " + px);
        if (port <= 0 || port > 65535) throw new IOException("代理端口越界: " + port);
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    private static HttpURLConnection openWithSf(URL u, Proxy proxy, SSLSocketFactory sf) throws IOException {
        HttpURLConnection conn = (proxy == null)
                ? (HttpURLConnection) u.openConnection()
                : (HttpURLConnection) u.openConnection(proxy);
        if (sf != null && conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sf);
        }
        return conn;
    }

    /** 回环/私网地址判定（用于绕过系统代理，避免本地 Ollama 被代理拦截）。解析失败则按公网处理。 */
    private static boolean isLoopbackOrPrivate(String host) {
        try {
            InetAddress a = InetAddress.getByName(host);
            return a.isLoopbackAddress() || a.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** 异常链中是否含 TLS 握手失败（用于触发 TLS 1.2 回退）。 */
    private static boolean isHandshakeFailure(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SSLHandshakeException) return true;
            String m = cur.getMessage();
            if (m != null && m.toLowerCase().contains("handshake_failure")) return true;
            cur = cur.getCause();
        }
        return false;
    }
}