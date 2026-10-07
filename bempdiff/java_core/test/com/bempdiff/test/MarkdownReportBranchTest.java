package com.bempdiff.test;

import com.bempdiff.ai.CategoryConclusion;
import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.FileRisk;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.diff.LibJarDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.report.MarkdownReport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MarkdownReport 剩余分支补测：差异依赖 JAR 章节（含 Top-K 截断、失败 jar、单元失败/未变收尾计数）、
 * 空 JAR 结果占位、聚焦报告（renderFocusReport 标题派生与清单空态）、单文件总结（renderSingleFile
 * 各分支 + 竖线转义）、按文件类型分布的新/旧侧回退与 OTHER 兜底、AI 章节的每文件初评/截断提示/
 * 上下文压缩(joinCapped/appendBudgeted)/分类结论专项与整体空态。
 */
public final class MarkdownReportBranchTest {

    private static final Map<String, DecompiledUnit> EMPTY = new LinkedHashMap<>();

    private static PackageSnapshot snapEmpty(String version) {
        return new PackageSnapshot(Path.of("dummy-" + version + ".war"), null, version, new LinkedHashMap<>());
    }

    private static PackageSnapshot snapWith(String version, LogicalEntry... es) {
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        for (LogicalEntry e : es) m.put(e.getKey(), e);
        return new PackageSnapshot(Path.of("dummy-" + version + ".war"), null, version, m);
    }

    private static LogicalEntry le(String key, FileClass fc) {
        return new LogicalEntry(key, Layer.L0, fc, 1, "sha-" + key, new EntrySource(key, null));
    }

