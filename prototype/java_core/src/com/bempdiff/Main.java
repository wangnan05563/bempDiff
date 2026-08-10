package com.bempdiff;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.model.*;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.report.MarkdownReport;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.config.AiConfig;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.HttpAiAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Java 移植端口验证驱动（不依赖 JavaFX）。
 * 用法：
 *   java -cp out com.bempdiff.Main inspect <pkg>
 *   java -cp out com.bempdiff.Main compare <old> <new> [--expand-all]
 *   java -cp out com.bempdiff.Main decompile <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>]
 */
public final class Main {

    private static final String STATS_PREFIX = "stats: ";
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(Main.class.getName());

    private static int layerCount(Map<String, LogicalEntry> entries, Layer layer) {
        int c = 0;
        for (LogicalEntry e : entries.values()) if (e.getLayer() == layer) c++;
        return c;
    }

    private static void inspect(Path p) throws IOException {
        PackageParser parser = new PackageParser();
        PackageSnapshot snap = parser.parse(p, new ParseConfig(), false);
        System.out.println("== INSPECT =="); // NOSONAR - CLI 工具的标准输出
        System.out.println("file   : " + p.getFileName()); // NOSONAR
        System.out.println("type   : " + snap.getType()); // NOSONAR
        System.out.println("version: " + snap.getVersion()); // NOSONAR
        System.out.println("total  : " + snap.getTotal()); // NOSONAR
        System.out.println("L0     : " + layerCount(snap.getEntries(), Layer.L0)); // NOSONAR
        System.out.println("L1     : " + layerCount(snap.getEntries(), Layer.L1)); // NOSONAR
        System.out.println("L2     : " + layerCount(snap.getEntries(), Layer.L2)); // NOSONAR
    }

