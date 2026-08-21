package com.bempdiff.server;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectContextAnalyzer;
import com.bempdiff.ai.context.ProjectContextCache;
import com.bempdiff.ai.context.ProjectIndex;
import com.bempdiff.ai.context.ProjectContextService;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.diff.LibJarDiff;
import com.bempdiff.diff.ArchiveTree;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.fs.FileOps;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.FolderParser;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.report.MarkdownReport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web UI 后端：内嵌 HTTP 服务（JDK 自带 com.sun.net.httpserver，零新增依赖）。
 * 绑定 127.0.0.1，同源托管静态 SPA；API 端点复用现有核心引擎（解析/差异/反编译/报告/导出/AI），
 * 不引入任何第三方 Web / JSON 框架。
 */
public final class BempServer {
    private static final Logger LOG = Logger.getLogger(BempServer.class.getName());

    // 重复字符串字面量常量：同一字符串在多处复用，集中定义避免重复。
    private static final String HDR_CONTENT_TYPE = "Content-Type";
    private static final String KEY_ERROR = "error";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_JOB_ID = "jobId";
    private static final String M_OPTIONS = "OPTIONS";
    private static final String MODE_FOLDER = "folder";
    private static final String STAGE_PARSING = "parsing";
    private static final String MSG_NOT_DONE = "比对尚未完成: ";
    private static final String DEFAULT_INDEX = "/index.html";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_PROJECT_DIR = "projectDir";
    private static final String PROP_USER_HOME = "user.home";
    private static final String DIR_BEMPDIFF = ".bempdiff";
    private static final String DIR_AI_REPLAY = "ai_replay";
    private static final String STAGE_THINKING = "thinking";
    private static final String AI_MODEL_FIELD = "model";
    private static final String RISK_MEDIUM = "MEDIUM";

    private final JobStore store = new JobStore();
    private final ServerConfig config;
    private final AtomicLong seq = new AtomicLong(System.nanoTime() % 1_000_000);
    private final Path webroot;
    // 比对任务后台执行器（虚拟线程：每个比对一个轻量线程，互不阻塞；HTTP 处理函数仅负责提交与返回）。
    // 并发比对上限：避免用户连续提交大包时多个解析任务同时跑爆文件句柄/内存（虚拟线程本身无界）。
    private static final int MAX_CONCURRENT_COMPARES =
            Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final Semaphore compareSem = new Semaphore(MAX_CONCURRENT_COMPARES);
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    // 成本闸门：AI 入口 token 预估缓存（per-job），避免重复反编译；DONE 后 diff 不可变，可安全复用。
    private final Map<String, Map<String, Object>> aiEstimateCache = new ConcurrentHashMap<>();
    // per-job 的 AI 工作缓存（反编译类图 + 前端文本差异 + 依赖 jar 分析）。
    // report / ai-analyze / export 三入口共用，避免并行发起多个分析类别时 N 倍重复反编译（评审 P1 #8）。
    private final Map<String, AiWorkCache> aiWorkCache = new ConcurrentHashMap<>();
    private static final int AI_WORK_CACHE_MAX = 16; // 粗粒度上限：超限清空，避免 job 长期驻留占用内存

