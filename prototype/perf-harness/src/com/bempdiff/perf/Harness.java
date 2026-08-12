package com.bempdiff.perf;

import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.report.MarkdownReport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * test-only HTTP 压测适配层（不进产品）。
 * 把 BempDiff 的核心操作暴露为 REST 端点，供 JMeter 以 HTTP 驱动，得到
 * 响应时间 / TPS / 错误率；资源占用由 JFR + OS 计数器在外部采集。
 *
 * 关键设计：
 *  - HttpServer 用虚拟线程池分发，隔离"HTTP 处理"与"被测工具自身的并发"，避免适配层成为瓶颈。
 *  - /api/decompile/batch 用可配置线程池并发反编译，直接验证 CFR 逐 class 起子进程的并发收益。
 *  - AI 默认走 MockAiAnalyzer（离线回放，零外网、确定性强）。
 */
public final class Harness {

    private static final Path CFR_JAR;
    private static final String JAVA_BIN;
    private static final Path AUDIT_FILE;
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * P0-2 解析并发上限（背压）：所有重负载端点都通过 {@link #parse} 解析大包，
     * 解析是 I/O + sha256 CPU 双密集；无限制并发会触发磁盘 I/O 与请求级内存争用雪崩
     * （50 并发下 parse 延迟放大 49×，100 并发放大 90×）。这里用全局信号量把"同时进行的
     * 解析数"钳制在 CPU/磁盘可承受范围，超额请求排队而非雪崩。
     * 可用 -Dbempdiff.parseConcurrency=N 覆盖；默认 = min(可用核数, 8)。
     */
    private static final int PARSE_CONCURRENCY = Math.max(1,
            Integer.getInteger("bempdiff.parseConcurrency",
                    Math.min(Runtime.getRuntime().availableProcessors(), 8)));
    private static final Semaphore PARSE_GATE = new Semaphore(PARSE_CONCURRENCY, true);

    /**
     * P1-3 并发资源：全局有界工作池（反编译/差异/AI/报告/导出共用），替代各端点"每请求
     * newFixedThreadPool"，避免高并发下线程/进程爆炸（请求数 × 每请求线程数）。
     * 队列 + CallerRunsPolicy 提供背压：池满时由调用线程（HTTP 派发线程）就地执行，
     * 而非无限堆积或拒绝。可用 -Dbempdiff.maxThreads=N 覆盖；默认 = min(核数*2, 16)。
     */
    private static final int MAX_WORK_THREADS = Math.max(2,
            Integer.getInteger("bempdiff.maxThreads",
                    Math.min(Runtime.getRuntime().availableProcessors() * 2, 16)));
    private static final ExecutorService WORK_POOL = new ThreadPoolExecutor(
            MAX_WORK_THREADS, MAX_WORK_THREADS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1024),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** P1-3：单请求整体超时（含解析+反编译+导出），超时未完成的候选被取消，防御慢请求挂死连接。 */
    private static final long REQUEST_TIMEOUT_MS = Long.getLong("bempdiff.requestTimeoutMs", 10 * 60_000L);

    static {
        // 优先用"运行 harness 的 JVM 自身的 java.home"（已是合法 Windows 路径，
        // 且保证 CFR 子进程与 harness 同 JDK）。避免 Git Bash 传入的 /d/... POSIX 风格
        // JAVA_HOME 被 java.nio.file.Paths 改坏盘符（/d/... -> \d\...，CreateProcess 找不到文件）。
        String jh = System.getProperty("java.home");
        if (jh == null || jh.isEmpty()) {
            jh = System.getenv("JAVA_HOME");
            if (jh == null || jh.isEmpty()) jh = System.getenv("BEMPDIFF_JAVA_HOME");
        }
        // 兜底：把 Git Bash 风格 /d/... 规范化为 D:/...，防止 Paths.get 吞掉盘符
        if (jh != null && jh.startsWith("/") && jh.length() >= 3
                && Character.isLetter(jh.charAt(1)) && jh.charAt(2) == '/') {
            jh = Character.toUpperCase(jh.charAt(1)) + ":" + jh.substring(2);
        }
        JAVA_BIN = (jh != null && !jh.isEmpty())
                ? Paths.get(jh, "bin", "java.exe").toString()
                : "java";
        String cfr = System.getProperty("bempdiff.cfr", "prototype/cfr.jar");
        CFR_JAR = Paths.get(cfr).toAbsolutePath();
        Path work = Paths.get(System.getProperty("bempdiff.work", ".")).toAbsolutePath();
        AUDIT_FILE = work.resolve("logs").resolve("perf-audit.log");
        try {
            Files.createDirectories(AUDIT_FILE.getParent());
        } catch (IOException ignored) {
            // 非关键
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18080;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        // P1-3：有界 HTTP 派发线程池（队列 + CallerRunsPolicy 背压），替代无界 newCachedThreadPool。
        int httpThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        server.setExecutor(new ThreadPoolExecutor(httpThreads, httpThreads,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(256),
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy()));
        server.createContext("/health", ex -> json(ex, 200, "{\"ok\":true,\"role\":\"bempdiff-perf-harness\"}"));
        server.createContext("/api/parse", Harness::apiParse);
        server.createContext("/api/diff", Harness::apiDiff);
        server.createContext("/api/decompile/batch", Harness::apiDecompileBatch);
        server.createContext("/api/ai/stageA", ex -> apiAi(ex, false));
        server.createContext("/api/ai/stageB", ex -> apiAi(ex, true));
        server.createContext("/api/report", Harness::apiReport);
        server.createContext("/api/export/classes", ex -> apiExport(ex, "classes"));
        server.createContext("/api/export/jars", ex -> apiExport(ex, "jars"));
        server.createContext("/api/export/sources", ex -> apiExport(ex, "sources"));
        server.createContext("/api/audit", Harness::apiAuditLog);
        server.createContext("/api/audit/query", Harness::apiAuditQuery);
        server.start();
        System.out.println("[Harness] listening on http://0.0.0.0:" + port
                + "  cfr=" + CFR_JAR + "  javaBin=" + JAVA_BIN
                + "  parseConcurrency=" + PARSE_CONCURRENCY);
        System.out.flush();
    }

    // ---------- 参数 / 响应 ----------
    private static Map<String, String> params(HttpExchange ex) {
        Map<String, String> m = new LinkedHashMap<>();
        String q = ex.getRequestURI().getQuery();
        if (q == null) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i < 0) { m.put(dec(kv), ""); continue; }
            m.put(dec(kv.substring(0, i)), dec(kv.substring(i + 1)));
        }
        return m;
    }

