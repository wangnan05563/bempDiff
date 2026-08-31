package com.bempdiff.ai;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.server.Json;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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
    /** TLSv1.2 协议名（多处重复，提取为常量规避 S1192）。 */
    private static final String TLS12 = "TLSv1.2";

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

    /** 仅 TLSv1.2 工厂（callChat/testConnection 握手失败兜底，对齐原测试 S1）。保留全量默认密码套件。 */
    private static final SSLSocketFactory TLS12_ONLY_FACTORY;

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
        if (current == null || !current.contains(TLS12)) {
            System.setProperty("https.protocols", TLS12 + ",TLSv1.3");
        }
        // 启用系统代理
        if (!"true".equalsIgnoreCase(System.getProperty("java.net.useSystemProxies"))) {
            System.setProperty("java.net.useSystemProxies", "true");
        }

        // [Layer 2] 构建通用兼容工厂并设为全局默认
        UNIVERSAL_COMPAT_FACTORY = buildSslFactory(new String[]{TLS12, "TLSv1.3"}, true, "通用兼容");
        if (UNIVERSAL_COMPAT_FACTORY != null) {
            try {
                HttpsURLConnection.setDefaultSSLSocketFactory(UNIVERSAL_COMPAT_FACTORY);
                LOG.info("[TLS] 通用兼容 SSLSocketFactory 已设为全局默认");
            } catch (Exception e) {
                LOG.warning("[WARN] 设置默认 SSLSocketFactory 失败: " + e.getMessage());
            }
        } else {
            LOG.warning("[WARN] 通用兼容工厂创建失败，将使用 JDK 默认 TLS 配置");
        }

        // [Layer 3] 仅 TLSv1.2 兜底工厂（callChat/testConnection 握手失败降级，对齐原测试 S1）
        TLS12_ONLY_FACTORY = buildSslFactory(new String[]{TLS12}, false, "仅 TLSv1.2");
        if (TLS12_ONLY_FACTORY != null) {
            LOG.info("[TLS] 仅 TLSv1.2 兜底工厂已构建");
        }
    }

    /**
     * 构建 SSLSocketFactory：包装默认工厂，在每条新连接上强制协议（及可选的弱套件过滤）。
     * 统一实现"通用兼容"与"仅 TLSv1.2"两种策略，避免两套匿名类样板重复。
     * 不替换 TrustManager（保留标准 CA 验证），仅调整协议版本和密码套件列表以提升兼容性。
     */
    private static SSLSocketFactory buildSslFactory(String[] protocols, boolean filterWeak, String warnTag) {
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
                    s.setEnabledProtocols(protocols);
                    if (filterWeak) {
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
                    }
                    return s;
                }
            };
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.warning("[WARN] 无法创建" + warnTag + "工厂: " + e.getMessage());
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
            ProjectContext effCtx = (req.perFileCtx != null) ? req.perFileCtx : ctx;
            boolean effWithCtx = effCtx != null && !effCtx.isEmpty();
            String prompt = PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, effCtx);
            String json = callChat(prompt, 0.1);
            out.add(MockAiAnalyzer.parseFileAnalysisStatic(req.key, json, effWithCtx));
        }
        return out;
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
    }

    /** 成本预估用（聚焦 + 顶层统计口径）：prompt 头部聚合数与折叠树一致。 */
    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg,
                                    ProjectContext ctx, String focus, DiffStats topStats) {
        return topStats == null
                ? buildStageAPrompt(diff, decompiled, cfg, ctx, focus)
                : PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, "", topStats);
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus, String category) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, category);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg, ctx, focus);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
        String json = callChat(prompt, 0.2);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        return MockAiAnalyzer.parseStageAStatic(json, withCtx);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus, String category) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, category);
        String json = callChat(prompt, 0.2);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        StageASummary s = MockAiAnalyzer.parseStageAStatic(json, withCtx);
        if (category != null) {
            s.setCategory(category);
        }
        return s;
    }

    /** 阶段A（权威类别增强 + 顶层统计口径）：头部聚合数与折叠树一致（diff 深读仍覆盖内层）。 */
    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg,
                                ProjectContext ctx, String focus, String category, DiffStats topStats) {
        if (topStats == null) return stageA(diff, decompiled, cfg, ctx, focus, category);
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, category, topStats);
        String json = callChat(prompt, 0.2);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        StageASummary s = MockAiAnalyzer.parseStageAStatic(json, withCtx);
        if (category != null) s.setCategory(category);
        return s;
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx, String focus) {
        List<FileAnalysis> out = new ArrayList<>();
        boolean withCtx = ctx != null && !ctx.isEmpty();
        for (DecompileReq req : candidates) {
            ProjectContext effCtx = (req.perFileCtx != null) ? req.perFileCtx : ctx;
            boolean effWithCtx = effCtx != null && !effCtx.isEmpty();
            String prompt = PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, effCtx, focus);
            String json = callChat(prompt, 0.1);
            out.add(MockAiAnalyzer.parseFileAnalysisStatic(req.key, json, effWithCtx));
        }
        return out;
    }

    @Override
    public boolean testConnection(AiConfig cfg) {
        return testConnectionHttp(cfg);
    }

    /**
     * 连接测试：复用与 {@link #callChat(String, double)} 完全相同的传输层
     * （HttpURLConnection + open() 的代理 / SSRF 防护 + 相同 SSLSocketFactory 阶梯）。
     *
     * <p><b>F1 修复（测试必须与真实调用同通道）</b>：此前测试走原始 SSLSocket、调用走
     * HttpURLConnection，两者 TLS 策略不同，绿灯测试不能保证真实 AI 调用可用。现统一为
     * 同一 HttpURLConnection 通道，测试通过即代表真实调用可用。
     *
     * <p><b>F2 修复（诊断不再误报）</b>：HTTP 层错误（401/403/404 等）立即按鉴权/端点问题
     * 返回，不再被归入"TLS 握手失败"。
     *
     * <p><b>F3 修复（不再手工解析状态行）</b>：改用 HttpURLConnection.getResponseCode()，
     * 避免畸形状态行导致未捕获的 RuntimeException 崩溃。
     *
     * <p>TLS 策略阶梯与 callChat 对齐：默认（全局兼容工厂 1.2+1.3）→ 兼容工厂（显式）
     * → 仅 TLSv1.2（对齐原测试 S1：部分中间设备/网关拒绝 1.3 广告）。
     */
    private boolean testConnectionHttp(AiConfig cfg) {
        lastError = null;
        String endpoint = cfg.getBaseUrl().endsWith("/")
                ? cfg.getBaseUrl() + "models"
                : cfg.getBaseUrl() + "/models";
        SSLSocketFactory[] ladder = buildTestLadder();
        StringBuilder diag = new StringBuilder();
        for (int i = 0; i < ladder.length; i++) {
            ProbeResult r = probeTestStrategy(endpoint, cfg, ladder[i], i, diag);
            if (r == ProbeResult.SUCCESS) return true;
            if (r == ProbeResult.FAILED) return false;
        }
        lastError = "TLS 握手失败（已尝试 " + ladder.length + " 种策略均被拒绝）: " + diag
                + "建议：填写 HTTPS 代理（如 http://127.0.0.1:7890）或检查防火墙/中间设备设置";
        return false;
    }

    /** OpenAI 兼容模型列表接口路径（GET {baseUrl}/models）。 */
    private static final String MODELS_PATH = "models";

    /**
     * 按 API Base URL + API Key 自动获取可用模型列表（OpenAI 兼容 /models 接口）。
     * 复用与 testConnection 完全相同的传输层（open 的代理 / SSRF 防护 + TLS 阶梯 + 鉴权头）。
     * 返回模型 ID 列表；任意策略成功但解析为空、或返回非 2xx 鉴权/端点错误时返回空列表（lastError 含原因）。
     */
    public List<String> fetchModels(AiConfig cfg) {
        return fetchModelsHttp(cfg);
    }

    private List<String> fetchModelsHttp(AiConfig cfg) {
        lastError = null;
        String endpoint = cfg.getBaseUrl().endsWith("/")
                ? cfg.getBaseUrl() + MODELS_PATH
                : cfg.getBaseUrl() + "/" + MODELS_PATH;
        SSLSocketFactory[] ladder = buildTestLadder();
        StringBuilder diag = new StringBuilder();
        for (int i = 0; i < ladder.length; i++) {
            FetchOutcome r = probeFetchStrategy(endpoint, cfg, ladder[i]);
            if (r.kind == FetchOutcome.Kind.SUCCESS) {
                List<String> models = parseModels(r.body);
                if (!models.isEmpty()) return models;
                // 接口返回成功但未能解析到模型（非标准响应体）：仍按"成功但为空"处理，给出可读提示。
                lastError = "接口返回成功，但响应格式未包含 data[].id 模型列表（可能非 OpenAI 兼容 /models 格式）";
                return models;
            }
            if (r.kind == FetchOutcome.Kind.AUTH_FAIL) {
                lastError = r.error;
                return Collections.emptyList();
            }
            // 握手失败 → 继续下一 TLS 策略
            diag.append("策略").append(i).append(":握手失败; ");
        }
        lastError = "TLS 握手失败（已尝试 " + ladder.length + " 种策略均被拒绝）: " + diag
                + "建议：填写 HTTPS 代理或检查防火墙/中间设备设置";
        return Collections.emptyList();
    }

    /** 单次 GET /models 的探测结果（含响应体或错误原因），用于 fetchModels 的 TLS 阶梯。 */
    private static final class FetchOutcome {
        enum Kind { SUCCESS, AUTH_FAIL, CONTINUE }
        final Kind kind;
        final String body;   // SUCCESS 时非空
        final String error;  // AUTH_FAIL/CONTINUE 诊断
        FetchOutcome(Kind kind, String body, String error) {
            this.kind = kind;
            this.body = body;
            this.error = error;
        }
    }

    /** 单次 GET /models：成功返回响应体；鉴权/端点错误返回 AUTH_FAIL；TLS 握手失败返回 CONTINUE 触发重试。 */
    private FetchOutcome probeFetchStrategy(String endpoint, AiConfig cfg, SSLSocketFactory sf) {
        try {
            URL u = URI.create(endpoint).toURL();
            HttpURLConnection c = open(u, cfg, sf);
            try {
                c.setRequestMethod("GET");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                setAuthHeader(c, cfg);
                int code = c.getResponseCode();
                if (code >= 200 && code < 300) {
                    String body = readAll(c.getInputStream());
                    return new FetchOutcome(FetchOutcome.Kind.SUCCESS, body, null);
                }
                if (code == 401) return new FetchOutcome(FetchOutcome.Kind.AUTH_FAIL, null, "HTTP 401（API Key 无效或已过期）");
                if (code == 403) return new FetchOutcome(FetchOutcome.Kind.AUTH_FAIL, null, "HTTP 403（访问被拒绝，检查账户权限）");
                if (code == 404) return new FetchOutcome(FetchOutcome.Kind.AUTH_FAIL, null, "HTTP 404（/models 端点不存在，检查 Base URL，部分厂商模型列表接口路径不同）");
                return new FetchOutcome(FetchOutcome.Kind.AUTH_FAIL, null, "HTTP " + code + "（服务端返回非成功状态）");
            } finally {
                c.disconnect();
            }
        } catch (IOException e) {
            if (isHandshakeFailure(e)) {
                return new FetchOutcome(FetchOutcome.Kind.CONTINUE, null, null);
            }
            String msg = classifyIoError(e.getMessage());
            lastError = msg;
            return new FetchOutcome(FetchOutcome.Kind.AUTH_FAIL, null, msg);
        }
    }

    /** 从 /models 响应解析模型 ID 列表：优先 data[].id（OpenAI 兼容），兜底 models[].name（Ollama /api/tags 风格）。 */
    private static List<String> parseModels(String body) {
        List<String> out = new ArrayList<>();
        if (body == null || body.isEmpty()) return out;
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map)) return out;
            Map<String, Object> root = (Map<String, Object>) parsed;
            Object arr = root.get("data");
            if (!(arr instanceof List)) arr = root.get("models"); // Ollama 兜底
            if (!(arr instanceof List)) return out;
            for (Object item : (List<?>) arr) {
                if (item instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) item;
                    Object id = m.get("id");
                    if (id == null) id = m.get("name"); // Ollama 用 name
                    if (id != null) {
                        String s = String.valueOf(id);
                        if (!s.isEmpty() && !out.contains(s)) out.add(s);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "解析模型列表失败", e);
        }
        return out;
    }

    /**
     * 用单个 TLS 策略发起一次连接探活。返回 true 表示需继续尝试下一策略；false 表示已成功或已确定失败应中止。
     * 提取自 testConnectionHttp，用于降低其认知复杂度。
     */
    private enum ProbeResult { SUCCESS, FAILED, CONTINUE }

    private ProbeResult probeTestStrategy(String endpoint, AiConfig cfg, SSLSocketFactory sf, int i, StringBuilder diag) {
        try {
            int code = doProbe(endpoint, cfg, sf);
            if (code >= 200 && code < 300) {
                lastError = null;
                LOG.log(java.util.logging.Level.INFO, "[TLS-测试] 策略{0} 成功 (HTTP {1})", new Object[]{i, code});
                return ProbeResult.SUCCESS;
            }
            // HTTP 层错误：区分鉴权/端点，直接返回（不换 TLS 策略，也不误报 TLS）
            if (code == 401) { lastError = "HTTP 401（API Key 无效或已过期）"; return ProbeResult.FAILED; }
            if (code == 403) { lastError = "HTTP 403（访问被拒绝，检查账户权限）"; return ProbeResult.FAILED; }
            if (code == 404) { lastError = "HTTP 404（端点不存在，检查 Base URL）"; return ProbeResult.FAILED; }
            lastError = "HTTP " + code + "（服务端返回非成功状态）";
            diag.append("策略").append(i).append(":HTTP ").append(code).append("; ");
            return ProbeResult.FAILED;
        } catch (IOException e) {
            boolean hs = isHandshakeFailure(e);
            diag.append("策略").append(i).append(":").append(e.getMessage()).append("; ");
            if (!hs) {
                // 网络层错误（超时/拒绝/不可达）不换 TLS 策略，给出分类提示
                lastError = classifyIoError(e.getMessage());
                return ProbeResult.FAILED;
            }
            // 握手失败 → 继续下一 TLS 策略
            LOG.log(java.util.logging.Level.INFO, "[TLS-测试] 策略{0} 握手失败: {1}", new Object[]{i, e.getMessage()});
            return ProbeResult.CONTINUE;
        }
    }


    /** 执行一次 GET 探活，返回 HTTP 响应码；复用 open() 的代理 / SSRF 防护与工厂注入。 */
    private int doProbe(String endpoint, AiConfig cfg, SSLSocketFactory sf) throws IOException {
        URL u = URI.create(endpoint).toURL();
        HttpURLConnection c = open(u, cfg, sf);
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            setAuthHeader(c, cfg);
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    /** 与 callChat 对齐的 TLS 工厂阶梯：默认（兼容 1.2+1.3）→ 兼容（显式）→ 仅 TLSv1.2。 */
    private static SSLSocketFactory[] buildTestLadder() {
        List<SSLSocketFactory> list = new ArrayList<>();
        list.add(null); // 默认（静态块已设为全局兼容工厂）
        if (UNIVERSAL_COMPAT_FACTORY != null) list.add(UNIVERSAL_COMPAT_FACTORY);
        if (TLS12_ONLY_FACTORY != null) list.add(TLS12_ONLY_FACTORY);
        return list.toArray(new SSLSocketFactory[0]);
    }

    /** 对非握手的 IO 错误进行分类（供 UI 诊断）。 */
    private static String classifyIoError(String msg) {
        if (msg == null) return "未知网络错误";
        if (msg.contains("timed out") || msg.contains("timeout")) return "连接超时（网络不通或需配置代理）";
        if (msg.contains("Unable to tunnel") || msg.contains("Proxy")) return "代理失败: " + msg;
        if (msg.contains("Network is unreachable") || msg.contains("No route to host")) return "网络不可达（检查网络连接或代理设置）";
        if (msg.contains("Connection refused")) return "连接被拒绝（端口不可达或防火墙拦截）";
        return msg;
    }

    /** 私有异常：AI 调用失败时抛出的具体运行时异常（代替泛型 RuntimeException，规避 java:S112）。 */
    private static final class AiCallException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        AiCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * HTTP 非 2xx 响应异常：携带状态码，供重试策略区分「可重试」与「永久性失败」。
     *
     * <p>背景：原实现所有 IOException 一律重试 2 次，而 HTTP 400（如 prompt 超出模型上下文窗口）
     * 属永久性客户端错误，重试必然同样失败，却要把数十万字符的请求体重发 3 次，既慢又浪费配额。
     */
    private static final class HttpStatusException extends IOException {
        private static final long serialVersionUID = 1L;
        final int statusCode;
        HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    /**
     * 判定失败是否值得重试：
     * 网络 / IO / TLS 握手错误、5xx 服务端错误、408 请求超时、429 限流 → 可重试；
     * 其余 4xx（400 参数或上下文超限、401 鉴权、403 权限、404 端点、422 语义错误）→ 永久性失败，不重试。
     */
    private static boolean isRetryable(IOException e) {
        if (e instanceof HttpStatusException) {
            int code = ((HttpStatusException) e).statusCode;
            if (code == 408 || code == 429) return true;
            return code >= 500;
        }
        return true; // 网络层 / TLS 错误：可重试
    }

    /**
     * 把供应商原始错误翻译为可操作提示。
     * token / 上下文窗口超限是最常见的永久性失败，需明确告知缩小范围的方法，
     * 否则用户面对裸 HTTP 400 无从下手。
     */
    private static String friendlyMessage(IOException e) {
        String raw = e.getMessage();
        if (raw == null) return String.valueOf(e);
        boolean tokenLimit = raw.contains("max_prompt_tokens") || raw.contains("context_length_exceeded")
                || raw.contains("context length") || raw.contains("maximum context")
                || raw.contains("too many tokens") || raw.contains("exceeded max");
        if (tokenLimit) {
            return raw + "；处置建议：本次分析范围超出模型上下文窗口，请缩小范围后重试——"
                    + "减少参与分析的变更文件数（配置中心降低「阶段B 深读文件数」/ 关闭无关目录），"
                    + "或改用差异树右键「AI功能总结」对单个文件分批深读。";
        }
        return raw;
    }

    /**
     * 调用 chat/completions，返回模型文本。Ollama 兼容 /v1/chat/completions。
     * 偶发超时/网络抖动自动重试（含 TLS 握手失败回退到兼容工厂）。
     *
     * <p><b>发送前上下文窗口护栏</b>：预估 token 超 {@code cfg.maxPromptTokens} 时直接取消，
     * 不再发起注定被模型拒收的请求。成本闸门只做费用确认（用户确认后照发），
     * 拦不住「超出模型上下文窗口」这一维度，故需此兜底。
     */
    private String callChat(String prompt, double temperature) {
        int maxTokens = cfg.getMaxPromptTokens();
        if (maxTokens > 0) {
            double est = estimateTokens(prompt);
            if (est > maxTokens) {
                throw new AiCallException("AI 调用已取消：本次请求预估约 " + (long) est
                        + " tokens，超过单次请求上限 " + maxTokens + "（模型上下文窗口护栏）。"
                        + "请缩小分析范围后重试：减少参与分析的变更文件数，"
                        + "或改用差异树右键「AI功能总结」对单个文件分批深读。", null);
            }
        }
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
            return handleCallFailure(prompt, temperature, sf, attempt, e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** callChat 的失败处理：握手失败时降级 TLS 策略并重试，重试耗尽则抛出 AiCallException（用于降低 callChat 认知复杂度）。 */
    private String handleCallFailure(String prompt, double temperature, SSLSocketFactory sf, int attempt, IOException e) {
        // 永久性客户端错误（400/401/403/404/422…）重试必然同样失败，且超大 prompt 重传代价高昂
        // （实测：token 超限 400 会把数十万字符请求体重发 3 次），故直接抛出不再重试。
        if (!isRetryable(e) || attempt >= MAX_RETRIES) {
            throw new AiCallException("AI 调用失败"
                    + (attempt > 0 ? "（已重试 " + attempt + " 次）" : "")
                    + ": " + friendlyMessage(e), e);
        }
        SSLSocketFactory nextSf = sf;
        if (isHandshakeFailure(e)) {
            nextSf = resolveFallbackFactory(sf);
            LOG.log(java.util.logging.Level.WARNING,
                    "[AI] 调用失败（第{0}次尝试，{1}，TLS={2}），{3}秒后重试：{4}",
                    new Object[]{attempt + 1, e.getClass().getSimpleName(),
                            nextSf == TLS12_ONLY_FACTORY ? "仅TLSv1.2" : "兼容(1.2+1.3)",
                            RETRY_BACKOFF_MS / 1000, e.getMessage()});
        } else {
            LOG.log(java.util.logging.Level.WARNING,
                    "[AI] 调用失败（第{0}次尝试，{1}），{2}秒后重试：{3}",
                    new Object[]{attempt + 1, e.getClass().getSimpleName(), // NOSONAR(S2259) - e 为 catch 捕获的非空异常
                            RETRY_BACKOFF_MS / 1000, e.getMessage()});
        }
        sleepBackoff();
        return callChat(prompt, temperature, nextSf, attempt + 1);
    }

    /** 握手失败时选择的回退工厂：仅 TLSv1.2 优先，其次通用兼容，最后原工厂。 */
    private static SSLSocketFactory resolveFallbackFactory(SSLSocketFactory sf) {
        if (TLS12_ONLY_FACTORY != null) {
            return TLS12_ONLY_FACTORY;
        }
        if (UNIVERSAL_COMPAT_FACTORY != null) {
            return UNIVERSAL_COMPAT_FACTORY;
        }
        return sf;
    }

    /** 重试前的短暂休眠；被中断时恢复中断标志并继续。 */
    private static void sleepBackoff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
            throw new HttpStatusException(code, "HTTP " + code + ": " + resp);
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
            // 畸形 URL（host 为空）会被 guardEndpoint 判定为非法参数，这里统一包装为 IOException 交由上层兜底处理
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