    public BempServer(ServerConfig config, Path webroot) {
        this.config = config;
        this.webroot = webroot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/session/compare", this::handleCompare);
        server.createContext("/api/job", this::handleJob);
        server.createContext("/api/entry", this::handleEntry);
        server.createContext("/api/file", this::handleFile);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/ai/test", this::handleAiTest);
        server.createContext("/api/ai/models", this::handleAiModels);
        server.createContext("/api/ai/context", this::handleAiContext);
        server.createContext("/", this::handleStatic);
        // 关键：默认 HttpServer 用单线程串行处理所有请求——一次长比对会阻塞全部 API/静态资源。
        // 改为每个请求一个虚拟线程，比对任务再下沉到 workers 执行器，UI 才能边比对边轮询进度。
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        LOG.info(() -> "[Web] BempDiff HTTP 服务已启动：http://127.0.0.1:" + port + "  (仅本机)");
        System.out.println("BempDiff Web 服务已启动: http://127.0.0.1:" + port); // NOSONAR - CLI 需在控制台可见
        // 阻塞主线程（CLI 子命令特性）
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------- 工具 ----------------

    private static String readBody(HttpExchange ex) throws IOException {
        byte[] b = readAll(ex.getRequestBody());
        return new String(b, StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", HDR_CONTENT_TYPE);
    }

    private static void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        addCors(ex);
        byte[] b = Json.write(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add(HDR_CONTENT_TYPE, "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void sendText(HttpExchange ex, int code, String mime, String text) throws IOException {
        addCors(ex);
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add(HDR_CONTENT_TYPE, mime + "; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void sendBytes(HttpExchange ex, int code, String mime, byte[] data) throws IOException {
        addCors(ex);
        ex.getResponseHeaders().add(HDR_CONTENT_TYPE, mime);
        ex.getResponseHeaders().add("Content-Disposition", "attachment");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    // 静态文件内联返回（不挂 Content-Disposition: attachment，否则浏览器会下载而非渲染）。
    private static void sendBytesInline(HttpExchange ex, int code, String mime, byte[] data) throws IOException {
        addCors(ex);
        ex.getResponseHeaders().add(HDR_CONTENT_TYPE, mime);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_ERROR, msg);
        sendJson(ex, code, m);
    }

    private static String queryParam(URI uri, String name) {
        String q = uri.getQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            if (pair.substring(0, idx).equals(name)) {
                try {
                    return java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                } catch (Exception e) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return null;
    }

    private static String findJava() {
        String jh = System.getenv("JAVA_HOME");
        if (jh != null) {
            Path cand = Paths.get(jh, "bin", "java.exe");
            if (cand.toFile().isFile()) return cand.toString();
            Path cand2 = Paths.get(jh, "bin", "java");
            if (cand2.toFile().isFile()) return cand2.toString();
        }
        return "java";
    }

    private Path cfrPath(CompareOptions opts) {
        if (opts.getCfrJar() == null || opts.getCfrJar().isEmpty()) return null;
        return Paths.get(opts.getCfrJar());
    }

    // ---------------- 比对 ----------------

    private void handleCompare(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        try {
            Map<String, Object> req = Json.parseObject(readBody(ex));
            String leftType = Json.str(req, "leftType", "package");
            String leftPath = Json.str(req, "leftPath", null);
            String rightPath = Json.str(req, "rightPath", null);
            if (leftPath == null || rightPath == null) {
                sendError(ex, 400, "缺少 leftPath / rightPath");
                return;
            }
            CompareOptions opts = CompareOptions.fromRequest(req);
            ParseConfig pc = opts.toParseConfig();
            final String mode = MODE_FOLDER.equals(leftType) ? MODE_FOLDER : "package";

            // 智能识别：同名不同版本的压缩包 → 自动按版本升序（旧→新），无需手动分辨新旧。
            boolean autoOrdered = false;
            String oldVersion = null;
            String newVersion = null;
            if (!MODE_FOLDER.equals(mode)) {
                if (com.bempdiff.parse.PackageVersion.sameBaseDifferentVersion(leftPath, rightPath)) {
                    String[] ordered = com.bempdiff.parse.PackageVersion.orderOldNew(leftPath, rightPath);
                    leftPath = ordered[0];
                    rightPath = ordered[1];
                    oldVersion = com.bempdiff.parse.PackageVersion.extractFromFileName(leftPath);
                    newVersion = com.bempdiff.parse.PackageVersion.extractFromFileName(rightPath);
                    autoOrdered = true;
                }
            }

            String jobId = "job-" + seq.incrementAndGet();
            Job job = new Job(jobId, mode, opts);
            store.put(job);

            // 后台异步执行（解析 + 差异计算可能很慢）：提交即返回 jobId，前端轮询 /api/job/{id}/status 取进度/结果。
            final String fLeftPath = leftPath;
            final String fRightPath = rightPath;
            workers.submit(() -> runCompareTask(job, leftType, fLeftPath, fRightPath, pc, opts));

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put(KEY_JOB_ID, jobId);
            resp.put("mode", mode);
            resp.put(KEY_STATUS, job.getStatus());
            resp.put("progress", job.getProgress());
            resp.put(KEY_PHASE, job.getPhase());
            resp.put(KEY_MESSAGE, job.getMessage());
            // 智能识别结果：供前端提示「已按版本自动排序 旧 vX → 新 vY」
            resp.put("autoOrdered", autoOrdered);
            resp.put("oldVersion", oldVersion);
            resp.put("newVersion", newVersion);
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "比对提交失败", e);
            sendError(ex, 500, "比对提交失败: " + e.getMessage());
        }
    }

    /** 后台比对任务：解析 → 差异计算 → 构建统计，按阶段推进进度；阶段边界检查取消请求。 */
    private void runCompareTask(Job job, String leftType, String leftPath, String rightPath,
                                ParseConfig pc, CompareOptions opts) {
        try {
            compareSem.acquire();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            job.fail("比对启动被中断");
            return;
        }
        try {
            // 等待并发许可期间可能已被取消（仍 QUEUED），拿到许可后立即再判一次，避免空跑一整轮解析。
            if (job.isCancelRequested()) { job.markCancelled(); return; }
            job.markRunning(STAGE_PARSING, "解析包（旧）…", 10);
            PackageSnapshot oldSnap;
            PackageSnapshot newSnap;
            if (MODE_FOLDER.equals(leftType)) {
                FolderParser fp = new FolderParser();
                oldSnap = fp.parse(Paths.get(leftPath), pc);
                job.markRunning(STAGE_PARSING, "解析包（新）…", 30);
                newSnap = fp.parse(Paths.get(rightPath), pc);
            } else {
                PackageParser pp = new PackageParser();
                oldSnap = pp.parse(Paths.get(leftPath), pc, opts.isExpandAll());
                job.markRunning(STAGE_PARSING, "解析包（新）…", 30);
                newSnap = pp.parse(Paths.get(rightPath), pc, opts.isExpandAll());
            }
            job.markRunning(STAGE_PARSING, "解析完成", 40);
            if (job.isCancelRequested()) { job.markCancelled(); return; }

            job.markRunning("diffing", "计算差异…", 50);
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(oldSnap, newSnap);
            job.markRunning("diffing", "计算差异…", 70);
            DiffStats s = engine.stats(r);
            if (job.isCancelRequested()) { job.markCancelled(); return; }

            job.markRunning("building", "构建差异树…", 90);
            job.complete(oldSnap, newSnap, r, s);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "比对失败: " + job.id);
            job.fail("比对失败: " + e.getMessage());
        } finally {
            compareSem.release();
        }
    }

    private Map<String, Object> statsJson(DiffStats s, PackageSnapshot snap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", s.getAdded());
        m.put("deleted", s.getDeleted());
        m.put("modified", s.getModified());
        m.put("unchanged", s.getUnchanged());
        m.put("bizChanged", s.getBizChanged());
        m.put("jarChanged", s.getJarChanged());
        m.put("total", snap.getTotal());
        return m;
    }

    private List<Object> buildTree(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        List<Object> tree = new ArrayList<>();
        DiffStatus[] order = {DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.UNCHANGED};
        for (DiffStatus st : order) {
            for (String k : r.get(st)) {
                tree.add(buildNode(k, oldSnap, newSnap, st));
            }
        }
        return tree;
    }

    private static Map<String, Object> buildNode(String k, PackageSnapshot oldSnap, PackageSnapshot newSnap, DiffStatus st) {
        LogicalEntry e = oldSnap.getEntries().get(k);
        if (e == null) e = newSnap.getEntries().get(k);
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("key", k);
        node.put(KEY_STATUS, st.name());
        node.put("layer", (e != null && e.getLayer() != null) ? e.getLayer().name() : "?");
        node.put("fileClass", (e != null && e.getFileClass() != null) ? e.getFileClass().name() : "OTHER");
        node.put("size", e != null ? e.getSize() : 0);
        return node;
    }

    // ---------------- Job：状态 / 报告 / 导出 ----------------

    private void handleJob(HttpExchange ex) throws IOException {
        // 请求路径：/api/job/ 后接 jobId 与动作（如 status / cancel / report / export）。
        String path = ex.getRequestURI().getPath();
        String sub = path.replaceFirst("^/api/job/?", "");
        String[] parts = sub.split("/");
        if (parts.length < 1 || parts[0].isEmpty()) {
            sendError(ex, 400, "缺少 jobId");
            return;
        }
        String jobId = parts[0];
        String action = parts.length >= 2 ? parts[1] : KEY_STATUS;
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        if (KEY_STATUS.equals(action)) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put(KEY_JOB_ID, job.id);
            resp.put("mode", job.mode);
            resp.put(KEY_STATUS, job.getStatus());
            resp.put(KEY_PHASE, job.getPhase());
            resp.put("progress", job.getProgress());
            resp.put(KEY_MESSAGE, job.getMessage());
            resp.put(KEY_ERROR, job.getError());
            // 仅完成时回带完整产物；进行中只返回进度，避免前端在结果未就绪时读到 null 快照。
            if ("DONE".equals(job.getStatus())) {
                resp.put("oldFile", job.getOldSnap().getFile().getFileName().toString());
                resp.put("newFile", job.getNewSnap().getFile().getFileName().toString());
                resp.put("oldVersion", nullToNA(job.getOldSnap().getVersion()));
                resp.put("newVersion", nullToNA(job.getNewSnap().getVersion()));
                resp.put("stats", statsJson(job.getStats(), job.getOldSnap()));
                resp.put("tree", buildTree(job.getResult(), job.getOldSnap(), job.getNewSnap()));
            }
            sendJson(ex, 200, resp);
            return;
        }
        if ("cancel".equals(action)) {
            job.requestCancel(); // 置取消标记；后台任务在阶段边界检查并标记 CANCELLED
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put(KEY_JOB_ID, job.id);
            resp.put(KEY_STATUS, job.getStatus());
            resp.put("cancelled", job.isCancelRequested());
            sendJson(ex, 200, resp);
            return;
        }
        if ("report".equals(action)) {
            handleReport(ex, job);
            return;
        }
        if ("export".equals(action)) {
            handleExport(ex, job);
            return;
        }
        if ("ai-analyze".equals(action)) {
            handleAiAnalyze(ex, job);
            return;
        }
        if ("ai-classify".equals(action)) {
            handleClassify(ex, job);
            return;
        }
        if ("ai-estimate".equals(action)) {
            handleAiEstimate(ex, job);
            return;
        }
        sendError(ex, 404, "未知 job 操作: " + action);
    }

    private void handleReport(HttpExchange ex, Job job) throws IOException {
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            boolean ai = false;
            String projDir = null;
            String category = null;   // 分析项（risk/breaking/impact/testpoints/custom）——修复：此前缺失，
            String prompt = null;     // 导致「生成报告(AI)」永远走默认分析，不同分析项报告内容相同
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                if (!body.isEmpty()) {
                    Map<String, Object> req = Json.parseObject(body);
                    ai = Json.bool(req, "ai", false);
                    projDir = Json.str(req, KEY_PROJECT_DIR, null);
                    category = Json.str(req, "category", null);
                    prompt = Json.str(req, "prompt", null);
                }
            }
            // 前端未显式传 projectDir 时，回退到配置中心的项目上下文设置（旧前端/漏传均生效）
            if (projDir == null && config.isProjectContextEnabled()) projDir = config.getProjectContextDir();
            // 复用 per-job 工作缓存（反编译/文本差异/jar 分析），与 ai-analyze/export 共享，避免重复计算
            AiWorkCache work = getAiWork(job);
            MarkdownReport rep = new MarkdownReport(job.opts.getTopK());
            String md = buildMarkdown(job, ai, projDir, category, prompt,
                    work.decompiled, work.text, work.libJar, rep);
            sendText(ex, 200, "text/markdown", md);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "报告生成失败", e);
            sendError(ex, 500, "报告生成失败: " + e.getMessage());
        }
    }

    private String buildMarkdown(Job job, boolean ai, String projDir, String category, String prompt,
                                 Map<String, DecompiledUnit> decompiled, Map<String, DecompiledUnit> text,
                                 LibJarDiff.Result libJar, MarkdownReport rep) {
        if (!ai) {
            return rep.render(job.getOldSnap(), job.getNewSnap(), job.getResult(), job.getStats(),
                    decompiled, text, libJar);
        }
        // AI 分支复用 runAiAnalysis，保证「报告」与「流式分析」产出完全一致（同一管线）。
        // category/prompt 透传：buildFocus 据此追加「本次分析聚焦」，使不同分析项的报告内容可区分。
        return runAiAnalysis(job, decompiled, text, libJar, projDir, category, prompt).markdown;
    }

    /**
     * 运行 AI 两阶段分析并渲染 Markdown；同时派生可视化「思考过程」步骤序列。
     * 供 report --ai 与 SSE 流式分析共用，确保两种呈现方式的内容一致。
     */
    private AiAnalysisResult runAiAnalysis(Job job, Map<String, DecompiledUnit> decompiled,
                                           Map<String, DecompiledUnit> text, LibJarDiff.Result libJar,
                                           String projDir, String category, String prompt) {
        AiConfig aiCfg = config.toAiConfig();
        // 项目级上下文（递归识别多项目 + 缓存）：stageA 注入仓库级上下文视图；
        // stageB 逐文件按所属项目精准注入（ProjectIndex.locate）。
        ProjectIndex pIndex = ProjectContextService.resolve(projDir);
        ProjectContext ctx = ProjectContextService.renderContextView(pIndex);
        // 真实调用判定：AI 开关开启 且 已配置 API Key → HttpAiAnalyzer；否则离线回放（Mock）。
        // （原实现只看 Key，若 persistApiKey=false 重启后 Key 丢失会静默降级 Mock，用户难以察觉原因。）
        boolean realLlm = aiCfg.isEnabled() && aiCfg.getApiKey() != null && !aiCfg.getApiKey().isEmpty();
        AiAnalyzer analyzer = realLlm
                ? new HttpAiAnalyzer(aiCfg) : new MockAiAnalyzer(
                Paths.get(System.getProperty(PROP_USER_HOME), DIR_BEMPDIFF, DIR_AI_REPLAY));
        Map<String, DecompiledUnit> aiMap = new LinkedHashMap<>(decompiled);
        aiMap.putAll(text);
        List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
        for (Map.Entry<String, DecompiledUnit> en : aiMap.entrySet()) {
            ProjectContext perCtx = (pIndex != null) ? pIndex.locate(en.getKey()) : null;
            bCands.add(new AiAnalyzer.DecompileReq(en.getKey(), en.getValue(),
                    fileClassOfKey(en.getKey(), job.getOldSnap(), job.getNewSnap()), perCtx));
        }
        if (bCands.size() > aiCfg.getStageBTopK()) bCands = bCands.subList(0, aiCfg.getStageBTopK());
        String focus = buildFocus(category, prompt);
        StageASummary summary = analyzer.stageA(job.getResult(), aiMap, aiCfg, ctx, focus);
        List<FileAnalysis> b = analyzer.stageB(bCands, aiCfg, ctx, focus);
        MarkdownReport rep = new MarkdownReport(job.opts.getTopK());
        // 报告 AI 章节标注本次分析聚焦项，让「所选分析项」在报告内容中显式可见
        rep.setAiFocus(categoryLabel(category, prompt));
        String md = rep.render(job.getOldSnap(), job.getNewSnap(), job.getResult(), job.getStats(),
                decompiled, text, summary, b, ctx, libJar);
        List<Map<String, Object>> thinking = buildThinkingSteps(job, summary, b, ctx);
        return new AiAnalysisResult(md, thinking);
    }

    /** 分析项可读标签：用于报告「分析聚焦」标注。custom 或未知类别返回自定义问题/默认整体分析。 */
    private static String categoryLabel(String category, String prompt) {
        if (prompt != null && !prompt.trim().isEmpty()) {
            return "自定义问题：" + prompt.trim();
        }
        if (category == null || category.isEmpty()) return "整体风险分析";
        switch (category) {
            case "risk": return "整体风险分析";
            case "breaking": return "破坏性变更专项";
            case "impact": return "影响范围分析";
            case "testpoints": return "测试要点分析";
            case "custom": return "自定义问题";
            default: return "整体风险分析";
        }
    }

    /** 由类别/自定义 prompt 构造聚焦指令（非空时引导模型在对应维度深入，使不同类别分析报告内容可区分）。 */
    private static String buildFocus(String category, String prompt) {
        if (prompt != null && !prompt.trim().isEmpty()) {
            return "请围绕以下用户问题进行分析：" + prompt.trim();
        }
        if (category == null || category.isEmpty()) return null;
        switch (category) {
            case "risk": return "本次分析请重点聚焦【整体风险等级与降级/回滚预案】，给出明确的风险结论与应对建议。";
            case "breaking": return "本次分析请重点聚焦【破坏性变更与向后兼容性】，逐一指出删除/签名变更/接口契约破坏等不兼容点。";
            case "impact": return "本次分析请重点聚焦【影响范围与上下游模块依赖】，说明本次变更会波及哪些对外接口与内部调用方。";
            case "testpoints": return "本次分析请重点聚焦【回归测试要点】，给出可执行的测试场景、用例思路与验证重点。";
            default: return null;
        }
    }

    /** 从阶段A/阶段B 结果派生「思考过程」步骤（前端折叠、浅灰展示）。 */
    private List<Map<String, Object>> buildThinkingSteps(Job job, StageASummary summary,
                                                         List<FileAnalysis> b, ProjectContext ctx) {
        List<Map<String, Object>> steps = new ArrayList<>();
        DiffStats s = job.getStats();
        addStep(steps, STAGE_THINKING,
                "综合差异统计：新增 " + s.getAdded() + " 处、修改 " + s.getModified() + " 处、删除 "
                        + s.getDeleted() + " 处；判断整体风险等级为「" + nz(summary.getOverallRisk())
                        + "」，影响范围：" + nz(summary.getImpactScope()));
        if (summary.getTestThemes() != null) {
            for (String t : summary.getTestThemes()) {
                addStep(steps, STAGE_THINKING, "提取测试要点：" + nz(t));
            }
        }
        if (b != null) {
            buildFileThinkingSteps(steps, b);
        }
        if (ctx != null && ctx.getSummary() != null && !ctx.getSummary().isEmpty()) {
            addStep(steps, STAGE_THINKING, "结合项目上下文画像：" + nz(ctx.getSummary()));
        }
        return steps;
    }

    /** 逐文件聚合「分析文件…」思考步骤 (提取自 buildThinkingSteps 以降低认知复杂度). */
    private static void buildFileThinkingSteps(List<Map<String, Object>> steps, List<FileAnalysis> b) {
        for (FileAnalysis fa : b) {
            StringBuilder sb = new StringBuilder();
            sb.append("分析文件 ").append(nz(fa.getKey())).append("：意图=").append(nz(fa.getIntent()))
                    .append("；风险=").append(nz(fa.getRisk()));
            if (fa.getImpact() != null && !fa.getImpact().isEmpty()) {
                sb.append("；影响=").append(fa.getImpact());
            }
            if (fa.getContextInfluence() != null && !fa.getContextInfluence().isEmpty()) {
                sb.append("；上下文影响=").append(fa.getContextInfluence());
            }
            addStep(steps, STAGE_THINKING, sb.toString());
        }
    }

    private static void addStep(List<Map<String, Object>> steps, String phase, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_PHASE, phase);
        m.put(KEY_MESSAGE, message);
        steps.add(m);
    }

    private static String nz(String s) {
        return (s == null || s.isEmpty()) ? "未提供" : s;
    }

    /** AI 分析结果：渲染后的 Markdown 全文 + 可视化思考步骤。 */
    private static final class AiAnalysisResult {
        final String markdown;
        final List<Map<String, Object>> thinking;
        AiAnalysisResult(String markdown, List<Map<String, Object>> thinking) {
            this.markdown = markdown;
            this.thinking = thinking;
        }
    }

    /** per-job 的 AI 工作负载（不可变快照）：反编译类图 + 前端文本差异 + 依赖 jar 分析，供多入口复用。 */
    private static final class AiWorkCache {
        final Map<String, DecompiledUnit> decompiled;
        final Map<String, DecompiledUnit> text;
        final LibJarDiff.Result libJar;
        AiWorkCache(Map<String, DecompiledUnit> decompiled, Map<String, DecompiledUnit> text,
                    LibJarDiff.Result libJar) {
            this.decompiled = decompiled;
            this.text = text;
            this.libJar = libJar;
        }
    }

    /** 取/算某 job 的 AI 工作负载：首次计算后按 jobId 缓存，report/ai-analyze/export 复用（评审 P1 #8）。 */
    private AiWorkCache getAiWork(Job job) {
        AiWorkCache c = aiWorkCache.get(job.id);
        if (c == null) {
            Map<String, DecompiledUnit> decompiled = buildClassMap(job, job.opts.getTopK());
            Map<String, DecompiledUnit> text = buildTextMap(job, job.opts.getTopK());
            LibJarDiff.Result libJar = LibJarDiff.analyze(job.getOldSnap(), job.getNewSnap(), job.getResult(),
                    new Decompiler(cfrPath(job.opts), findJava()), job.opts.getTopK());
            c = new AiWorkCache(decompiled, text, libJar);
            aiWorkCache.put(job.id, c);
            if (aiWorkCache.size() > AI_WORK_CACHE_MAX) aiWorkCache.clear(); // 粗粒度上限：超限清空，避免长期驻留
        }
        return c;
    }

    private void handleExport(HttpExchange ex, Job job) throws IOException {
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            Path outDir = Files.createTempDirectory("bempdiff-export-");
            AssetExporter exporter = new AssetExporter();
            // 复用 per-job 工作缓存（反编译/文本差异），避免与 report/ai-analyze 重复计算（评审 P1 #8）
            AiWorkCache work = getAiWork(job);
            Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>(work.decompiled);
            decompiled.putAll(work.text);
            exporter.exportDiffClasses(job.getResult(), job.getOldSnap(), job.getNewSnap(), outDir);
            exporter.exportDiffJars(job.getResult(), job.getOldSnap(), job.getNewSnap(), outDir);
            Path zip = exporter.exportDecompiledSources(decompiled, outDir, job.opts.getTopK());
            byte[] data = readAll(Files.newInputStream(zip));
            sendBytes(ex, 200, "application/zip", data);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "资产导出失败", e);
            sendError(ex, 500, "资产导出失败: " + e.getMessage());
        }
    }

    // ---------------- AI 流式分析（SSE） ----------------

    /**
     * AI 分析流式端点（SSE）。比对完成后，逐条推送「思考过程」(thinking)，再以字符块流式推送
     * Markdown 结论(answer)，最后发送 done（异常则 error）。与 report --ai 共用 runAiAnalysis 管线。
     */
    private void handleAiAnalyze(HttpExchange ex, Job job) throws IOException {
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            String projDir = null, category = null, prompt = null;
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                if (!body.isEmpty()) {
                    Map<String, Object> req = Json.parseObject(body);
                    projDir = Json.str(req, KEY_PROJECT_DIR, null);
                    category = Json.str(req, "category", null);
                    prompt = Json.str(req, "prompt", null);
                }
            }
            // 前端未显式传 projectDir 时，回退到配置中心的项目上下文设置（旧前端/漏传均生效）
            if (projDir == null && config.isProjectContextEnabled()) projDir = config.getProjectContextDir();
            // 复用 per-job 工作缓存（反编译/文本差异/jar 分析）：并行多类别分析共享同一份，避免 N 倍重复计算
            AiWorkCache work = getAiWork(job);
            Map<String, DecompiledUnit> decompiled = work.decompiled;
            Map<String, DecompiledUnit> text = work.text;
            LibJarDiff.Result libJar = work.libJar;

            // SSE 响应头：流式、禁缓冲/缓存、同源。content-length 不设置 → 分块传输。
            ex.getResponseHeaders().set(HDR_CONTENT_TYPE, "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("X-Accel-Buffering", "no");
            ex.sendResponseHeaders(200, 0);
            OutputStream os = ex.getResponseBody();

            AiAnalysisResult result;
            try { // NOSONAR(S1141) - AI 失败在局部产出 SSE 错误，需内层 try
                result = runAiAnalysis(job, decompiled, text, libJar, projDir, category, prompt);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "AI 分析失败", e);
                sseEvent(os, KEY_ERROR, Map.of(KEY_MESSAGE, "AI 分析失败: " + e.getMessage()));
                os.flush();
                os.close();
                return;
            }

            // 1) 思考过程：逐条推送（前端折叠展示，浅灰）
            for (Map<String, Object> step : result.thinking) {
                sseEvent(os, STAGE_THINKING, step);
                os.flush();
                sleepQuietly(110);
            }
            // 2) 答案：按字符块流式推送 Markdown（逐字呈现）
            String md = result.markdown;
            final int CHUNK = 28;
            for (int i = 0; i < md.length(); i += CHUNK) {
                int end = Math.min(md.length(), i + CHUNK);
                sseEvent(os, "answer", Map.of("text", md.substring(i, end)));
                os.flush();
                sleepQuietly(16);
            }
            // 3) 完成
            sseEvent(os, "done", new LinkedHashMap<>());
            os.flush();
            os.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "AI 流式分析异常", e);
            try {
                sseEvent(ex.getResponseBody(), KEY_ERROR, Map.of(KEY_MESSAGE, "AI 流式分析异常: " + e.getMessage()));
            } catch (IOException ignore) {
                // 响应已无法回写，忽略
            }
        }
    }

    /** 写出一个 SSE 事件（event: X\\ndata: <json>\\n\\n）。 */
    private static void sseEvent(OutputStream os, String event, Object data) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("event: ").append(event).append("\n");
        sb.append("data: ").append(Json.write(data)).append("\n\n");
        os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 静默休眠（流式节奏控制）；被打断时恢复中断标记。 */
    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------- 单条目反编译 ----------------

    private void handleEntry(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String sub = path.replaceFirst("^/api/entry/?", "");
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        if ("children".equals(sub)) {
            handleEntryChildren(ex);
            return;
        }
        if ("recursive".equals(sub)) {
            handleEntryRecursive(ex);
            return;
        }
        if (!"decompile".equals(sub)) {
            sendError(ex, 404, "未知 entry 操作: " + sub);
            return;
        }
        String jobId = queryParam(ex.getRequestURI(), KEY_JOB_ID);
        String key = queryParam(ex.getRequestURI(), "key");
        if (jobId == null || key == null) {
            sendError(ex, 400, "缺少 jobId / key");
            return;
        }
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            List<String> parts = ArchiveTree.splitCompound(key);
            String topKey = parts.get(0);
            String innerPath = (parts.size() >= 2) ? parts.get(parts.size() - 1) : null;
            FileClass fc;
            if (innerPath != null) {
                fc = PackageParser.classify(innerPath); // 内部条目按扩展名判定
            } else {
                LogicalEntry oe = job.getOldSnap().getEntries().get(topKey);
                LogicalEntry ne = job.getNewSnap().getEntries().get(topKey);
                fc = entryFileClass(oe, ne);
            }
            DecompiledUnit u;
            com.bempdiff.diff.DiffRules rules = job.opts.toDiffRules();
            if (innerPath == null) {
                // ---- 顶层条目（原逻辑）----
                LogicalEntry oe = job.getOldSnap().getEntries().get(topKey);
                LogicalEntry ne = job.getNewSnap().getEntries().get(topKey);
                if (fc == FileClass.CLASS) {
                    u = new Decompiler(cfrPath(job.opts), findJava()).decompile(job.getOldSnap(), job.getNewSnap(), oe, ne, key, rules);
                } else if (fc == FileClass.ARCHIVE || fc == FileClass.JAR) {
                    // 归档（含嵌套 jar）：清单对比。outerEntry 仅 folder 模式是真实磁盘路径；
                    // package 模式（outerEntry=包内相对路径）不能直接当磁盘路径用，需从包内抽取字节落临时文件。
                    Path oa = archivePath(oe), na = archivePath(ne);
                    boolean oaTemp = false, naTemp = false; // 仅本请求新建的临时落盘文件才允许删除（真实归档绝不删）
                    if (oa != null && !Files.isRegularFile(oa)) oa = null;
                    if (na != null && !Files.isRegularFile(na)) na = null;
                    if (oa == null && oe != null) { byte[] b = new PackageParser().readEntryBytes(job.getOldSnap(), oe); if (b != null) { oa = writeTemp(b); oaTemp = true; } }
                    if (na == null && ne != null) { byte[] b = new PackageParser().readEntryBytes(job.getNewSnap(), ne); if (b != null) { na = writeTemp(b); naTemp = true; } }
                    try {
                        u = new com.bempdiff.diff.ArchiveDiff().diff(key, oa, na);
                    } finally {
                        // 仅删除本请求新建的临时文件；oa/na 为真实归档路径（folder 模式）时标记为 false，绝不误删用户文件（修正 S1 修复的数据丢失隐患）
                        if (oaTemp) deleteTemp(oa);
                        if (naTemp) deleteTemp(na);
                    }
                } else if (fc == FileClass.OFFICE) {
                    // Office 文档（docx/xlsx/pptx）：解析内容为文本后行级 diff（需求：文档内容对比）
                    u = new com.bempdiff.diff.OfficeTextDiff().diff(job.getOldSnap(), job.getNewSnap(), oe, ne, key, fc, rules);
                } else {
                    u = new FrontendTextDiff().diff(job.getOldSnap(), job.getNewSnap(), oe, ne, key, fc, rules);
                }
            } else {
                // ---- 归档内部条目（复合键 outer!/inner）：直接对字节做对应 diff（委托 ArchiveTree）----
                u = ArchiveTree.computeInnerEntry(job.getOldSnap(), job.getNewSnap(), job.opts, key, cfrPath(job.opts), findJava());
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("key", key);
            resp.put("engine", u.getEngine());
            resp.put("ok", u.isOk());
            resp.put(KEY_ERROR, u.getError());
            resp.put("oldSource", u.getOldSource());
            resp.put("newSource", u.getNewSource());
            resp.put("diffText", u.getDiffText());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "反编译失败: " + key);
            sendError(ex, 500, "反编译失败: " + e.getMessage());
        }
    }

    /**
     * 差异树右键菜单的磁盘文件操作（仅文件夹对比模式）。
     * POST /api/file  body: { jobId, op: "info"|"delete"|"rename"|"copy", key, newName? }
     * - 包对比模式（job.mode != folder）：返回 400 明确提示"条目位于压缩包内，无磁盘路径"。
     * - 操作侧判定：DELETED→左侧；ADDED→右侧；其余→左侧（基准侧）；复制方向与目标侧相反。
     * - 成功/失败统一 200 + {ok:boolean, code, message, side?}，由前端 toast 呈现（FileOps 纯逻辑可单测）。
     */
    private void handleFile(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "仅支持 POST");
            return;
        }
        String body = readBody(ex);
        Map<String, Object> req = body.isEmpty() ? new LinkedHashMap<>() : Json.parseObject(body);
        String jobId = Json.str(req, KEY_JOB_ID, null);
        String key = Json.str(req, "key", null);
        String op = Json.str(req, "op", null);
        if (jobId == null || key == null || op == null) {
            sendError(ex, 400, "缺少 jobId / key / op");
            return;
        }
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        if (!MODE_FOLDER.equals(job.mode)) {
            // 包对比模式：条目在压缩包内，物理路径不存在 → 明确提示（不静默失败）
            sendError(ex, 400, "当前为包对比模式，条目位于压缩包内（无磁盘路径），不支持该文件操作；请切换到「文件夹」模式后重试");
            return;
        }
        try {
            Path oldRoot = job.getOldSnap().getFile().toAbsolutePath().normalize();
            Path newRoot = job.getNewSnap().getFile().toAbsolutePath().normalize();
            String status = statusOf(job, key);
            String side = DiffStatus.DELETED.name().equals(status) ? "left"
                    : DiffStatus.ADDED.name().equals(status) ? "right" : "left";
            Path root = "left".equals(side) ? oldRoot : newRoot;
            Path other = "left".equals(side) ? newRoot : oldRoot;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("key", key);
            resp.put("side", side);
            resp.put("status", status);
            switch (op) {
                case "info" -> {
                    FileOps.OpResult r = FileOps.info(root, key);
                    resp.put("ok", r.ok());
                    resp.put("code", r.code());
                    resp.put(KEY_MESSAGE, r.message());
                    if (r.info() != null) resp.put("info", r.info().toMap());
                }
                case "delete" -> {
                    FileOps.OpResult r = FileOps.deleteEntry(root, key);
                    resp.put("ok", r.ok());
                    resp.put("code", r.code());
                    resp.put(KEY_MESSAGE, r.message());
                }
                case "rename" -> {
                    String newName = Json.str(req, "newName", null);
                    FileOps.OpResult r = FileOps.renameEntry(root, key, newName);
                    resp.put("ok", r.ok());
                    resp.put("code", r.code());
                    resp.put(KEY_MESSAGE, r.message());
                }
                case "copy" -> {
                    // 复制方向：仅右侧存在（ADDED）→ 右→左；其余 → 左→右
                    String direction = DiffStatus.ADDED.name().equals(status) ? "r2l" : "l2r";
                    FileOps.OpResult r = FileOps.copyAcross(root, other, key, direction);
                    resp.put("ok", r.ok());
                    resp.put("code", r.code());
                    resp.put(KEY_MESSAGE, r.message());
                    resp.put("direction", direction);
                }
                default -> {
                    sendError(ex, 400, "未知文件操作: " + op);
                    return;
                }
            }
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "文件操作失败: " + op + " / " + key);
            sendError(ex, 500, "文件操作失败: " + e.getMessage());
        }
    }

    /** 查询 key 在差异结果中的状态（MODIFIED/ADDED/DELETED/UNCHANGED）。 */
    private static String statusOf(Job job, String key) {
        for (DiffStatus st : DiffStatus.values()) {
            if (job.getResult().get(st).contains(key)) return st.name();
        }
        return DiffStatus.UNCHANGED.name();
    }

    /**
     * 展开归档：返回内部条目列表，并跨旧/新两侧计算逐文件 ADDED/DELETED/MODIFIED/UNCHANGED。
     * key 可为顶层归档 key，或复合键 outer!/innerArchive（支持递归展开）。
     */
    private void handleEntryChildren(HttpExchange ex) throws IOException {
        String jobId = queryParam(ex.getRequestURI(), KEY_JOB_ID);
        String key = queryParam(ex.getRequestURI(), "key");
        if (jobId == null || key == null) {
            sendError(ex, 400, "缺少 jobId / key");
            return;
        }
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            List<Map<String, Object>> children = ArchiveTree.computeChildren(job.getOldSnap(), job.getNewSnap(), key);
            sendJson(ex, 200, children);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "展开归档失败: " + key);
            sendError(ex, 500, "展开归档失败: " + e.getMessage());
        }
    }

    /**
     * 自动递归解包：把归档（含任意深度嵌套归档）一次性展开为完整嵌套差异树。
     * 每层节点带 status（ADDED/DELETED/MODIFIED/UNCHANGED），嵌套归档节点带 children。
     */
    private void handleEntryRecursive(HttpExchange ex) throws IOException {
        String jobId = queryParam(ex.getRequestURI(), KEY_JOB_ID);
        String key = queryParam(ex.getRequestURI(), "key");
        if (jobId == null || key == null) {
            sendError(ex, 400, "缺少 jobId / key");
            return;
        }
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            Map<String, Object> tree = ArchiveTree.recursiveUnpack(job.getOldSnap(), job.getNewSnap(), key);
            sendJson(ex, 200, tree);
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "递归解包失败: " + key);
            sendError(ex, 500, "递归解包失败: " + e.getMessage());
        }
    }

    private static Path writeTemp(byte[] b) throws IOException {
        Path tmp = Files.createTempFile("bempdiff-entry-", ".bin");
        Files.write(tmp, b);
        // S1 修复：兜底在 JVM 退出时删除，避免 %TEMP% 持续累积（调用方也会显式删除）。
        try { tmp.toFile().deleteOnExit(); } catch (Exception ignored) {}
        return tmp;
    }

    /** S1 修复：安全删除临时文件（失败静默，不干扰主流程）。 */
    private static void deleteTemp(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }


    private static FileClass entryFileClass(LogicalEntry oe, LogicalEntry ne) {
        if (ne != null) return ne.getFileClass();
        if (oe != null) return oe.getFileClass();
        return FileClass.CLASS;
    }

    /** 条目是否位于嵌套 jar 内（true=在 war/jar 的某个内部条目里）。 */
    private static boolean isNested(LogicalEntry oe, LogicalEntry ne) {
        LogicalEntry pick = (ne != null) ? ne : oe;
        if (pick == null || pick.getSrc() == null) return false;
        return pick.getSrc().isNested();
    }

    /** 把 LogicalEntry 还原为磁盘上的归档文件路径（仅对"非嵌套"条目有效）。 */
    private static java.nio.file.Path archivePath(LogicalEntry e) {
        if (e == null || e.getSrc() == null) return null;
        if (e.getSrc().isNested()) return null; // 嵌套条目（如 war 内的 lib jar）不直接走 ArchiveDiff
        String outer = e.getSrc().getOuterEntry();
        if (outer == null) return null;
        return java.nio.file.Paths.get(outer);
    }

    // ---------------- 配置 ----------------

    private void handleConfig(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        if (ex.getRequestMethod().equals("GET")) {
            sendJson(ex, 200, config.toJson());
            return;
        }
        if (ex.getRequestMethod().equals("PUT")) {
            Map<String, Object> req = Json.parseObject(readBody(ex));
            config.updateFrom(req);
            sendJson(ex, 200, config.toJson());
            return;
        }
        sendError(ex, 405, "不支持的方法: " + ex.getRequestMethod());
    }

    // ---------------- AI 连接测试 ----------------

    private void handleAiTest(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        try {
            AiConfig aiCfg = readAiTestConfig(ex);
            HttpAiAnalyzer analyzer = new HttpAiAnalyzer(aiCfg);
            boolean ok = analyzer.testConnection(aiCfg);
            String msg = ok ? "连接成功" : errorText(analyzer.getLastError());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", ok);
            resp.put(KEY_MESSAGE, msg);
            resp.put("lastError", analyzer.getLastError());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "AI 连接测试异常", e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", false);
            resp.put(KEY_MESSAGE, "测试异常: " + e.getMessage());
            resp.put("lastError", e.getMessage());
            sendJson(ex, 200, resp);
        }
    }

    // ---------------- AI 模型列表自动获取 ----------------

    /**
     * 根据 API Base URL + API Key 自动获取可用模型列表（OpenAI 兼容 /models 接口）。
     * 请求体可携带 provider/baseUrl/apiKey/httpProxy/httpsProxy/blockPrivateEndpoints 临时覆盖，
     * 缺省时回退服务端已保存配置（与 handleAiTest 同一读取逻辑）。返回 {ok, models[], lastError, message}。
     */
    private void handleAiModels(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        try {
            AiConfig aiCfg = readAiTestConfig(ex);
            HttpAiAnalyzer analyzer = new HttpAiAnalyzer(aiCfg);
            List<String> models = analyzer.fetchModels(aiCfg);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", !models.isEmpty());
            resp.put("models", models);
            resp.put("lastError", analyzer.getLastError());
            resp.put(KEY_MESSAGE, models.isEmpty()
                    ? (analyzer.getLastError() != null ? analyzer.getLastError() : "未获取到模型列表")
                    : "已获取 " + models.size() + " 个可用模型");
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "获取模型列表异常", e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", false);
            resp.put("models", new ArrayList<>());
            resp.put("lastError", e.getMessage());
            resp.put(KEY_MESSAGE, "获取模型列表异常: " + e.getMessage());
            sendJson(ex, 200, resp);
        }
    }

    // ---------------- 项目级上下文（递归识别 + 缓存）管理 ----------------

    /**
     * 查询/刷新「上下文目录」的递归项目索引。
     * GET /api/ai/context?dir=<projDir>&refresh=1 ；POST body {dir, refresh} 亦可。
     * 返回项目清单（relPath/构建系统/模块数/依赖数/简述）与缓存信息，供配置中心展示与手动刷新。
     */
    private void handleAiContext(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        try {
            String dir = "";
            boolean refresh = false;
            if (ex.getRequestMethod().equals("GET")) {
                Map<String, String> q = queryOf(ex.getRequestURI().getQuery());
                dir = q.getOrDefault("dir", "");
                refresh = "1".equals(q.get("refresh")) || "true".equals(q.get("refresh"));
            } else if (ex.getRequestMethod().equals("POST")) {
                Map<String, Object> req = Json.parseObject(readBody(ex));
                dir = Json.str(req, "dir", "");
                refresh = Json.bool(req, "refresh", false);
            }
            if (dir.isEmpty()) dir = config.getProjectContextDir();
            if (dir == null || dir.isEmpty()) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("ok", false);
                resp.put(KEY_MESSAGE, "未指定 dir 且配置中心未设置项目上下文目录");
                sendJson(ex, 200, resp);
                return;
            }
            boolean fromCache;
            ProjectIndex idx = refresh ? ProjectContextService.refresh(dir)
                    : ProjectContextService.resolve(dir);
            if (idx == null) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("ok", false);
                resp.put(KEY_MESSAGE, "目录无效：\"" + dir + "\"");
                sendJson(ex, 200, resp);
                return;
            }
            // fromCache：显式刷新后必为 false；resolve 命中内存/磁盘缓存时为 true
            fromCache = !refresh && ProjectContextCache.load(dir) != null;
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.putAll(ProjectContextCache.summaryOf(idx, fromCache));
            List<Map<String, Object>> projects = new ArrayList<>();
            int totalJava = 0;
            for (ProjectIndex.ProjectEntry e : idx.getProjects()) {
                ProjectContext c = e.getCtx();
                if (c == null) continue;
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("relPath", e.getRelPath());
                pm.put("buildSystem", c.getBuildSystem());
                pm.put("moduleCount", c.getModules() == null ? 0 : c.getModules().size());
                pm.put("depCount", c.getDependencies() == null ? 0 : c.getDependencies().size());
                pm.put("fileCount", e.getJavaFileCount());
                pm.put("summary", c.getSummary());
                projects.add(pm);
                totalJava += e.getJavaFileCount();
            }
            resp.put("projects", projects);
            resp.put("javaFileCount", totalJava);
            resp.put(KEY_MESSAGE, (refresh ? "已刷新" : "已加载") + " " + idx.size() + " 个项目上下文");
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "查询项目上下文异常", e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", false);
            resp.put(KEY_MESSAGE, "查询项目上下文异常: " + e.getMessage());
            sendJson(ex, 200, resp);
        }
    }

    /** 解析查询串为键值对（dir、refresh 等），容错空值。 */
    private static Map<String, String> queryOf(String query) {
        Map<String, String> m = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return m;
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0) m.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return m;
    }

    private AiConfig readAiTestConfig(HttpExchange ex) throws IOException {
        AiConfig aiCfg = config.toAiConfig();
        aiCfg.setEnabled(true);
        if (!(ex.getRequestMethod().equals("POST") && ex.getRequestBody().available() > 0)) {
            return aiCfg;
        }
        Map<String, Object> req = Json.parseObject(readBody(ex));
        if (req.containsKey("provider")) aiCfg.setProvider(Json.str(req, "provider", aiCfg.getProvider()));
        if (req.containsKey("baseUrl")) aiCfg.setBaseUrl(Json.str(req, "baseUrl", aiCfg.getBaseUrl()));
        if (req.containsKey("apiKey")) aiCfg.setApiKey(Json.str(req, "apiKey", ""));
        if (req.containsKey(AI_MODEL_FIELD)) aiCfg.setModel(Json.str(req, AI_MODEL_FIELD, aiCfg.getModel()));
        if (req.containsKey("httpProxy")) aiCfg.setHttpProxy(Json.str(req, "httpProxy", aiCfg.getHttpProxy()));
        if (req.containsKey("httpsProxy")) aiCfg.setHttpsProxy(Json.str(req, "httpsProxy", aiCfg.getHttpsProxy()));
        if (req.containsKey("blockPrivateEndpoints")) aiCfg.setBlockPrivateEndpoints(Json.bool(req, "blockPrivateEndpoints", aiCfg.isBlockPrivateEndpoints()));
        return aiCfg;
    }

    private static String errorText(String lastError) {
        return lastError != null ? lastError : "连接失败";
    }

    // ---------------- 静态资源 ----------------

    private void handleStatic(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals(M_OPTIONS)) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = DEFAULT_INDEX;
        Path file = (webroot != null) ? webroot.resolve(path.substring(1)) : null;
        if (file != null && Files.isRegularFile(file)) {
            byte[] data = readAll(Files.newInputStream(file));
            sendBytesInline(ex, 200, mimeOf(path), data);
            return;
        }
        // 占位首页（前端未构建时也能打开看到服务存活）
        String html = "<!doctype html><html lang='zh'><head><meta charset='utf-8'>"
                + "<title>BempDiff Web</title><style>body{font-family:system-ui;margin:40px;background:#f6f8fa}"
                + "code{background:#eef;padding:2px 6px;border-radius:4px}</style></head><body>"
                + "<h1>BempDiff Web 服务</h1><p>服务已启动。可用接口：</p><ul>"
                + "<li><code>POST /api/session/compare</code> 比对（package/folder）</li>"
                + "<li><code>GET /api/job/{id}/status</code> 任务状态/差异树</li>"
                + "<li><code>GET /api/entry/decompile?jobId=&amp;key=</code> 单文件反编译</li>"
                + "<li><code>GET/PUT /api/config</code> 配置</li>"
                + "<li><code>POST /api/ai/test</code> AI 连接测试</li>"
                + "<li><code>POST /api/ai/models</code> 自动获取模型列表</li>"
                + "<li><code>POST /api/job/{id}/report</code> 生成 Markdown 报告</li>"
                + "<li><code>POST /api/job/{id}/export</code> 导出差异资产 zip</li>"
                + "</ul><p>前端 SPA 构建后放置到 webroot 目录即可被本服务托管。</p></body></html>";
        sendText(ex, 200, "text/html", html);
    }

    private static String mimeOf(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ---------------- AI 智能分类与优先级（A1+B2） ----------------

    /**
     * 智能分类与优先级（B2 自动打标 + A1 风险分级）。
     * 复用两阶段管线的 stageB（同样受成本闸门/脱敏约束），对每个候选文件产出
     * { key, risk(HIGH/MEDIUM/LOW), category, reason }。
     * 无 API Key（Mock 模式）时 risk 走确定性启发式、category 始终启发式，保证离线可验证。
     */
    private void handleClassify(HttpExchange ex, Job job) throws IOException { // NOSONAR(S3776, S6541) - 分类请求处理，双路径+嵌套分支为业务必需，拆分降低可读性
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            String projDir = null;
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                if (!body.isEmpty()) {
                    Map<String, Object> req = Json.parseObject(body);
                    projDir = Json.str(req, KEY_PROJECT_DIR, null);
                }
            }
            // 分类需覆盖全部变更文件（不局限于 L1 内部类），故直接对 MODIFIED/ADDED/DELETED 反编译/文本化，
            // 不依赖 buildClassMap 的 L1 过滤。反编译失败（ok=false）仍保留单元，启发式打标不受影响。
            Decompiler dec = new Decompiler(cfrPath(job.opts), findJava());
            FrontendTextDiff ftd = new FrontendTextDiff();
            com.bempdiff.diff.DiffRules rules = job.opts.toDiffRules();
            Map<String, DecompiledUnit> aiMap = new LinkedHashMap<>();
            for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
                for (String k : job.getResult().get(st)) {
                    LogicalEntry oe = job.getOldSnap().getEntries().get(k);
                    LogicalEntry ne = job.getNewSnap().getEntries().get(k);
                    FileClass fc = fileClassOfKey(k, job.getOldSnap(), job.getNewSnap());
                    DecompiledUnit u = (fc == FileClass.CLASS)
                            ? dec.decompile(job.getOldSnap(), job.getNewSnap(), oe, ne, k, rules)
                            : ftd.diff(job.getOldSnap(), job.getNewSnap(), oe, ne, k, fc, rules);
                    aiMap.put(k, u);
                }
            }
            ProjectContext ctx = (projDir != null && !projDir.isEmpty())
                    ? ProjectContextAnalyzer.analyze(Paths.get(projDir)) : null;
            AiConfig aiCfg = config.toAiConfig();
            boolean realLlm = aiCfg.getApiKey() != null && !aiCfg.getApiKey().isEmpty();
            AiAnalyzer analyzer = realLlm ? new HttpAiAnalyzer(aiCfg)
                    : new MockAiAnalyzer(Paths.get(System.getProperty(PROP_USER_HOME), DIR_BEMPDIFF, DIR_AI_REPLAY));
            // 修复：智能分类覆盖全部变更文件，不按 stageBTopK 截断。
            // 此前复用报告/分析的 topK 截断（默认 15），导致差异树只对前 N 个文件打标、
            // 其余文件无分类（用户反馈「分类不全」）。候选顺序保持 aiMap 插入序（ADDED/MODIFIED/DELETED）。
            List<AiAnalyzer.DecompileReq> cands = buildAllCandidates(aiMap, job);
            List<FileAnalysis> b = analyzer.stageB(cands, aiCfg, ctx);
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++) {
                AiAnalyzer.DecompileReq req = cands.get(i);
                FileAnalysis fa = (i < b.size()) ? b.get(i) : null;
                DiffStatus st = statusOf(job.getResult(), req.key);
                String rawRisk = (fa != null) ? fa.getRisk() : null;
                String risk = realLlm ? normalizeRisk(rawRisk)
                                       : heuristicRisk(req.key, req.fileClass);
                String category = categorize(req.key, req.fileClass, fa != null ? fa.getIntent() : null);
                String reason = (fa != null && fa.getIntent() != null && !fa.getIntent().isEmpty())
                        ? fa.getIntent() : heuristicReason(req.fileClass, st);
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("key", req.key);
                it.put("risk", risk);
                it.put("category", category);
                it.put("reason", reason);
                items.add(it);
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("items", items);
            resp.put("coverage", cands.size());
            resp.put("totalChanged", job.getResult().get(DiffStatus.ADDED).size()
                    + job.getResult().get(DiffStatus.MODIFIED).size() + job.getResult().get(DiffStatus.DELETED).size());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "智能分类失败", e);
            sendError(ex, 500, "智能分类失败: " + e.getMessage());
        }
    }

    // ---------------- 成本闸门：AI token 预估（P0 #5） ----------------

    /**
     * AI 成本预估端点：复用两阶段管线的 prompt 构造（PromptBuilders.buildStageA/B）+ estimateTokens
     * （prompt 字符数 / 2），精确预估三大 AI 入口的 token 消耗，供前端超阈值强制确认。
     *  - report / analyze：共用 runAiAnalysis 管线 → stageA 一次 + stageB 逐文件
     *  - classify：仅 stageB 逐文件（无 stageA）
     * 带 per-job 缓存，避免重复反编译。
     */
    private void handleAiEstimate(HttpExchange ex, Job job) throws IOException {
        if (!"DONE".equals(job.getStatus())) {
            sendError(ex, 409, MSG_NOT_DONE + job.getStatus());
            return;
        }
        try {
            AiConfig aiCfg = config.toAiConfig();
            // 修复：预估按分析项区分（与 report/ai-analyze 真实 prompt 口径一致）。
            // 缓存键含类别：不同分析项 prompt 不同、token 预估不同，必须分开缓存。
            String category = null;
            String prompt = null;
            String projDir = null;
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                if (!body.isEmpty()) {
                    Map<String, Object> req = Json.parseObject(body);
                    category = Json.str(req, "category", null);
                    prompt = Json.str(req, "prompt", null);
                    projDir = Json.str(req, KEY_PROJECT_DIR, null);
                }
            }
            // 前端未显式传 projectDir 时回退到配置中心的项目上下文设置（与 runAiAnalysis 口径一致）。
            if (projDir == null && config.isProjectContextEnabled()) projDir = config.getProjectContextDir();
            String focus = buildFocus(category, prompt);
            // 缓存键含阈值：阈值可热更新，变化时必须失效重算（否则闸门阈值被冻结，与配置脱节）。
            double threshold = aiCfg.getCostGateWarnTokens();
            String cacheKey = job.id + "@" + threshold + "@" + String.valueOf(focus);
            Map<String, Object> cached = aiEstimateCache.get(cacheKey);
            if (cached != null) { sendJson(ex, 200, cached); return; }
            Map<String, Object> r = new LinkedHashMap<>();
            // M4 修复：report/analyze 的 ctx 须与真实 runAiAnalysis 口径一致（按 projDir 推导），
            // 此前传 null 导致启用「项目级上下文增强」时预估低于实际消耗（成本闸门可能漏拦）。
            ProjectContext ctx = reportContext(projDir);
            double rep = estimateStageAStageB(job, aiCfg, ctx, focus);
            r.put("report", rep);
            r.put("analyze", rep);
            // classify 的 ctx 与真实调用一致：projectContextEnabled 时注入项目上下文
            r.put("classify", estimateStageBOnly(job, aiCfg, classifyContext()));
            r.put("threshold", threshold);
            aiEstimateCache.put(cacheKey, r);
            sendJson(ex, 200, r);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "AI 成本预估失败", e);
            sendError(ex, 500, "AI 成本预估失败: " + e.getMessage());
        }
    }

    /** classify 真实调用使用的项目上下文：仅当 projectContextEnabled 且目录非空时注入（与 runAiClassify 决策一致）。 */
    private ProjectContext classifyContext() {
        if (config.isProjectContextEnabled() && config.getProjectContextDir() != null
                && !config.getProjectContextDir().isEmpty()) {
            try {
                return ProjectContextAnalyzer.analyze(Paths.get(config.getProjectContextDir()));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "项目上下文分析失败（预估降级为无上下文）", e);
            }
        }
        return null;
    }

    /** M4 修复：report/analyze 预估使用的项目上下文，口径与 runAiAnalysis 完全一致——
     *  优先用本次请求携带的 projectDir，未传则回退到配置中心的项目上下文目录。 */
    private ProjectContext reportContext(String projDir) {
        if (projDir != null && !projDir.isEmpty()) {
            try {
                return ProjectContextAnalyzer.analyze(Paths.get(projDir));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "项目上下文分析失败（预估降级为无上下文）", e);
            }
        }
        return null;
    }

    /** 报告/流式分析：stageA 一次 + stageB 逐候选文件（截断 stageBTopK），汇总 token 估算。 */
    private double estimateStageAStageB(Job job, AiConfig aiCfg, ProjectContext ctx, String focus) {
        Map<String, DecompiledUnit> aiMap = aiMapForReport(job);
        AiAnalyzer analyzer = new MockAiAnalyzer(
                Paths.get(System.getProperty(PROP_USER_HOME), DIR_BEMPDIFF, DIR_AI_REPLAY));
        double tokens = analyzer.estimateTokens(
                analyzer.buildStageAPrompt(job.getResult(), aiMap, aiCfg, ctx, focus));
        for (AiAnalyzer.DecompileReq req : buildCandidates(aiMap, job, aiCfg.getStageBTopK())) {
            tokens += analyzer.estimateTokens(
                    analyzer.buildStageBPrompt(req.key, req.unit, req.fileClass, aiCfg, ctx, focus));
        }
        return tokens;
    }

    /** 智能分类：仅 stageB 逐候选文件。修复：覆盖全部变更文件（与 handleClassify 一致，
     *  不按 stageBTopK 截断——分类是逐文件打标，截断会使成本预估低于真实消耗、成本闸门漏拦）。 */
    private double estimateStageBOnly(Job job, AiConfig aiCfg, ProjectContext ctx) {
        Map<String, DecompiledUnit> aiMap = aiMapForClassify(job);
        AiAnalyzer analyzer = new MockAiAnalyzer(
                Paths.get(System.getProperty(PROP_USER_HOME), DIR_BEMPDIFF, DIR_AI_REPLAY));
        double tokens = 0;
        for (AiAnalyzer.DecompileReq req : buildAllCandidates(aiMap, job)) {
            tokens += analyzer.estimateTokens(
                    analyzer.buildStageBPrompt(req.key, req.unit, req.fileClass, aiCfg, ctx));
        }
        return tokens;
    }

    /** 报告/流式分析共用的 aiMap（L1 内部类 + 前端文本，截断 topK）；与 runAiAnalysis 构造一致。 */
    private Map<String, DecompiledUnit> aiMapForReport(Job job) {
        Map<String, DecompiledUnit> decompiled = buildClassMap(job, job.opts.getTopK());
        Map<String, DecompiledUnit> text = buildTextMap(job, job.opts.getTopK());
        Map<String, DecompiledUnit> m = new LinkedHashMap<>(decompiled);
        m.putAll(text);
        return m;
    }

    /** 智能分类共用的 aiMap（覆盖全部 MODIFIED/ADDED/DELETED，不局限于 L1）；与 handleClassify 构造一致。 */
    private Map<String, DecompiledUnit> aiMapForClassify(Job job) {
        Decompiler dec = new Decompiler(cfrPath(job.opts), findJava());
        FrontendTextDiff ftd = new FrontendTextDiff();
        com.bempdiff.diff.DiffRules rules = job.opts.toDiffRules();
        Map<String, DecompiledUnit> aiMap = new LinkedHashMap<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String key : job.getResult().get(st)) {
                LogicalEntry oe = job.getOldSnap().getEntries().get(key);
                LogicalEntry ne = job.getNewSnap().getEntries().get(key);
                FileClass fc = fileClassOfKey(key, job.getOldSnap(), job.getNewSnap());
                DecompiledUnit u = (fc == FileClass.CLASS)
                        ? dec.decompile(job.getOldSnap(), job.getNewSnap(), oe, ne, key, rules)
                        : ftd.diff(job.getOldSnap(), job.getNewSnap(), oe, ne, key, fc, rules);
                aiMap.put(key, u);
            }
        }
        return aiMap;
    }

    /** 按 aiMap 构造候选（与 runAiAnalysis 候选顺序、截断一致），用于 report/analyze 的 stageB 估算。 */
    private List<AiAnalyzer.DecompileReq> buildCandidates(
            Map<String, DecompiledUnit> aiMap, Job job, int topK) {
        List<AiAnalyzer.DecompileReq> cands = new ArrayList<>();
        for (Map.Entry<String, DecompiledUnit> en : aiMap.entrySet()) {
            cands.add(new AiAnalyzer.DecompileReq(en.getKey(), en.getValue(),
                    fileClassOfKey(en.getKey(), job.getOldSnap(), job.getNewSnap())));
        }
        if (cands.size() > topK) cands = cands.subList(0, topK);
        return cands;
    }

    /** 全量候选（不截断）：智能分类需覆盖全部变更文件，与 handleClassify 真实调用一致。 */
    private List<AiAnalyzer.DecompileReq> buildAllCandidates(
            Map<String, DecompiledUnit> aiMap, Job job) {
        List<AiAnalyzer.DecompileReq> cands = new ArrayList<>();
        for (Map.Entry<String, DecompiledUnit> en : aiMap.entrySet()) {
            cands.add(new AiAnalyzer.DecompileReq(en.getKey(), en.getValue(),
                    fileClassOfKey(en.getKey(), job.getOldSnap(), job.getNewSnap())));
        }
        return cands;
    }

    private static DiffStatus statusOf(DiffResult diff, String k) {
        for (DiffStatus st : DiffStatus.values()) if (diff.get(st).contains(k)) return st;
        return DiffStatus.MODIFIED;
    }

    /** 归一化 AI 风险等级到 HIGH/MEDIUM/LOW（大小写/中英文兼容，缺省 MEDIUM）。 */
    private static String normalizeRisk(String r) {
        if (r == null) return RISK_MEDIUM;
        String u = r.trim().toUpperCase();
        if (u.contains("HIGH") || u.contains("高")) return "HIGH";
        if (u.contains("LOW") || u.contains("低")) return "LOW";
        return RISK_MEDIUM;
    }

    /** 离线确定性风险启发式（无 API Key 时使用）。 */
    private static String heuristicRisk(String key, FileClass fc) {
        if (fc == FileClass.CONFIG) return "LOW";
        if (fc != null && fc.isFrontendText()) return "LOW";
        String k = (key == null ? "" : key).toLowerCase();
        if (k.contains("controller") || k.contains("api") || k.contains("facade") || k.contains("endpoint")) return "HIGH";
        if (k.contains("dao") || k.contains("mapper") || k.contains("repository")
                || k.contains("service") || k.contains("manager") || k.contains("biz")) return RISK_MEDIUM;
        return RISK_MEDIUM;
    }

    private static String heuristicReason(FileClass fc, DiffStatus st) {
        String status;
        if (st == DiffStatus.ADDED) status = "新增";
        else if (st == DiffStatus.DELETED) status = "删除";
        else status = "修改";
        if (fc == FileClass.CONFIG) return status + "配置文件，影响配置语义/启动加载，建议核对配置项";
        if (fc != null && fc.isFrontendText()) return status + "前端资源，影响页面/交互，建议核对渲染与交互";
        return status + "后端类，按包路径/变更类型初判风险，建议结合上下文复核";
    }

    /** 变更类别启发式（离线确定性，不依赖 LLM 输出）。 */
    private static String categorize(String key, FileClass fc, String intent) {
        if (fc == FileClass.CONFIG) return "配置";
        if (fc == FileClass.JS) return "前端脚本";
        if (fc == FileClass.HTML) return "前端模板";
        if (fc == FileClass.CSS) return "前端样式";
        if (fc == FileClass.JSP) return "页面";
        String k = (key == null ? "" : key).toLowerCase();
        String it = (intent == null ? "" : intent).toLowerCase();
        if (isInterfaceCandidate(k, it)) return "接口";
        if (isDataCandidate(k, it)) return "数据";
        if (isServiceCandidate(k, it)) return "服务";
        if (isConfigCandidate(k)) return "配置";
        if (isModelCandidate(k)) return "模型";
        return "业务代码";
    }

    private static boolean isInterfaceCandidate(String k, String it) {
        return k.contains("controller") || k.contains("api") || k.contains("facade") || it.contains("接口") || it.contains("api");
    }
    private static boolean isDataCandidate(String k, String it) {
        return k.contains("dao") || k.contains("mapper") || k.contains("repository")
                || it.contains("数据库") || it.contains("sql") || it.contains("数据访问");
    }
    private static boolean isServiceCandidate(String k, String it) {
        return k.contains("service") || k.contains("manager") || k.contains("biz") || it.contains("服务");
    }
    private static boolean isConfigCandidate(String k) {
        return k.contains("config") || k.contains("bean") || k.contains("context") || k.contains("properties");
    }
    private static boolean isModelCandidate(String k) {
        return k.contains("entity") || k.contains(AI_MODEL_FIELD) || k.contains("domain")
                || k.contains("dto") || k.contains("vo") || k.contains("po");
    }

    // ---------------- 复用 Main 的映射逻辑 ----------------

    private Map<String, DecompiledUnit> buildClassMap(Job job, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(job.getResult(), job.getOldSnap(), job.getNewSnap());
        if (cands.size() > topK) cands = cands.subList(0, topK);
        Decompiler dec = new Decompiler(cfrPath(job.opts), findJava());
        com.bempdiff.diff.DiffRules rules = job.opts.toDiffRules();
        for (String k : cands) {
            m.put(k, dec.decompile(job.getOldSnap(), job.getNewSnap(),
                    job.getOldSnap().getEntries().get(k), job.getNewSnap().getEntries().get(k), k, rules));
        }
        return m;
    }

    private Map<String, DecompiledUnit> buildTextMap(Job job, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        FrontendTextDiff ftd = new FrontendTextDiff();
        com.bempdiff.diff.DiffRules rules = job.opts.toDiffRules();
        List<String> cands = DiffEngine.collectTextDiffCandidates(job.getResult(), job.getOldSnap(), job.getNewSnap());
        int limit = Math.min(cands.size(), topK);
        for (int i = 0; i < limit; i++) {
            String k = cands.get(i);
            LogicalEntry oe = job.getOldSnap().getEntries().get(k);
            LogicalEntry ne = job.getNewSnap().getEntries().get(k);
            FileClass fc = textFileClass(oe, ne);
            m.put(k, ftd.diff(job.getOldSnap(), job.getNewSnap(), oe, ne, k, fc, rules));
        }
        return m;
    }

    private static FileClass textFileClass(LogicalEntry oe, LogicalEntry ne) {
        if (ne != null) return ne.getFileClass();
        if (oe != null) return oe.getFileClass();
        return FileClass.JS;
    }

    private static FileClass fileClassOfKey(String k, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        LogicalEntry e = oldSnap.getEntries().get(k);
        if (e == null) e = newSnap.getEntries().get(k);
        return (e != null) ? e.getFileClass() : FileClass.CLASS;
    }

    private static String nullToNA(String s) {
        return (s == null || s.isEmpty()) ? "（未知）" : s;
    }
}