    private static String dec(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static int pi(Map<String, String> m, String k, int d) {
        String v = m.get(k);
        if (v == null) return d;
        try { return Integer.parseInt(v.trim()); } catch (Exception e) { return d; }
    }

    private static boolean pb(Map<String, String> m, String k) {
        return "true".equalsIgnoreCase(m.getOrDefault(k, "false"));
    }

    private static void json(HttpExchange ex, int code, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } catch (IOException e) {
            // 连接已断，忽略
        }
    }

    private static void fail(HttpExchange ex, String msg) {
        json(ex, 500, "{\"ok\":false,\"error\":" + jstr(msg) + "}");
    }

    private static String jstr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
                .replace("\r", " ").replace("\t", " ") + "\"";
    }

    // ---------- 工具：解析 + 差异 + 候选 ----------
    private static PackageSnapshot parse(Path p, boolean expandAll) throws IOException {
        // P0-2：全局解析并发闸门。所有端点共用，超额排队而非雪崩。
        PARSE_GATE.acquireUninterruptibly();
        try {
            return new PackageParser().parse(p, new ParseConfig(), expandAll);
        } finally {
            PARSE_GATE.release();
        }
    }

    /**
     * P1-3：在全局有界工作池 {@link #WORK_POOL} 中并发反编译候选，统一单请求超时。
     * 取代各端点"每请求 newFixedThreadPool"——高并发下总线程数被 WORK_POOL 钳制，
     * 不会随请求数线性膨胀。超时未完成的候选由 invokeAll 取消（future.get 抛 CancellationException）。
     * 返回的 Future 列表与 cands 顺序一致，端点可按下标取结果。
     */
    private static List<Future<DecompiledUnit>> decompileConcurrently(
            Decompiler dec, PackageSnapshot o, PackageSnapshot n, List<String> cands)
            throws InterruptedException {
        List<Callable<DecompiledUnit>> tasks = new ArrayList<>(cands.size());
        for (String k : cands) {
            LogicalEntry oe = o.getEntries().get(k);
            LogicalEntry ne = n.getEntries().get(k);
            tasks.add(() -> dec.decompile(o, n, oe, ne, k));
        }
        return WORK_POOL.invokeAll(tasks, REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static int layerCount(PackageSnapshot snap, Layer layer) {
        int c = 0;
        for (LogicalEntry e : snap.getEntries().values()) if (e.getLayer() == layer) c++;
        return c;
    }

    private static int sumStatus(DiffResult r, DiffStatus... sts) {
        int n = 0;
        for (DiffStatus s : sts) n += r.get(s).size();
        return n;
    }

    // ---------- 端点 ----------
    private static void apiParse(HttpExchange ex) {
        Map<String, String> m = params(ex);
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true,\"elapsedMs\":").append(ms);
            b.append(",\"oldEntries\":").append(o.getTotal());
            b.append(",\"newEntries\":").append(n.getTotal());
            b.append(",\"oldL1\":").append(layerCount(o, Layer.L1));
            b.append(",\"newL1\":").append(layerCount(n, Layer.L1));
            b.append("}");
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void apiDiff(HttpExchange ex) {
        Map<String, String> m = params(ex);
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(o, n);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true,\"elapsedMs\":").append(ms);
            b.append(",\"entries\":").append(o.getTotal());
            b.append(",\"added\":").append(r.get(DiffStatus.ADDED).size());
            b.append(",\"deleted\":").append(r.get(DiffStatus.DELETED).size());
            b.append(",\"modified\":").append(r.get(DiffStatus.MODIFIED).size());
            b.append(",\"unchanged\":").append(r.get(DiffStatus.UNCHANGED).size());
            b.append("}");
            logAudit(m);
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 并发反编译（P0 瓶颈验证点）：threads 控制线程池大小，topK 控制反编译候选数。 */
    private static void apiDecompileBatch(HttpExchange ex) {
        Map<String, String> m = params(ex);
        int threads = Math.max(1, pi(m, "threads", 1));
        int topK = Math.max(1, pi(m, "topK", 12));
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(o, n);
            List<String> cands = DiffEngine.collectL1ClassCandidates(r, o, n);
            if (cands.size() > topK) cands = cands.subList(0, topK);
            long parseMs = (System.nanoTime() - t0) / 1_000_000;

            Decompiler dec = new Decompiler(CFR_JAR, JAVA_BIN);
            List<Future<DecompiledUnit>> futures = decompileConcurrently(dec, o, n, cands);
            int ok = 0, failc = 0;
            long decompiledBytes = 0;
            for (Future<DecompiledUnit> f : futures) {
                try {
                    DecompiledUnit u = f.get();
                    if (u.isOk()) { ok++; if (u.getDiffText() != null) decompiledBytes += u.getDiffText().length(); }
                    else failc++;
                } catch (Exception e) { failc++; }
            }
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            long decompileMs = totalMs - parseMs;
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true");
            b.append(",\"threads\":").append(threads);
            b.append(",\"candidates\":").append(cands.size());
            b.append(",\"parseMs\":").append(parseMs);
            b.append(",\"decompileMs\":").append(decompileMs);
            b.append(",\"totalMs\":").append(totalMs);
            b.append(",\"ok\":").append(ok).append(",\"fail\":").append(failc);
            b.append(",\"decompiledChars\":").append(decompiledBytes);
            b.append("}");
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 两阶段 AI（MockAiAnalyzer 离线回放）。stageB=true 时额外跑阶段B深读。 */
    private static void apiAi(HttpExchange ex, boolean stageB) {
        Map<String, String> m = params(ex);
        int topK = Math.max(1, pi(m, "topK", 12));
        int threads = Math.max(1, pi(m, "threads", 1));
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(o, n);
            List<String> cands = DiffEngine.collectL1ClassCandidates(r, o, n);
            if (cands.size() > topK) cands = cands.subList(0, topK);

            Decompiler dec = new Decompiler(CFR_JAR, JAVA_BIN);
            Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
            List<Future<DecompiledUnit>> futures = decompileConcurrently(dec, o, n, cands);
            for (int i = 0; i < cands.size(); i++) {
                try { decompiled.put(cands.get(i), futures.get(i).get()); }
                catch (Exception e) { /* 失败单元忽略，保留 key 占位 */ }
            }

            Path replay = Files.createTempDirectory("bempdiff-ai-");
            AiAnalyzer analyzer = new MockAiAnalyzer(replay);
            AiConfig cfg = new AiConfig();
            cfg.setEnabled(true);
            analyzer.testConnection(cfg);
            String overallRisk = "UNKNOWN";
            int stageBCount = 0;
            if (!stageB) {
                StageASummary s = analyzer.stageA(r, decompiled, cfg);
                overallRisk = s.getOverallRisk();
            } else {
                List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
                for (String k : cands) bCands.add(new AiAnalyzer.DecompileReq(k, decompiled.get(k), FileClass.CLASS));
                if (bCands.size() > cfg.getStageBTopK()) bCands = bCands.subList(0, cfg.getStageBTopK());
                List<FileAnalysis> fa = analyzer.stageB(bCands, cfg);
                stageBCount = fa.size();
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true");
            b.append(",\"stage\":").append(stageB ? jstr("B") : jstr("A"));
            b.append(",\"elapsedMs\":").append(ms);
            b.append(",\"candidates\":").append(cands.size());
            b.append(",\"overallRisk\":").append(jstr(overallRisk));
            b.append(",\"stageBCount\":").append(stageBCount);
            b.append("}");
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void apiReport(HttpExchange ex) {
        Map<String, String> m = params(ex);
        int topK = Math.max(1, pi(m, "topK", 12));
        int threads = Math.max(1, pi(m, "threads", 1));
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(o, n);
            var s = engine.stats(r);
            List<String> cands = DiffEngine.collectL1ClassCandidates(r, o, n);
            if (cands.size() > topK) cands = cands.subList(0, topK);
            Decompiler dec = new Decompiler(CFR_JAR, JAVA_BIN);
            Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
            List<Future<DecompiledUnit>> futures = decompileConcurrently(dec, o, n, cands);
            for (int i = 0; i < cands.size(); i++) {
                try { decompiled.put(cands.get(i), futures.get(i).get()); } catch (Exception ignored) {}
            }
            Path out = Files.createTempFile("bempdiff-report-", ".md");
            new MarkdownReport(topK).writeToFile(o, n, r, s, decompiled, new LinkedHashMap<>(), out);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            long size = Files.size(out);
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true,\"elapsedMs\":").append(ms);
            b.append(",\"reportBytes\":").append(size);
            b.append(",\"reportPath\":").append(jstr(out.toString()));
            b.append("}");
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void apiExport(HttpExchange ex, String kind) {
        Map<String, String> m = params(ex);
        int topK = Math.max(1, pi(m, "topK", 12));
        int threads = Math.max(1, pi(m, "threads", 1));
        long t0 = System.nanoTime();
        try {
            PackageSnapshot o = parse(Paths.get(m.get("old")), pb(m, "expandAll"));
            PackageSnapshot n = parse(Paths.get(m.get("new")), pb(m, "expandAll"));
            DiffEngine engine = new DiffEngine();
            DiffResult r = engine.compute(o, n);
            List<String> cands = DiffEngine.collectL1ClassCandidates(r, o, n);
            if (cands.size() > topK) cands = cands.subList(0, topK);
            Decompiler dec = new Decompiler(CFR_JAR, JAVA_BIN);
            Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
            List<Future<DecompiledUnit>> futures = decompileConcurrently(dec, o, n, cands);
            for (int i = 0; i < cands.size(); i++) {
                try { decompiled.put(cands.get(i), futures.get(i).get()); } catch (Exception ignored) {}
            }
            AssetExporter exporter = new AssetExporter();
            Path outDir = Files.createTempDirectory("bempdiff-export-");
            String outPath;
            int fileCount;
            switch (kind) {
                case "jars": {
                    Path p = exporter.exportDiffJars(r, o, n, outDir);
                    outPath = p.toString();
                    fileCount = countFiles(p);
                    break;
                }
                case "sources": {
                    Path p = exporter.exportDecompiledSources(decompiled, outDir, topK);
                    outPath = p.toString();
                    fileCount = countFiles(p);
                    break;
                }
                default: {
                    Path p = exporter.exportDiffClasses(r, o, n, outDir);
                    outPath = p.toString();
                    fileCount = countFiles(p);
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            StringBuilder b = new StringBuilder();
            b.append("{\"ok\":true,\"kind\":").append(jstr(kind));
            b.append(",\"elapsedMs\":").append(ms);
            b.append(",\"fileCount\":").append(fileCount);
            b.append(",\"outPath\":").append(jstr(outPath));
            b.append("}");
            json(ex, 200, b.toString());
        } catch (Exception e) {
            fail(ex, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static int countFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (var s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile).count();
        }
    }

    // ---------- 轻量文件审计（覆盖 AuditLogger 接口；真实实现尚未合入 core） ----------
    private static void logAudit(Map<String, String> m) {
        try {
            String line = LocalDate.now().format(DF) + "\t"
                    + System.nanoTime() + "\t"
                    + m.getOrDefault("old", "?") + "\t"
                    + m.getOrDefault("new", "?") + "\t"
                    + "mock\n";
            Files.write(AUDIT_FILE, line.getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private static void apiAuditLog(HttpExchange ex) {
        logAudit(params(ex));
        json(ex, 200, "{\"ok\":true}");
    }

    private static void apiAuditQuery(HttpExchange ex) {
        Map<String, String> m = params(ex);
        String from = m.getOrDefault("from", "");
        String to = m.getOrDefault("to", "");
        try {
            List<String> lines = Files.exists(AUDIT_FILE)
                    ? Files.readAllLines(AUDIT_FILE, StandardCharsets.UTF_8)
                    : new ArrayList<>();
            int cnt = 0;
            for (String ln : lines) {
                String d = ln.split("\t", 2)[0];
                if (!from.isEmpty() && d.compareTo(from) < 0) continue;
                if (!to.isEmpty() && d.compareTo(to) > 0) continue;
                cnt++;
            }
            json(ex, 200, "{\"ok\":true,\"total\":"
                    + cnt + ",\"from\":" + jstr(from) + ",\"to\":" + jstr(to) + "}");
        } catch (Exception e) {
            fail(ex, e.getMessage());
        }
    }
}
