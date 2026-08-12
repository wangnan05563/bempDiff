package com.bempdiff;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.FrontendTextDiff;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.FolderDiff;
import com.bempdiff.model.*;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.parse.FolderParser;
import com.bempdiff.report.MarkdownReport;
import com.bempdiff.report.FolderReport;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.ai.AiAnalyzer;
import com.bempdiff.config.AiConfig;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.MockAiAnalyzer;
import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectContextAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
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
        printTree(r, oldSnap, newSnap);
    }

    /** FR：文件夹比较。与 compare 共享差异算法与树打印，仅数据源换成 FolderParser。 */
    private static void compareFolders(Path oldDir, Path newDir) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获
        FolderParser parser = new FolderParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldDir, cfg);
        PackageSnapshot newSnap = parser.parse(newDir, cfg);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);
        DiffStats s = engine.stats(r);

        System.out.println("== COMPARE FOLDERS =="); // NOSONAR
        System.out.println("old dir: " + oldDir); // NOSONAR
        System.out.println("new dir: " + newDir); // NOSONAR
        System.out.println(STATS_PREFIX + s); // NOSONAR
        printTree(r, oldSnap, newSnap);
    }

    /** FR：文件夹对比（增强版，独立引擎）。递归比对两目录，区分仅左/仅右/两侧不同(内容或属性)/相同/类型冲突，
     *  输出清晰汇总 + 展开式差异树（含属性变更与文本行级 diff）+ 可选 Markdown 报告。 */
    private static void folderDiff(Path leftDir, Path rightDir, int topK, FolderDiff.Options opts, Path reportMd) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获
        FolderDiff.FolderDiffResult r = FolderDiff.compare(leftDir, rightDir, opts);
        System.out.println("== FOLDER DIFF =="); // NOSONAR
        System.out.println("left : " + leftDir); // NOSONAR
        System.out.println("right: " + rightDir); // NOSONAR
        System.out.println("--- 汇总 ---"); // NOSONAR
        System.out.println("仅左侧存在 : " + r.summary.leftOnly); // NOSONAR
        System.out.println("仅右侧存在 : " + r.summary.rightOnly); // NOSONAR
        System.out.println("两侧不同   : " + r.summary.modified + " (内容不同=" + r.summary.contentChanged // NOSONAR
                + ", 仅属性不同=" + r.summary.attrOnlyChanged + ")"); // NOSONAR
        System.out.println("完全相同   : " + r.summary.same); // NOSONAR
        System.out.println("类型冲突   : " + r.summary.typeMismatch); // NOSONAR
        System.out.println("扫描文件/目录: " + r.summary.scannedFiles + "/" + r.summary.scannedDirs // NOSONAR
                + "  读取错误: " + r.summary.errors); // NOSONAR
        System.out.println("--- 差异树 (展开) ---"); // NOSONAR
        printFolderTree(r.roots, "");
        if (reportMd != null) {
            FolderReport.writeToFile(r, reportMd);
            System.out.println("report: " + reportMd); // NOSONAR
        }
    }

    /** 展开式差异树打印（缩进体现层级，标注状态与属性变更；文本文件内容不同则附行级 diff）。 */
    private static void printFolderTree(List<FolderDiff.FolderEntry> nodes, String indent) {
        for (FolderDiff.FolderEntry e : nodes) {
            String marker;
            String name = e.relPath.substring(e.relPath.lastIndexOf('/') + 1);
            switch (e.status) {
                case LEFT_ONLY: marker = "[<]"; break;
                case RIGHT_ONLY: marker = "[>]"; break;
                case MODIFIED: marker = "[*]"; break;
                case TYPE_MISMATCH: marker = "[!]"; break;
                default: marker = "[=]";
            }
            // 目录自身为 SAME 但子树含差异时，显式标注，避免父节点"看起来没变"
            boolean subtreeDiff = e.type == FolderDiff.EntryType.DIR
                    && e.status == FolderDiff.FolderDiffStatus.SAME && FolderDiff.subtreeHasDiff(e);
            if (subtreeDiff) marker = "[*]";
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append(marker).append(' ').append(name);
            if (e.type == FolderDiff.EntryType.DIR) sb.append('/');
            if (e.status == FolderDiff.FolderDiffStatus.MODIFIED) sb.append(' ').append(attrSummary(e));
            else if (subtreeDiff) sb.append(" (子树含差异)");
            System.out.println(sb); // NOSONAR
            if (e.lineDiff != null) {
                for (String line : e.lineDiff.split("\n", -1)) {
                    System.out.println(indent + "    " + line); // NOSONAR
                }
            }
            if (e.children != null && !e.children.isEmpty()) {
                printFolderTree(e.children, indent + "  ");
            }
        }
    }

    /** MODIFIED 条目的属性/内容变更摘要（供树与报告复用）。 */
    private static String attrSummary(FolderDiff.FolderEntry e) {
        List<String> parts = new ArrayList<>();
        if (e.attrChanges.contains(FolderDiff.AttrChange.SIZE))
            parts.add("大小 " + FolderDiff.fmtSize(e.sizeLeft) + "→" + FolderDiff.fmtSize(e.sizeRight));
        if (e.attrChanges.contains(FolderDiff.AttrChange.MTIME))
            parts.add("修改时间 " + FolderDiff.fmtMtime(e.mtimeLeft) + "→" + FolderDiff.fmtMtime(e.mtimeRight));
        if (e.attrChanges.contains(FolderDiff.AttrChange.CONTENT)) parts.add("内容不同");
        if (e.attrChanges.contains(FolderDiff.AttrChange.TYPE)) parts.add("类型冲突(文件/目录)");
        return "[" + String.join(", ", parts) + "]";
    }

    /** 统一的差异树打印（按 status 分组，缩进体现层级，标注 layer）。 */
    private static void printTree(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
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
        // 文本类文件（配置文件/JSP/JS/HTML/CSS）：压缩前端资源美化后 diff，配置文件与 JSP 换行归一后 diff
        Map<String, DecompiledUnit> fe = buildTextMap(r, oldSnap, newSnap, topK);
        int feOk = 0;
        for (Map.Entry<String, DecompiledUnit> e : fe.entrySet()) {
            DecompiledUnit u = e.getValue();
            System.out.println("----------------------------------------"); // NOSONAR
            System.out.println("KEY   : " + e.getKey() + " (文本 " + fileClassOfKey(e.getKey(), oldSnap, newSnap) + ")"); // NOSONAR
            System.out.println("ENGINE: " + u.getEngine() + "  OK=" + u.isOk()); // NOSONAR
            if (u.isOk()) {
                feOk++;
                if (u.getDiffText() != null && u.getDiffText().length() > 2000) {
                    System.out.println("DIFF  : (截断展示前 2000 字符)\n" + u.getDiffText().substring(0, 2000)); // NOSONAR
                } else {
                    System.out.println("DIFF  :\n" + u.getDiffText()); // NOSONAR
                }
            } else {
                System.out.println("ERROR : " + u.getError()); // NOSONAR
            }
        }
        System.out.println("----------------------------------------"); // NOSONAR
        System.out.println("反编译成功=" + okCnt + " / " + cands.size()
                + "  文本类文件比对=" + feOk + " / " + fe.size()); // NOSONAR
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

    /** T12/FR7：全链路 + Markdown 报告导出（真实包验证）。--ai 时加载持久化 AI 配置，运行两阶段分析并原生写回「七、AI 智能分析」章节。 */
    private static void report(Path oldP, Path newP, boolean expandAll, int topK, Path cfrJar, Path outMd, List<String> a) throws IOException { // NOSONAR - 顶层 main 方法通用异常捕获，用于统一错误处理入口
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
        Map<String, DecompiledUnit> text = buildTextMap(r, oldSnap, newSnap, topK);

        MarkdownReport rep = new MarkdownReport(topK);

        // --ai：两阶段 AI 分析并写回「七、AI 智能分析」章节（FR-CX-01），支持项目级上下文增强。
        // 有 API Key 走真实 Http 调用；无 Key 时降级为离线 Mock 回放（仍能产出 AI 章节 + 项目上下文影响说明）。
        if (a.contains("--ai")) {
            AiConfig aiCfg = loadAiConfig(a);
            Path replayDir = Paths.get(System.getProperty("user.home"), ".bempdiff", "ai_replay");
            if (a.contains("--replay")) replayDir = Paths.get(a.get(a.indexOf("--replay") + 1));
            AiAnalyzer analyzer;
            if (aiCfg.getApiKey() != null && !aiCfg.getApiKey().isEmpty()) {
                analyzer = new HttpAiAnalyzer(aiCfg);
                LOG.log(java.util.logging.Level.INFO, "[AI] 使用 HttpAiAnalyzer（真实调用，provider={0}）", aiCfg.getProvider());
            } else {
                analyzer = new MockAiAnalyzer(replayDir);
                LOG.warning("[AI] 未配置 API Key，使用 MockAiAnalyzer 离线回放生成 AI 章节（如需真实分析请在 ⚙设置 填 Key 或用 --apikey；--replay 可指定回放目录）。");
            }
            // 项目级上下文增强（可选 --project <dir>）：离线扫描工程目录，作为 AI 分析依据
            ProjectContext ctx = null;
            if (a.contains("--project")) {
                try {
                    Path projDir = Paths.get(a.get(a.indexOf("--project") + 1));
                    ctx = ProjectContextAnalyzer.analyze(projDir);
                    LOG.log(java.util.logging.Level.INFO, "[AI] 项目级上下文已加载：{0}（构建系统={1}, 模块数={2}）",
                            new Object[]{projDir, ctx.getBuildSystem(), ctx.getModules().size()});
                } catch (RuntimeException ex) {
                    LOG.log(java.util.logging.Level.WARNING, "[AI] 项目级上下文扫描失败，已忽略：{0}", ex.getMessage());
                    ctx = null;
                }
            }
            boolean conn = analyzer.testConnection(aiCfg);
            LOG.log(java.util.logging.Level.INFO, "[AI] 连接测试={0}", conn);
            Map<String, DecompiledUnit> aiMap = new LinkedHashMap<>(decompiled);
            aiMap.putAll(text);
            StageASummary summary;
            try {
                summary = analyzer.stageA(r, aiMap, aiCfg, ctx);
            } catch (RuntimeException ex) {
                LOG.log(java.util.logging.Level.WARNING, "[AI] 阶段A 调用失败，已降级：{0}", ex.getMessage());
                summary = new StageASummary();
                summary.setOverallRisk("UNKNOWN");
                summary.setImpactScope("AI 阶段A 不可用（连接失败/超时/鉴权错误），仅给出基础差异结论。错误：" + ex.getMessage());
                summary.setTestThemes(java.util.Arrays.asList("人工核对全部差异文件", "回归核心业务流程", "校验对外接口兼容性"));
                summary.setFileRisks(new ArrayList<>());
            }
            List<FileAnalysis> b = new ArrayList<>();
            try {
                List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
                for (String k : aiMap.keySet()) {
                    bCands.add(new AiAnalyzer.DecompileReq(k, aiMap.get(k), fileClassOfKey(k, oldSnap, newSnap)));
                }
                if (bCands.size() > aiCfg.getStageBTopK()) bCands = bCands.subList(0, aiCfg.getStageBTopK());
                b = analyzer.stageB(bCands, aiCfg, ctx);
            } catch (RuntimeException ex) {
                LOG.log(java.util.logging.Level.WARNING, "[AI] 阶段B 调用失败，已降级（仅保留阶段A）：{0}", ex.getMessage());
            }
            rep.writeToFile(oldSnap, newSnap, r, s, decompiled, text, summary, b, ctx, outMd);
            System.out.println("== REPORT (AI) =="); // NOSONAR
            System.out.println("项目级上下文: " + (ctx != null ? ("已启用（构建系统=" + ctx.getBuildSystem() + ", 模块数=" + ctx.getModules().size() + "）") : "未启用")); // NOSONAR
            System.out.println("out: " + outMd); // NOSONAR
            System.out.println("AI 整体风险=" + summary.getOverallRisk() + "  阶段B 深读=" + b.size() + " 文件"); // NOSONAR
            System.out.println(STATS_PREFIX + s + "  decompiled=" + decompiled.size() + "  text=" + text.size()); // NOSONAR
            return;
        }

        rep.writeToFile(oldSnap, newSnap, r, s, decompiled, text, outMd);
        System.out.println("== REPORT =="); // NOSONAR
        System.out.println("out: " + outMd); // NOSONAR
        System.out.println(STATS_PREFIX + s + "  decompiled=" + decompiled.size()
                + "  text=" + text.size()); // NOSONAR
    }

    /** 从持久化 ui-config.properties 加载 AI 配置（与 UI 同路径）；CLI --apikey/--baseurl/--provider/--model/--aiconfig 可覆盖。 */
    private static AiConfig loadAiConfig(List<String> a) {
        AiConfig c = new AiConfig();
        Path cfgPath;
        if (a.contains("--aiconfig")) {
            cfgPath = Paths.get(a.get(a.indexOf("--aiconfig") + 1));
        } else {
            cfgPath = Paths.get(System.getProperty("user.home"), ".bempdiff", "ui-config.properties");
        }
        if (Files.exists(cfgPath)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(cfgPath)) {
                p.load(in);
                c.setProvider(p.getProperty("aiProvider", c.getProvider()));
                c.setBaseUrl(p.getProperty("aiBaseUrl", c.getBaseUrl()));
                c.setApiKey(p.getProperty("aiApiKey", ""));
                c.setModel(p.getProperty("aiModel", c.getModel()));
                c.setStageBTopK(Integer.parseInt(p.getProperty("stageBTopK", "15")));
                c.setCostGateWarnTokens(Double.parseDouble(p.getProperty("costGateWarnTokens", "8000")));
                c.setHttpProxy(p.getProperty("httpProxy", ""));
                c.setHttpsProxy(p.getProperty("httpsProxy", ""));
                c.setBlockPrivateEndpoints(Boolean.parseBoolean(p.getProperty("blockPrivateEndpoints", "false")));
                c.setEnabled(true);
            } catch (IOException | NumberFormatException ex) {
                LOG.log(java.util.logging.Level.WARNING, "[AI] 读取配置文件失败，使用 CLI/默认值：{0}", ex.getMessage());
            }
        }
        // CLI 覆盖（优先级最高）
        if (a.contains("--apikey")) c.setApiKey(a.get(a.indexOf("--apikey") + 1));
        if (a.contains("--provider")) c.setProvider(a.get(a.indexOf("--provider") + 1));
        if (a.contains("--baseurl")) c.setBaseUrl(a.get(a.indexOf("--baseurl") + 1));
        if (a.contains("--model")) c.setModel(a.get(a.indexOf("--model") + 1));
        if (c.getApiKey() != null && !c.getApiKey().isEmpty()) c.setEnabled(true);
        return c;
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
        // 文本类文件（配置文件/JSP/前端 JS/HTML/CSS）一并纳入源码导出 zip（按扩展名落盘）
        Map<String, DecompiledUnit> text = buildTextMap(r, oldSnap, newSnap, topK);
        decompiled.putAll(text);

        AssetExporter exporter = new AssetExporter();
        Path classesDir = exporter.exportDiffClasses(r, oldSnap, newSnap, outDir);
        Path jarsDir = exporter.exportDiffJars(r, oldSnap, newSnap, outDir);
        Path zip = exporter.exportDecompiledSources(decompiled, outDir, topK);

        System.out.println("== EXPORT =="); // NOSONAR
        System.out.println("outDir : " + outDir); // NOSONAR
        System.out.println("classes: " + classesDir + " (" + countFiles(classesDir) + " class 文件)"); // NOSONAR
        System.out.println("jars    : " + jarsDir + " (" + countFiles(jarsDir) + " jar 文件)"); // NOSONAR
        System.out.println("src zip : " + zip + " (" + decompiled.size() + " 源码，含 " + text.size() + " 文本类)"); // NOSONAR
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
        // 文本类文件（配置文件/JSP/前端 JS/HTML/CSS）并入 stageA 摘要；stageB 深读携带 fileClass 以切换领域措辞
        Map<String, DecompiledUnit> text = buildTextMap(r, oldSnap, newSnap, topK);
        decompiled.putAll(text);

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

        // 项目级上下文增强（可选 --project <dir>）：扫描工程目录，作为 AI 分析依据
        ProjectContext ctx = null;
        if (a.contains("--project")) {
            try {
                Path projDir = Paths.get(a.get(a.indexOf("--project") + 1));
                ctx = ProjectContextAnalyzer.analyze(projDir);
                LOG.log(java.util.logging.Level.INFO, "[AI] 项目级上下文已加载：{0}（构建系统={1}, 模块数={2}）",
                        new Object[]{projDir, ctx.getBuildSystem(), ctx.getModules().size()});
            } catch (RuntimeException ex) {
                LOG.log(java.util.logging.Level.WARNING, "[AI] 项目级上下文扫描失败，已忽略：{0}", ex.getMessage());
                ctx = null;
            }
        }

        // 连接测试（FR9.4 设置弹窗「测试」按钮对应）
        boolean conn = analyzer.testConnection(aiCfg);
        LOG.log(java.util.logging.Level.INFO, "[AI] 连接测试={0}", conn);

        // 阶段A
        String stageAPrompt = analyzer.buildStageAPrompt(r, decompiled, aiCfg, ctx);
        double stageATokens = analyzer.estimateTokens(stageAPrompt);
        boolean gateWarn = stageATokens > aiCfg.getCostGateWarnTokens();
        StageASummary summary;
        List<FileAnalysis> b;
        try {
            summary = analyzer.stageA(r, decompiled, aiCfg, ctx);

            // 阶段B：仅对前 topK 候选深读（含文本类文件，携带 fileClass 以切换领域措辞）
            List<AiAnalyzer.DecompileReq> bCands = new ArrayList<>();
            for (String k : decompiled.keySet()) {
                bCands.add(new AiAnalyzer.DecompileReq(k, decompiled.get(k), fileClassOfKey(k, oldSnap, newSnap)));
            }
            // 成本闸门：阶段B 仅对前 aiCfg.stageBTopK 深读
            if (bCands.size() > aiCfg.getStageBTopK()) bCands = bCands.subList(0, aiCfg.getStageBTopK());
            b = analyzer.stageB(bCands, aiCfg, ctx);
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
        System.out.println("项目级上下文: " + (ctx != null ? ("已启用（构建系统=" + ctx.getBuildSystem() + ", 模块数=" + ctx.getModules().size() + "）") : "未启用")); // NOSONAR
        System.out.println("阶段A: overallRisk=" + summary.getOverallRisk() + " 影响=" + summary.getImpactScope()); // NOSONAR
        if (ctx != null && summary.getContextInfluence() != null && !summary.getContextInfluence().isEmpty()) {
            System.out.println("阶段A 上下文影响: " + summary.getContextInfluence()); // NOSONAR
        }
        System.out.println("阶段A: 测试主题=" + summary.getTestThemes()); // NOSONAR
        System.out.println("阶段A prompt tokens≈" + (long) stageATokens + " 成本闸门触发=" + gateWarn + " (阈值" + aiCfg.getCostGateWarnTokens() + ")"); // NOSONAR
        System.out.println("阶段B: 深读文件数=" + b.size() + " / 候选=" + cands.size()); // NOSONAR
        for (FileAnalysis fa : b) {
            StringBuilder line = new StringBuilder();
            line.append("  - ").append(fa.getKey()).append(" | risk=").append(fa.getRisk()).append(" | intent=").append(fa.getIntent());
            if (fa.getTestPoints() != null && !fa.getTestPoints().isEmpty()) {
                line.append(" | 测试要点=").append(String.join("；", fa.getTestPoints()));
            }
            if (ctx != null && fa.getContextInfluence() != null && !fa.getContextInfluence().isEmpty()) {
                line.append(" | 上下文影响=").append(fa.getContextInfluence());
            }
            System.out.println(line.toString()); // NOSONAR
        }
        System.out.println("prompt 已落地: " + replayDir); // NOSONAR
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    /** 构建文本类文件内容 diff 集合（CONFIG/JSP/JS/HTML/CSS）：配置文件与 JSP 直接换行归一后 diff，
     *  压缩前端资源先美化再 diff（FR4.4 增强 + 本需求扩展）。 */
    private static Map<String, DecompiledUnit> buildTextMap(DiffResult r, PackageSnapshot oldSnap,
                                                            PackageSnapshot newSnap, int topK) {
        Map<String, DecompiledUnit> m = new LinkedHashMap<>();
        FrontendTextDiff ftd = new FrontendTextDiff();
        List<String> cands = DiffEngine.collectTextDiffCandidates(r, oldSnap, newSnap);
        int limit = Math.min(cands.size(), topK);
        for (int i = 0; i < limit; i++) {
            String k = cands.get(i);
            LogicalEntry oe = oldSnap.getEntries().get(k);
            LogicalEntry ne = newSnap.getEntries().get(k);
            FileClass fc = (ne != null) ? ne.getFileClass() : (oe != null ? oe.getFileClass() : FileClass.JS);
            m.put(k, ftd.diff(oldSnap, newSnap, oe, ne, k, fc));
        }
        return m;
    }

    private static FileClass fileClassOfKey(String k, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        LogicalEntry e = oldSnap.getEntries().get(k);
        if (e == null) e = newSnap.getEntries().get(k);
        return (e != null) ? e.getFileClass() : FileClass.CLASS;
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
        int maxDepth = -1;
        if (a.contains("--max-depth")) maxDepth = Integer.parseInt(a.get(a.indexOf("--max-depth") + 1));
        FolderDiff.Options fopts = FolderDiff.Options.defaults();
        fopts.maxDepth = maxDepth;
        Path folderReport = a.contains("--report") ? Paths.get(a.get(a.indexOf("--report") + 1)) : null;

        switch (cmd) {
            case "inspect":
                requireArgs(args, 2, cmd);
                inspect(Paths.get(args[1]));
                break;
            case "compare":
                requireArgs(args, 3, cmd);
                compare(Paths.get(args[1]), Paths.get(args[2]), expandAll);
                break;
            case "compare-folders":
                requireArgs(args, 3, cmd);
                folderDiff(Paths.get(args[1]), Paths.get(args[2]), topK, fopts, null);
                break;
            case "folderdiff":
                requireArgs(args, 3, cmd);
                folderDiff(Paths.get(args[1]), Paths.get(args[2]), topK, fopts, folderReport);
                break;
            case "decompile":
                requireArgs(args, 3, cmd);
                decompile(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar);
                break;
            case "report":
                requireArgs(args, 3, cmd);
                report(Paths.get(args[1]), Paths.get(args[2]), expandAll, topK, cfrJar,
                        outPath(a, "--out", "bempdiff-report.md"), a);
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
        System.out.println("  compare-folders <leftDir> <rightDir>             (文件夹对比，基础版)"); // NOSONAR
        System.out.println("  folderdiff <leftDir> <rightDir> [--report <md>] [--max-depth N]"); // NOSONAR
        System.out.println("  decompile <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>]"); // NOSONAR
        System.out.println("  report  <old> <new> [--expand-all] [--top-k N] [--cfr <cfr.jar>] [--out <md>] [--ai] [--apikey KEY] [--baseurl URL] [--provider P] [--model M] [--aiconfig <path>] [--project <dir>] [--replay <dir>]"); // NOSONAR
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