    private static DiffResult buildDiff() {
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "a.class");
        r.put(DiffStatus.ADDED, "b.class");
        r.put(DiffStatus.DELETED, "c.class");
        return r;
    }

    private static DiffStats buildStats() {
        DiffStats s = new DiffStats();
        s.setAdded(1);
        s.setDeleted(1);
        s.setModified(1);
        s.setUnchanged(0);
        s.setBizChanged(3);
        s.setJarChanged(0);
        return s;
    }

    private static DecompiledUnit okUnit(String key) {
        return new DecompiledUnit(key, "old\n", "new\n", " old\n+new\n", "cfr", "", true);
    }

    private static DecompiledUnit badUnit(String key) {
        return new DecompiledUnit(key, null, null, "", "cfr", "反编译炸了", false);
    }

    // --------------------------- 差异依赖 JAR 章节 ---------------------------

    public void testRender_libJarSections_coversAllBranches() {
        String jar1Key = "WEB-INF/lib/failed.jar";
        String jar2Key = "WEB-INF/lib/dep.jar";

        LibJarDiff.DiffJarInfo failedJar = new LibJarDiff.DiffJarInfo(
                jar1Key, DiffStatus.ADDED, 0, 0, 0, 0,
                Collections.emptyList(), true, "枚举 lib jar 内部 class 失败 boom");

        List<LibJarDiff.LibClassUnit> classes = new ArrayList<>(Arrays.asList(
                new LibJarDiff.LibClassUnit("com/x/Ok.class", DiffStatus.MODIFIED, okUnit("com/x/Ok.class")),
                new LibJarDiff.LibClassUnit("com/x/Ok2.class", DiffStatus.ADDED, okUnit("com/x/Ok2.class")),
                new LibJarDiff.LibClassUnit("com/x/Bad.class", DiffStatus.MODIFIED, badUnit("com/x/Bad.class")),
                new LibJarDiff.LibClassUnit("com/x/Null.class", DiffStatus.ADDED, null),
                new LibJarDiff.LibClassUnit("com/x/Same.class", DiffStatus.UNCHANGED, null)));
        LibJarDiff.DiffJarInfo depJar = new LibJarDiff.DiffJarInfo(
                jar2Key, DiffStatus.MODIFIED, 1, 0, 2, 1, classes);

        List<LibJarDiff.DiffJarInfo> jars = new ArrayList<>(Arrays.asList(failedJar, depJar));
        LibJarDiff.Result result = new LibJarDiff.Result(jars, 2, 2, 0, 3, 1);

        MarkdownReport rep = new MarkdownReport(1); // Top-K=1：仅展开首个 ok class
        String md = rep.render(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(),
                EMPTY, EMPTY, result);

        Asserts.assertContains("JAR 章节标题", md, "## 五、差异依赖 JAR 内部源码对比");
        Asserts.assertContains("总览计数", md, "个差异 JAR（新增 1 / 修改 1 / 删除 0）");
        // 失败 jar：标题 + [分析失败] + 错误原因
        Asserts.assertContains("失败 jar 键", md, jar1Key);
        Asserts.assertContains("失败 jar 标注", md, "  [分析失败]");
        Asserts.assertContains("失败 jar 错误", md, "枚举 lib jar 内部 class 失败 boom");
        // 正常 jar：状态标签 + 内部 class 统计行
        Asserts.assertContains("依赖 jar 键", md, jar2Key);
        Asserts.assertContains("依赖 jar 状态标签", md, "  [修改 ~]");
        Asserts.assertContains("内部 class 统计行", md, "内部 class：新增 **1** · 删除 **0** · 修改 **2** · 未变 1");
        // Top-K=1：首个 ok class 展开为源码小节，第二个 ok class 被截断
        Asserts.assertContains("展开的 class 小节", md, "#### com/x/Ok.class");
        Asserts.assertContains("引擎标注", md, "- 反编译引擎：cfr");
        Asserts.assertNotContains("超 Top-K 的 Ok2 不展开", md, "com/x/Ok2.class");
        // 反编译失败单元行 + 未变/超 Top-K 收尾计数
        Asserts.assertContains("失败 class 行", md, "`com/x/Bad.class`");
        Asserts.assertContains("另有收尾段", md, "另有 ");
        Asserts.assertContains("超 Top-K 未展开源码计数", md, "超出全局 Top-K 未展开源码");
        Asserts.assertContains("未变更折叠计数", md, "未变更（已折叠，无需反编译）");
        Asserts.assertContains("反编译失败计数", md, "个反编译失败");
    }

    public void testRender_libJarEmptyResult_placeholder() {
        LibJarDiff.Result empty = new LibJarDiff.Result(new ArrayList<>(), 0, 0, 0, 0, 0);
        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.render(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(),
                EMPTY, EMPTY, empty);
        Asserts.assertContains("JAR 章节标题", md, "## 五、差异依赖 JAR");
        Asserts.assertContains("空 JAR 占位", md, "无差异依赖 JAR（本次比对未检出 JAR 级依赖变动）");
    }

    // --------------------------- 聚焦报告 renderFocusReport ---------------------------

    public void testRenderFocusReport_withFocusAndChanges() {
        MarkdownReport rep = new MarkdownReport(15).setAiFocus("测试要点分析");
        String md = rep.renderFocusReport(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(),
                null, null, null);
        Asserts.assertContains("聚焦派生标题", md, "# 测试要点分析报告");
        Asserts.assertContains("老包行", md, "- 老包：");
        Asserts.assertContains("差异规模行", md, "- 差异规模：新增 **1** · 删除 **1** · 修改 **1**");
        Asserts.assertContains("变更文件清单小节", md, "#### 变更文件清单");
        Asserts.assertContains("清单含文件", md, "a.class");
        // null summary/ctx → AI 章节回落未启用提示
        Asserts.assertContains("未启用提示", md, "未启用 AI 分析");
    }

    public void testRenderFocusReport_titleVariants() {
        // focus 已以「报告」结尾 → 原样；focus 空/null → 通用标题。
        MarkdownReport a = new MarkdownReport(15).setAiFocus("破坏性变更报告");
        String ma = a.renderFocusReport(snapEmpty("1.0"), snapEmpty("2.0"), new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>()),
                buildStats(), null, null, null);
        Asserts.assertContains("已含报告后缀原样", ma, "# 破坏性变更报告");
        Asserts.assertContains("空清单占位", ma, "（无文件变更清单）");

        MarkdownReport b = new MarkdownReport(15); // aiFocus null
        String mb = b.renderFocusReport(snapEmpty("1.0"), snapEmpty("2.0"), new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>()),
                buildStats(), null, null, null);
        Asserts.assertContains("无聚焦回落通用标题", mb, "# AI 智能分析报告");
    }

    // --------------------------- 单文件总结 renderSingleFile ---------------------------

    public void testRenderSingleFile_withAnalysisAndContext() {
        FileAnalysis fa = new FileAnalysis("k", "调整逻辑", "HIGH", "仅计费",
                Arrays.asList("回归调用方", "校验回滚"), "该模块被事务包裹");
        fa.setOutputTruncated(true);
        ProjectContext ctx = new ProjectContext("C:/p", "Maven", Arrays.asList("core"),
                Arrays.asList("spring"), Arrays.asList("App"), Arrays.asList("yml"),
                Arrays.asList("Java"), Arrays.asList("包根 com.x"), "多模块");
        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.renderSingleFile("dir/a|b.class", DiffStatus.MODIFIED, FileClass.CLASS,
                okUnit("dir/a|b.class"), fa, ctx);
        Asserts.assertContains("标题", md, "# 单文件 AI 功能总结");
        Asserts.assertContains("竖线应转义", md, "dir/a\\|b.class");
        Asserts.assertContains("改动意图", md, "调整逻辑");
        Asserts.assertContains("风险等级", md, "**HIGH**");
        Asserts.assertContains("测试要点", md, "回归调用方");
        Asserts.assertContains("截断提示", md, "本结论由被截断的 AI 输出解析而来");
        Asserts.assertContains("上下文影响", md, "项目上下文影响");
        Asserts.assertContains("项目上下文小节", md, "### 项目级上下文（分析依据）");
        Asserts.assertContains("ok 单元渲染 diff", md, "```diff");
    }

    public void testRenderSingleFile_nullFa_and_failedUnit() {
        MarkdownReport rep = new MarkdownReport(15);
        // fa=null → 无分析结论占位；unit 非 ok → 内容提取失败行。
        String md = rep.renderSingleFile("x.class", DiffStatus.ADDED, FileClass.CLASS,
                badUnit("x.class"), null, null);
        Asserts.assertContains("无 AI 结论占位", md, "（AI 未返回该文件的分析结论）");
        Asserts.assertContains("提取失败提示", md, "内容提取失败");
        Asserts.assertContains("失败原因", md, "反编译炸了");
        Asserts.assertNotContains("失败单元不渲染 diff 围栏", md, "```diff");
    }

    // --------------------------- 按文件类型分布：新侧回退与 OTHER 兜底 ---------------------------

    public void testRenderTypeBreakdown_newSideFallbackAndOther() {
        PackageSnapshot old = snapWith("1.0", le("d.class", FileClass.CLASS));               // 修改项在旧侧
        PackageSnapshot nu = snapWith("2.0", le("conf/a.xml", FileClass.CONFIG));            // 新增项仅新侧
        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "d.class");     // 旧侧 CLASS
        r.put(DiffStatus.ADDED, "conf/a.xml");     // 旧侧查不到 → 新侧 CONFIG
        r.put(DiffStatus.DELETED, "gone.js");      // 两侧都查不到 → OTHER 兜底
        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.render(old, nu, r, buildStats(), EMPTY, EMPTY);
        Asserts.assertContains("类型分布小节", md, "### 按文件类型分布");
        Asserts.assertContains("合计行", md, "**合计**");
        Asserts.assertContains("Java 类行", md, "Java 类");
        Asserts.assertContains("配置文件行", md, "配置文件(XML/Properties)");
        Asserts.assertContains("OTHER 兜底行", md, "其他");
    }

    // --------------------------- AI 章节：初评/截断/上下文压缩/分类结论 ---------------------------

    public void testRender_aiSection_fileRisks_truncation_and_contextCompression() {
        StageASummary s = new StageASummary();
        s.setOverallRisk("HIGH");
        s.setCategory("risk");
        s.setImpactScope("计费域");
        s.setContextInfluence("受 spring 事务约束");
        s.setTestThemes(Arrays.asList("回归计费", "校验回滚"));
        s.setOutputTruncated(true);
        s.setFileRisks(Arrays.asList(
                new FileRisk("com/x/A.class", "HIGH", "对外契约变更"),
                new FileRisk("com/x/B.class", "LOW", "仅内部")));
        CategoryConclusion c = new CategoryConclusion();
        c.setRiskRationale("涉及对外契约");
        c.setRollbackPlan("可回滚");
        // breakingChanges/testPoints 保持空 → 整体分支 renderXxxIfAny 跳过（不出占位）
        s.setConclusion(c);

        List<String> sixModules = Arrays.asList("M1", "M2", "M3", "M4", "M5", "M6");
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 60; i++) huge.append("依赖项").append(i);
        ProjectContext ctx = new ProjectContext("C:/proj", "Maven", sixModules,
                Arrays.asList(huge.toString()), Arrays.asList("App"), Arrays.asList("yml"),
                Arrays.asList("Java"), Arrays.asList("包根"),
                "这是一个非常长的架构叙述，用于触发上下文小节的预算裁剪逻辑，需要超过一百多个字符以验证截断分支被正确走到，避免超长文本挤占整份报告的展示篇幅与结构化关键字段的预算分配。");

        FileAnalysis fa = new FileAnalysis("com/x/A.class", "调整", "HIGH", "影响",
                Arrays.asList("tp1"), "上下文影响");
        fa.setOutputTruncated(true);

        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.render(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(),
                EMPTY, EMPTY, s, Arrays.asList(fa), ctx);

        Asserts.assertContains("阶段A 小节", md, "### 阶段A 概览");
        Asserts.assertContains("概览截断提示", md, "本结论由被截断的 AI 输出解析而来");
        Asserts.assertContains("每文件初评", md, "每文件初评风险");
        Asserts.assertContains("初评键", md, "com/x/A.class");
        Asserts.assertContains("测试主题", md, "测试主题：");
        Asserts.assertContains("整体风险结论", md, "### 整体风险结论");
        Asserts.assertContains("joinCapped 等N项", md, "等6项");
        Asserts.assertContains("上下文截断省略号", md, "…");
        Asserts.assertNotContains("整体分支空清单不占位", md, "未识别到破坏性变更点");
        // 阶段B 文件级截断提示
        Asserts.assertContains("阶段B 小节", md, "### 阶段B 逐文件深读");
    }

    public void testRender_categoryConclusions_specialPlaceholders() {
        // breaking：空 breakingChanges → 占位「未识别到破坏性变更点」。
        String mb = renderCategory("breaking", c -> {
            c.setCompatibilityVerdict("存在不兼容");
        });
        Asserts.assertContains("破坏性专项小节", mb, "### 破坏性变更专项结论");
        Asserts.assertContains("空破坏性占位", mb, "未识别到破坏性变更点");
        Asserts.assertContains("兼容性结论", mb, "兼容性结论");

        // testpoints：空 testPoints → 占位「未识别到针对性测试要点」。
        String mt = renderCategory("testpoints", c -> {
        });
        Asserts.assertContains("测试要点专项小节", mt, "### 测试要点专项结论");
        Asserts.assertContains("空测试要点占位", mt, "未识别到针对性测试要点");

        // impact：所有字段空 → 小节标题在但无 KV 行。
        String mi = renderCategory("impact", c -> {
        });
        Asserts.assertContains("影响范围专项小节", mi, "### 影响范围专项结论");
        Asserts.assertNotContains("空 impact 无受影响模块行", mi, "**受影响模块/服务**");
    }

    private interface ConclusionFiller {
        void fill(CategoryConclusion c);
    }

    private static String renderCategory(String category, ConclusionFiller filler) {
        MarkdownReport rep = new MarkdownReport(15);
        StageASummary s = new StageASummary();
        s.setOverallRisk("MEDIUM");
        s.setCategory(category);
        CategoryConclusion c = new CategoryConclusion();
        filler.fill(c);
        c.setSuggestions("建议补齐回归用例");
        s.setConclusion(c);
        String md = rep.render(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(),
                EMPTY, EMPTY, s, null, null);
        Asserts.assertContains("优化改造建议(全类别共享)", md, "### 优化改造建议");
        return md;
    }

    // --------------------------- 文本类章节：失败单元与超 Top-K 提示 ---------------------------

    public void testRender_textDiff_failedUnitAndBeyondTopK() {
        Map<String, DecompiledUnit> text = new LinkedHashMap<>();
        // 失败单元排在最前：!isOk 分支不消耗 shown 预算，故即便 Top-K=1 也会被渲染，
        // 紧随其后的 app.js 仍展开（填充引擎行与 diff），extra.js 超出预算被截断。
        text.put("bad.css", badUnit("bad.css"));
        text.put("app.js", okUnit("app.js"));
        text.put("extra.js", okUnit("extra.js"));
        MarkdownReport rep = new MarkdownReport(1); // Top-K=1
        String md = rep.render(snapEmpty("1.0"), snapEmpty("2.0"), buildDiff(), buildStats(), EMPTY, text);
        Asserts.assertContains("文本章节标题", md, "## 四、文本类文件内容差异");
        Asserts.assertContains("首个文本文件", md, "### app.js");
        Asserts.assertContains("处理引擎", md, "- 处理引擎：cfr");
        Asserts.assertContains("读取/美化失败行", md, "读取/美化失败：反编译炸了");
        Asserts.assertNotContains("Top-K=1 后 extra.js 不展开", md, "### extra.js");
        Asserts.assertContains("超 Top-K 文本提示", md, "个文本类文件未展开（Top-K 限制）");
    }
}
