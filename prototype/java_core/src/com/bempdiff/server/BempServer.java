package com.bempdiff.server;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectContextAnalyzer;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.diff.LibJarDiff;
import com.bempdiff.export.AssetExporter;
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

    private final JobStore store = new JobStore();
    private final ServerConfig config;
    private final AtomicLong seq = new AtomicLong(System.nanoTime() % 1_000_000);
    private final Path webroot;

    public BempServer(ServerConfig config, Path webroot) {
        this.config = config;
        this.webroot = webroot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/session/compare", this::handleCompare);
        server.createContext("/api/job", this::handleJob);
        server.createContext("/api/entry", this::handleEntry);
        server.createContext("/api/config", this::handleConfig);
        server.createContext("/api/ai/test", this::handleAiTest);
        server.createContext("/", this::handleStatic);
        server.start();
        LOG.info(() -> "[Web] BempDiff HTTP 服务已启动：http://127.0.0.1:" + port + "  (仅本机)");
        System.out.println("BempDiff Web 服务已启动: http://127.0.0.1:" + port); // NOSONAR
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
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        addCors(ex);
        byte[] b = Json.write(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void sendText(HttpExchange ex, int code, String mime, String text) throws IOException {
        addCors(ex);
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", mime + "; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static void sendBytes(HttpExchange ex, int code, String mime, byte[] data) throws IOException {
        addCors(ex);
        ex.getResponseHeaders().add("Content-Type", mime);
        ex.getResponseHeaders().add("Content-Disposition", "attachment");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
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
        if (opts.cfrJar == null || opts.cfrJar.isEmpty()) return null;
        return Paths.get(opts.cfrJar);
    }

    // ---------------- 比对 ----------------

    private void handleCompare(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("OPTIONS")) {
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

            PackageSnapshot oldSnap;
            PackageSnapshot newSnap;
            String mode;
            DiffEngine engine = new DiffEngine();
            if ("folder".equals(leftType)) {
                FolderParser fp = new FolderParser();
                oldSnap = fp.parse(Paths.get(leftPath), pc);
                newSnap = fp.parse(Paths.get(rightPath), pc);
                mode = "folder";
            } else {
                PackageParser pp = new PackageParser();
                oldSnap = pp.parse(Paths.get(leftPath), pc, opts.expandAll);
                newSnap = pp.parse(Paths.get(rightPath), pc, opts.expandAll);
                mode = "package";
            }
            DiffResult r = engine.compute(oldSnap, newSnap);
            DiffStats s = engine.stats(r);

            String jobId = "job-" + seq.incrementAndGet();
            Job job = new Job(jobId, mode, oldSnap, newSnap, r, s, opts);
            store.put(job);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jobId", jobId);
            resp.put("mode", mode);
            resp.put("oldFile", oldSnap.getFile().getFileName().toString());
            resp.put("newFile", newSnap.getFile().getFileName().toString());
            resp.put("oldVersion", nullToNA(oldSnap.getVersion()));
            resp.put("newVersion", nullToNA(newSnap.getVersion()));
            resp.put("stats", statsJson(s, oldSnap));
            resp.put("tree", buildTree(r, oldSnap, newSnap));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "比对失败", e);
            sendError(ex, 500, "比对失败: " + e.getMessage());
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
                LogicalEntry e = oldSnap.getEntries().get(k);
                if (e == null) e = newSnap.getEntries().get(k);
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("key", k);
                node.put("status", st.name());
                node.put("layer", (e != null && e.getLayer() != null) ? e.getLayer().name() : "?");
                node.put("fileClass", (e != null && e.getFileClass() != null) ? e.getFileClass().name() : "OTHER");
                node.put("size", e != null ? e.getSize() : 0);
                tree.add(node);
            }
        }
        return tree;
    }

    // ---------------- Job：状态 / 报告 / 导出 ----------------

    private void handleJob(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(); // /api/job/{id}/{sub}
        String sub = path.replaceFirst("^/api/job/?", "");
        String[] parts = sub.split("/");
        if (parts.length < 1 || parts[0].isEmpty()) {
            sendError(ex, 400, "缺少 jobId");
            return;
        }
        String jobId = parts[0];
        String action = parts.length >= 2 ? parts[1] : "status";
        Job job = store.get(jobId);
        if (job == null) {
            sendError(ex, 404, "任务不存在: " + jobId);
            return;
        }
        if (ex.getRequestMethod().equals("OPTIONS")) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        if ("status".equals(action)) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jobId", job.id);
            resp.put("mode", job.mode);
            resp.put("status", job.status);
            resp.put("error", job.error);
            resp.put("stats", statsJson(job.stats, job.oldSnap));
            resp.put("tree", buildTree(job.result, job.oldSnap, job.newSnap));
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
        sendError(ex, 404, "未知 job 操作: " + action);
    }

    private void handleReport(HttpExchange ex, Job job) throws IOException {
        try {
            boolean ai = false;
            String projDir = null;
            if (ex.getRequestMethod().equals("POST")) {
                String body = readBody(ex);
                if (!body.isEmpty()) {
                    Map<String, Object> req = Json.parseObject(body);
                    ai = Json.bool(req, "ai", false);
                    projDir = Json.str(req, "projectDir", null);
                }
            }
            Map<String, DecompiledUnit> decompiled = buildClassMap(job, job.opts.topK);
            Map<String, DecompiledUnit> text = buildTextMap(job, job.opts.topK);
            LibJarDiff.Result libJar = LibJarDiff.analyze(job.oldSnap, job.newSnap, job.result,
                    new Decompiler(cfrPath(job.opts), findJava()), job.opts.topK);
            MarkdownReport rep = new MarkdownReport(job.opts.topK);
            String md;
            if (ai) {
                AiConfig aiCfg = config.toAiConfig();
                ProjectContext ctx = (projDir != null && !projDir.isEmpty())
                        ? ProjectContextAnalyzer.analyze(Paths.get(projDir)) : null;
                AiAnalyzer analyzer = (aiCfg.getApiKey() != null && !aiCfg.getApiKey().isEmpty())
                        ? new HttpAiAnalyzer(aiCfg) : new MockAiAnalyzer(
                        Paths.get(System.getProperty("user.home"), ".bempdiff", "ai_replay"));
                Map<String, DecompiledUnit> aiMap = new LinkedHashMap<>(decompiled);
                aiMap.putAll(text);
                List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
                for (String k : aiMap.keySet()) bCands.add(new AiAnalyzer.DecompileReq(k, aiMap.get(k),
                        fileClassOfKey(k, job.oldSnap, job.newSnap)));
                if (bCands.size() > aiCfg.getStageBTopK()) bCands = bCands.subList(0, aiCfg.getStageBTopK());
                StageASummary summary = analyzer.stageA(job.result, aiMap, aiCfg, ctx);
                List<FileAnalysis> b = analyzer.stageB(bCands, aiCfg, ctx);
                md = rep.render(job.oldSnap, job.newSnap, job.result, job.stats, decompiled, text,
                        summary, b, ctx, libJar);
            } else {
                md = rep.render(job.oldSnap, job.newSnap, job.result, job.stats, decompiled, text, libJar);
            }
            sendText(ex, 200, "text/markdown", md);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "报告生成失败", e);
            sendError(ex, 500, "报告生成失败: " + e.getMessage());
        }
    }

    private void handleExport(HttpExchange ex, Job job) throws IOException {
        try {
            Path outDir = Files.createTempDirectory("bempdiff-export-");
            AssetExporter exporter = new AssetExporter();
            Map<String, DecompiledUnit> decompiled = buildClassMap(job, job.opts.topK);
            Map<String, DecompiledUnit> text = buildTextMap(job, job.opts.topK);
            decompiled.putAll(text);
            exporter.exportDiffClasses(job.result, job.oldSnap, job.newSnap, outDir);
            exporter.exportDiffJars(job.result, job.oldSnap, job.newSnap, outDir);
            Path zip = exporter.exportDecompiledSources(decompiled, outDir, job.opts.topK);
            byte[] data = readAll(Files.newInputStream(zip));
            sendBytes(ex, 200, "application/zip", data);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "资产导出失败", e);
            sendError(ex, 500, "资产导出失败: " + e.getMessage());
        }
    }

    // ---------------- 单条目反编译 ----------------

    private void handleEntry(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String sub = path.replaceFirst("^/api/entry/?", "");
        if (!"decompile".equals(sub)) {
            sendError(ex, 404, "未知 entry 操作: " + sub);
            return;
        }
        if (ex.getRequestMethod().equals("OPTIONS")) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        String jobId = queryParam(ex.getRequestURI(), "jobId");
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
        try {
            LogicalEntry oe = job.oldSnap.getEntries().get(key);
            LogicalEntry ne = job.newSnap.getEntries().get(key);
            FileClass fc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.CLASS);
            DecompiledUnit u;
            if (fc == FileClass.CLASS) {
                u = new Decompiler(cfrPath(job.opts), findJava()).decompile(job.oldSnap, job.newSnap, oe, ne, key);
            } else {
                u = new FrontendTextDiff().diff(job.oldSnap, job.newSnap, oe, ne, key, fc);
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("key", key);
            resp.put("engine", u.getEngine());
            resp.put("ok", u.isOk());
            resp.put("error", u.getError());
            resp.put("oldSource", u.getOldSource());
            resp.put("newSource", u.getNewSource());
            resp.put("diffText", u.getDiffText());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "反编译失败: " + key, e);
            sendError(ex, 500, "反编译失败: " + e.getMessage());
        }
    }

    // ---------------- 配置 ----------------

    private void handleConfig(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("OPTIONS")) {
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
        if (ex.getRequestMethod().equals("OPTIONS")) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        try {
            AiConfig aiCfg;
            if (ex.getRequestMethod().equals("POST") && ex.getRequestBody().available() > 0) {
                Map<String, Object> req = Json.parseObject(readBody(ex));
                aiCfg = config.toAiConfig(); // 以持久化配置为基准，再用请求覆盖
                if (req.containsKey("provider")) aiCfg.setProvider(Json.str(req, "provider", aiCfg.getProvider()));
                if (req.containsKey("baseUrl")) aiCfg.setBaseUrl(Json.str(req, "baseUrl", aiCfg.getBaseUrl()));
                if (req.containsKey("apiKey")) aiCfg.setApiKey(Json.str(req, "apiKey", ""));
                if (req.containsKey("model")) aiCfg.setModel(Json.str(req, "model", aiCfg.getModel()));
                if (req.containsKey("httpProxy")) aiCfg.setHttpProxy(Json.str(req, "httpProxy", aiCfg.getHttpProxy()));
                if (req.containsKey("httpsProxy")) aiCfg.setHttpsProxy(Json.str(req, "httpsProxy", aiCfg.getHttpsProxy()));
                if (req.containsKey("blockPrivateEndpoints")) aiCfg.setBlockPrivateEndpoints(Json.bool(req, "blockPrivateEndpoints", aiCfg.isBlockPrivateEndpoints()));
                aiCfg.setEnabled(true);
            } else {
                aiCfg = config.toAiConfig();
                aiCfg.setEnabled(true);
            }
            HttpAiAnalyzer analyzer = new HttpAiAnalyzer(aiCfg);
            boolean ok = analyzer.testConnection(aiCfg);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", ok);
            resp.put("message", ok ? "连接成功" : (analyzer.getLastError() != null ? analyzer.getLastError() : "连接失败"));
            resp.put("lastError", analyzer.getLastError());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "AI 连接测试异常", e);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", false);
            resp.put("message", "测试异常: " + e.getMessage());
            resp.put("lastError", e.getMessage());
            sendJson(ex, 200, resp);
        }
    }

    // ---------------- 静态资源 ----------------

    private void handleStatic(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("OPTIONS")) {
            sendJson(ex, 204, new LinkedHashMap<>());
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        Path file = (webroot != null) ? webroot.resolve(path.substring(1)) : null;
        if (file != null && Files.isRegularFile(file)) {
            byte[] data = readAll(Files.newInputStream(file));
            sendBytes(ex, 200, mimeOf(path), data);
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

    // ---------------- 复用 Main 的映射逻辑 ----------------

    private Map<String, DecompiledUnit> buildClassMap(Job job, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(job.result, job.oldSnap, job.newSnap);
        if (cands.size() > topK) cands = cands.subList(0, topK);
        Decompiler dec = new Decompiler(cfrPath(job.opts), findJava());
        for (String k : cands) {
            m.put(k, dec.decompile(job.oldSnap, job.newSnap,
                    job.oldSnap.getEntries().get(k), job.newSnap.getEntries().get(k), k));
        }
        return m;
    }

    private Map<String, DecompiledUnit> buildTextMap(Job job, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        FrontendTextDiff ftd = new FrontendTextDiff();
        List<String> cands = DiffEngine.collectTextDiffCandidates(job.result, job.oldSnap, job.newSnap);
        int limit = Math.min(cands.size(), topK);
        for (int i = 0; i < limit; i++) {
            String k = cands.get(i);
            LogicalEntry oe = job.oldSnap.getEntries().get(k);
            LogicalEntry ne = job.newSnap.getEntries().get(k);
            FileClass fc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.JS);
            m.put(k, ftd.diff(job.oldSnap, job.newSnap, oe, ne, k, fc));
        }
        return m;
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