    private static void compare(Path oldP, Path newP, boolean expandAll) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
        PackageParser parser = new PackageParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldP, cfg, expandAll);
        PackageSnapshot newSnap = parser.parse(newP, cfg, expandAll);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);
        DiffStats s = engine.stats(r);

        System.out.println("== COMPARE =="); // NOSONAR
        System.out.println("old: " + oldP.getFileName() + "  version=" + oldSnap.getVersion()); // NOSONAR
        System.out.println("new: " + newP.getFileName() + "  version=" + newSnap.getVersion()); // NOSONAR
        System.out.println(STATS_PREFIX + s); // NOSONAR
        System.out.println("--- tree (status / layer) ---"); // NOSONAR
        DiffStatus[] order = {DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.UNCHANGED};
        for (DiffStatus st : order) {
            for (String k : r.get(st)) {
                LogicalEntry e = oldSnap.getEntries().get(k);
                if (e == null) e = newSnap.getEntries().get(k);
                Layer lay = e == null ? null : e.getLayer();
                String layName = lay == null ? "?" : lay.name();
                String indent = repeat("  ", k.split("/").length - 1);
                System.out.println(indent + st + " [" + layName + "] " + k); // NOSONAR
            }
        }
    }

    /** T07/T08：对 L1 修改/新增/删除类反编译并双栏源码 diff（真实包验证） */
    private static void decompile(Path oldP, Path newP, boolean expandAll, int topK, Path cfrJar) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
        PackageParser parser = new PackageParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldP, cfg, expandAll);
        PackageSnapshot newSnap = parser.parse(newP, cfg, expandAll);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);

        String javaBin = findJava();
        Decompiler dec = new Decompiler(cfrJar, javaBin);

        // 候选：L1 的 修改/新增/删除 class（前 topK）
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, oldSnap, newSnap);
        int skipped = Math.max(0, cands.size() - topK);
        if (cands.size() > topK) cands = cands.subList(0, topK);

        System.out.println("== DECOMPILE =="); // NOSONAR
        System.out.println("old: " + oldP.getFileName() + "  new: " + newP.getFileName()); // NOSONAR
        System.out.println("L1 class 候选=" + (cands.size() + skipped) + " 反编译=" + cands.size() + " 跳过=" + skipped + " (topK=" + topK + ")"); // NOSONAR
        int okCnt = 0;
        for (String k : cands) {
            LogicalEntry oe = oldSnap.getEntries().get(k);
            LogicalEntry ne = newSnap.getEntries().get(k);
            DecompiledUnit u = dec.decompile(oldSnap, newSnap, oe, ne, k);
            System.out.println("----------------------------------------"); // NOSONAR
            System.out.println("KEY   : " + k); // NOSONAR
            System.out.println("ENGINE: " + u.getEngine() + "  OK=" + u.isOk()); // NOSONAR
            if (!u.isOk()) {
                System.out.println("ERROR : " + u.getError()); // NOSONAR
                continue;
            }
            okCnt++;
            if (u.getDiffText() != null && u.getDiffText().length() > 2000) {
                System.out.println("DIFF  : (截断展示前 2000 字符)\n" + u.getDiffText().substring(0, 2000)); // NOSONAR
            } else {
                System.out.println("DIFF  :\n" + u.getDiffText()); // NOSONAR
            }
        }
        System.out.println("----------------------------------------"); // NOSONAR
        System.out.println("反编译成功=" + okCnt + " / " + cands.size()); // NOSONAR
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

    /** T12/FR7：全链路 + Markdown 报告导出（真实包验证） */
    private static void report(Path oldP, Path newP, boolean expandAll, int topK, Path cfrJar, Path outMd) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
        PackageParser parser = new PackageParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldP, cfg, expandAll);
        PackageSnapshot newSnap = parser.parse(newP, cfg, expandAll);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);
        DiffStats s = engine.stats(r);

        Decompiler dec = new Decompiler(cfrJar, findJava());
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, oldSnap, newSnap);
        if (cands.size() > topK) cands = cands.subList(0, topK);
        for (String k : cands) {
            decompiled.put(k, dec.decompile(oldSnap, newSnap,
                    oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
        }

        MarkdownReport rep = new MarkdownReport(topK);
        rep.writeToFile(oldSnap, newSnap, r, s, decompiled, outMd);
        System.out.println("== REPORT =="); // NOSONAR
        System.out.println("out: " + outMd); // NOSONAR
        System.out.println(STATS_PREFIX + s + "  decompiled=" + decompiled.size()); // NOSONAR
    }

    /** T13/FR6：全链路 + 差异资产导出（真实包验证） */
    private static void export(Path oldP, Path newP, boolean expandAll, int topK, Path cfrJar, Path outDir) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
        PackageParser parser = new PackageParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldP, cfg, expandAll);
        PackageSnapshot newSnap = parser.parse(newP, cfg, expandAll);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);
        DiffStats s = engine.stats(r);

        Decompiler dec = new Decompiler(cfrJar, findJava());
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, oldSnap, newSnap);
        if (cands.size() > topK) cands = cands.subList(0, topK);
        for (String k : cands) {
            decompiled.put(k, dec.decompile(oldSnap, newSnap,
                    oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
        }

        AssetExporter exporter = new AssetExporter();
        Path classesDir = exporter.exportDiffClasses(r, oldSnap, newSnap, outDir);
        Path jarsDir = exporter.exportDiffJars(r, oldSnap, newSnap, outDir);
        Path zip = exporter.exportDecompiledSources(decompiled, outDir, topK);

        System.out.println("== EXPORT =="); // NOSONAR
        System.out.println("outDir : " + outDir); // NOSONAR
        System.out.println("classes: " + classesDir + " (" + countFiles(classesDir) + " class 文件)"); // NOSONAR
        System.out.println("jars    : " + jarsDir + " (" + countFiles(jarsDir) + " jar 文件)"); // NOSONAR
        System.out.println("src zip : " + zip + " (" + decompiled.size() + " 源码)"); // NOSONAR
        System.out.println(STATS_PREFIX + s); // NOSONAR
    }

    private static long countFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    /** T09/T10/T11：全链路 + 两阶段 AI 分析（离线回放，不触网、无需 Key） */
    private static void ai(Path oldP, Path newP, boolean expandAll, int topK, Path cfrJar, Path replayDir, List<String> a) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
        PackageParser parser = new PackageParser();
        ParseConfig cfgP = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldP, cfgP, expandAll);
        PackageSnapshot newSnap = parser.parse(newP, cfgP, expandAll);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);

        Decompiler dec = new Decompiler(cfrJar, findJava());
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, oldSnap, newSnap);
        if (cands.size() > topK) cands = cands.subList(0, topK);
        for (String k : cands) {
            decompiled.put(k, dec.decompile(oldSnap, newSnap,
                    oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
        }

        AiConfig aiCfg = new AiConfig();
        aiCfg.setEnabled(true);
        // 公网模型模式（触发脱敏）；本地 ollama 时把 provider 改为 "ollama"
        aiCfg.setProvider("openai");
        // 有真实 API Key -> 用 HttpAiAnalyzer（真实调用）；否则离线回放 Mock
        AiAnalyzer analyzer;
        if (a.contains("--apikey")) {
            aiCfg.setApiKey(a.get(a.indexOf("--apikey") + 1));
            if (a.contains("--provider")) aiCfg.setProvider(a.get(a.indexOf("--provider") + 1));
            if (a.contains("--baseurl")) aiCfg.setBaseUrl(a.get(a.indexOf("--baseurl") + 1));
            analyzer = new HttpAiAnalyzer(aiCfg);
            LOG.log(java.util.logging.Level.INFO, "[AI] 使用 HttpAiAnalyzer（真实调用，provider={0}）", aiCfg.getProvider());
        } else {
            analyzer = new MockAiAnalyzer(replayDir);
            LOG.info("[AI] 使用 MockAiAnalyzer（离线回放，--apikey 未提供）");
        }

        // 连接测试（FR9.4 设置弹窗「测试」按钮对应）
        boolean conn = analyzer.testConnection(aiCfg);
        LOG.log(java.util.logging.Level.INFO, "[AI] 连接测试={0}", conn);

        // 阶段A
        String stageAPrompt = analyzer.buildStageAPrompt(r, decompiled, aiCfg);
        double stageATokens = analyzer.estimateTokens(stageAPrompt);
        boolean gateWarn = stageATokens > aiCfg.getCostGateWarnTokens();
        StageASummary summary;
        List<FileAnalysis> b;
        try {
            summary = analyzer.stageA(r, decompiled, aiCfg);

            // 阶段B：仅对 high/medium（离线兜底统一 MEDIUM）或前 topK 候选深读
            List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
            for (String k : cands) bCands.add(new AiAnalyzer.DecompileReq(k, decompiled.get(k)));
            // 成本闸门：阶段B 仅对前 aiCfg.stageBTopK 深读
            if (bCands.size() > aiCfg.getStageBTopK()) bCands = bCands.subList(0, aiCfg.getStageBTopK());
            b = analyzer.stageB(bCands, aiCfg);
        } catch (RuntimeException ex) {
            // 优雅降级（需求：AI 不可用时基础比对仍可用）：不崩溃，给兜底结论
            LOG.log(java.util.logging.Level.WARNING, "[AI] 分析调用失败，已降级为基础结论：{0}", ex.getMessage());
            summary = new StageASummary();
            summary.setOverallRisk("UNKNOWN");
            summary.setImpactScope("AI 服务不可用（连接失败/超时/鉴权错误），本次仅给出基础差异结论，未做智能分析。错误：" + ex.getMessage());
            summary.setTestThemes(java.util.Arrays.asList("人工核对全部差异文件", "回归核心业务流程", "校验对外接口兼容性"));
            summary.setFileRisks(new ArrayList<>());
            b = new ArrayList<>();
        }

        System.out.println("== AI (两阶段 / 离线回放) =="); // NOSONAR
        System.out.println("阶段A: overallRisk=" + summary.getOverallRisk() + " 影响=" + summary.getImpactScope()); // NOSONAR
        System.out.println("阶段A: 测试主题=" + summary.getTestThemes()); // NOSONAR
        System.out.println("阶段A prompt tokens≈" + (long) stageATokens + " 成本闸门触发=" + gateWarn + " (阈值" + aiCfg.getCostGateWarnTokens() + ")"); // NOSONAR
        System.out.println("阶段B: 深读文件数=" + b.size() + " / 候选=" + cands.size()); // NOSONAR
        for (FileAnalysis fa : b) {
            System.out.println("  - " + fa.getKey() + " | risk=" + fa.getRisk() + " | intent=" + fa.getIntent()); // NOSONAR
        }
        System.out.println("prompt 已落地: " + replayDir); // NOSONAR
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
        }
        List<String> a = Arrays.asList(args);
        String cmd = args[0];
        boolean expandAll = a.contains("--expand-all");
        int topK = 12;
        if (a.contains("--top-k")) topK = Integer.parseInt(a.get(a.indexOf("--top-k") + 1));
        Path cfrJar = null;
        if (a.contains("--cfr")) cfrJar = Paths.get(a.get(a.indexOf("--cfr") + 1));

        switch (cmd) {
            case "inspect":
                requireArgs(args, 2, cmd);
                inspect(Paths.get(args[1]));
                break;
            case "compare":
                requireArgs(args, 3, cmd);
                compare(Paths.get(args[1]), Paths.get(args[2]), expandAll);
                break;
            case "decompile":
                requireArgs(args, 3, cmd);
                decompile(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar);
                break;
            case "report":
                requireArgs(args, 3, cmd);
                report(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar,
                        outPath(a, "--out", "bempdiff-report.md"));
                break;
            case "export":
                requireArgs(args, 3, cmd);
                export(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar,
                        outPath(a, "--out", "bempdiff-export"));
                break;
            case "ai":
                requireArgs(args, 3, cmd);
                ai(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar,
                        outPath(a, "--replay", "bempdiff-ai-replay"), a);
                break;
            default:
                System.out.println("unknown subcommand: " + cmd); // NOSONAR
                printUsage();
                System.exit(2);
        }
    }

    private static void printUsage() {
        System.out.println("usage:"); // NOSONAR
        System.out.println("  inspect <pkg>"); // NOSONAR
        System.out.println("  compare <old> <new> [--expand-all]"); // NOSONAR
        System.out.println("  decompile <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>]"); // NOSONAR
        System.out.println("  report  <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <md>]"); // NOSONAR
        System.out.println("  export  <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <dir>]"); // NOSONAR
        System.out.println("  ai      <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--replay <dir>] [--apikey KEY] [--baseurl URL] [--provider P]"); // NOSONAR
        System.out.println("（省略 --out/--replay 时输出到当前目录的相对路径）"); // NOSONAR
    }

    /** 子命令参数个数校验：不足则打印用法并以 exit 2 退出，避免 ArrayIndexOutOfBounds 崩溃。 */
    private static void requireArgs(String[] args, int need, String cmd) {
        if (args.length < need) {
            System.out.println("错误: " + cmd + " 需要至少 " + (need - 1) + " 个位置参数，实际 " + (args.length - 1)); // NOSONAR
            printUsage();
            System.exit(2);
        }
    }

    /** 取 --flag 后的路径；未提供则用相对默认名（避免硬编码开发机绝对路径，适配分发后的 exe）。 */
    private static Path outPath(List<String> a, String flag, String def) {
        return Paths.get(a.contains(flag) ? a.get(a.indexOf(flag) + 1) : def);
    }
}