package com.bempdiff.test;

import com.bempdiff.ai.FileAnalysis;
import com.bempdiff.ai.StageASummary;
import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.report.MarkdownReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 报告导出测试：章节完整性、前端章节、删除类→破坏性变更清单、Top-K 限制、writeToFile 自动建目录。 */
public final class ReportTest {

    private static PackageSnapshot snap(String version) {
        return new PackageSnapshot(Path.of("dummy-" + version + ".war"), null, version, new LinkedHashMap<>());
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
        s.setAdded(1); s.setDeleted(1); s.setModified(1); s.setUnchanged(0);
        s.setBizChanged(3); s.setJarChanged(0);
        return s;
    }

    private static Map<String, DecompiledUnit> makeDecompiled() {
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", "diff-a", "cfr", "", true));
        return decompiled;
    }

    /** 前端 JS 美化后 diff 单元（与 class 反编译同构的 DecompiledUnit）。 */
    private static Map<String, DecompiledUnit> makeFrontend() {
        Map<String, DecompiledUnit> frontend = new LinkedHashMap<>();
        // 合法 unified diff（无 hunk 头，与 LineDiff 输出一致）：上下文行 + 删除行 + 新增行，
        // 经 DiffDigest 解析为一个「修改」块（老 L2 → 新 L2）。
        String diffJs = " var x = 1;\n-var x = 1;\n+let x = 2;\n class A {}\n";
        frontend.put("app.min.js", new DecompiledUnit("app.min.js", "o", "n", diffJs, "frontend", "", true));
        return frontend;
    }

    private static final Map<String, DecompiledUnit> EMPTY = new LinkedHashMap<>();

    public void testRender_sectionsComplete() {
        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), makeDecompiled(), EMPTY);

        // 对齐当前章节结构（FR4.4 重构后）：一差异统计 / 二差异文件树 / 三Java类反编译
        // / 四文本类文件内容差异 / 五差异依赖JAR(需 libJar 输入,本例 null 不渲染)
        // / 六破坏性变更清单 / 七AI 智能分析。
        Asserts.assertContains("标题", md, "# 差异分析报告");
        Asserts.assertContains("一、差异统计", md, "## 一、差异统计");
        Asserts.assertContains("新增统计", md, "新增 **1**");
        Asserts.assertContains("二、差异文件树", md, "## 二、差异文件树");
        Asserts.assertContains("三、Java 类反编译差异", md, "## 三、反编译源码级差异");
        Asserts.assertContains("四、文本类文件内容差异", md, "## 四、文本类文件内容差异");
        Asserts.assertContains("六、破坏性变更清单", md, "## 六、破坏性变更清单");
        Asserts.assertContains("删除类应入破坏性清单", md, "c.class");
        Asserts.assertContains("老包版本", md, "1.0");
        Asserts.assertContains("新包版本", md, "2.0");
        // 锁定契约：6 参数 render 重载不应渲染 AI 章节（AI 仅在 9/10 参数重载渲染）；
        // 反向断言防止未来有人不慎把 AI 渲染塞进该路径而测试不报警。
        Asserts.assertNotContains("6 参数 render 不应含 AI 章节", md, "## 七、AI 智能分析");
    }

    public void testRender_frontendSectionRendered() {
        MarkdownReport rep = new MarkdownReport(15);
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), makeDecompiled(), makeFrontend());
        Asserts.assertContains("前端章节标题(现归类于文本类文件内容差异)", md, "## 四、文本类文件内容差异");
        Asserts.assertContains("前端文件名应出现在报告", md, "app.min.js");
        // 报告代码差异章节现已精简为「仅变更行 + 行号 + 类型」：
        Asserts.assertContains("前端 diff 应含旧行内容", md, "var x = 1");
        Asserts.assertContains("前端 diff 应含新行内容", md, "let x = 2");
        Asserts.assertContains("前端 diff 应标注变更类型", md, "[修改]");
        Asserts.assertContains("前端 diff 应含行号", md, "L2");
        Asserts.assertContains("前端处理引擎标注", md, "frontend");
    }

    /** 需求守护：报告代码差异章节仅展示变更行（不含上下文行/整个文件），附原始/修改后行号与变更类型。 */
    public void testRender_codeDiffShowsOnlyChangedLines() {
        MarkdownReport rep = new MarkdownReport(15);
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        // 含上下文行 + 变更行的 unified diff（无 hunk 头，与 LineDiff/Decompiler.simpleDiff 输出一致）
        String diff = " // ctx line AAA\n"
                + "-int oldVal = 1;\n"
                + "+int newVal = 2;\n"
                + " // ctx line ZZZ\n";
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", diff, "cfr", "", true));
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), decompiled, EMPTY);
        // 需求1：仅展示存在差异的代码行，不显示整个文件内容（上下文行应被剔除）
        Asserts.assertContains("应含旧行内容", md, "int oldVal = 1");
        Asserts.assertContains("应含新行内容", md, "int newVal = 2");
        Asserts.assertNotContains("不应展示上下文行 AAA", md, "ctx line AAA");
        Asserts.assertNotContains("不应展示上下文行 ZZZ", md, "ctx line ZZZ");
        // 需求2：差异行附带原始行号与修改后行号
        Asserts.assertContains("应含老侧行号", md, "老 L2");
        Asserts.assertContains("应含新侧行号", md, "新 L2");
        // 需求3：标注差异类型（修改/新增/删除）
        Asserts.assertContains("应标注变更类型", md, "[修改]");
    }

    public void testRender_aiSection_withContextInfluence() {
        MarkdownReport rep = new MarkdownReport(15);
        StageASummary s = new StageASummary();
        s.setOverallRisk("HIGH");
        s.setImpactScope("计费域变更");
        s.setContextInfluence("该改动位于 billing-core，受 spring 事务配置约束，影响被限定在计费域");
        s.setTestThemes(Arrays.asList("回归计费", "校验事务回滚"));

        FileAnalysis fa = new FileAnalysis("com/x/Billing.class", "调整计费逻辑", "LOW",
                "仅影响计费模块", Arrays.asList("回归调用方"), "该模块被声明式事务包裹，回滚策略已覆盖");

        ProjectContext ctx = new ProjectContext("C:/proj", "Maven",
                Arrays.asList("core", "web"), Arrays.asList("spring-boot", "mybatis"),
                Arrays.asList("com/x/App.java"), Arrays.asList("application.yml"),
                Arrays.asList("Maven", "Spring"), Arrays.asList("包根 com.x"),
                "多模块工程（Maven）");

        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(),
                makeDecompiled(), EMPTY, s, Arrays.asList(fa), ctx);

        Asserts.assertContains("应包含 AI 章节", md, "## 七、AI 智能分析");
        Asserts.assertContains("应包含项目级上下文小节", md, "### 项目级上下文（分析依据）");
        Asserts.assertContains("应渲染构建系统", md, "Maven");
        Asserts.assertContains("应渲染模块", md, "core、web");
        Asserts.assertContains("阶段A 应含上下文影响", md, "**项目上下文影响**");
        Asserts.assertContains("阶段A 影响文本", md, "billing-core");
        Asserts.assertContains("阶段B 应含文件级上下文影响", md, "该模块被声明式事务包裹");
    }

    public void testRender_aiSection_disabledWhenNull() {
        MarkdownReport rep = new MarkdownReport(15);
        // summary=null, ctx=null → 应显示未启用
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(),
                makeDecompiled(), EMPTY, null, null, null);
        Asserts.assertContains("未启用提示", md, "未启用 AI 分析");
        Asserts.assertNotContains("不应含项目级上下文章节", md, "### 项目级上下文");
    }

    public void testRender_topKLimit() {
        MarkdownReport rep = new MarkdownReport(2);
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", "da", "cfr", "", true));
        decompiled.put("b.class", new DecompiledUnit("b.class", "o", "n", "db", "cfr", "", true));
        decompiled.put("c.class", new DecompiledUnit("c.class", "o", "n", "dc", "cfr", "", true));
        String md = rep.render(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), decompiled, EMPTY);
        // 章节三在 Top-K=2 限制下仅展示前 2 个文件源码 diff；用精准断言隔离章节三，
        // 避免章节一「### 按文件类型分布」等其它章节标题干扰全局计数。
        Asserts.assertContains("Top-K=2 应展示首个文件 a.class", md, "### a.class");
        Asserts.assertContains("Top-K=2 应展示第二个文件 b.class", md, "### b.class");
        Asserts.assertNotContains("超出 Top-K 的 c.class 不应展开源码", md, "### c.class");
        Asserts.assertContains("应提示超出 Top-K 限制", md, "另有 1 个修改类未展开（Top-K 限制）");
    }

    public void testWriteToFile_createsParentDirs() throws IOException {
        MarkdownReport rep = new MarkdownReport(15);
        Path dir = Files.createTempDirectory("bdreport");
        Path out = dir.resolve("nested").resolve("sub").resolve("report.md"); // 父目录原本不存在
        rep.writeToFile(snap("1.0"), snap("2.0"), buildDiff(), buildStats(), EMPTY, EMPTY, out);
        Asserts.assertTrue("报告文件应被创建", Files.exists(out));
        Asserts.assertTrue("报告不应为空", Files.size(out) > 0);
    }

    private static int countOccurrences(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
        return c;
    }
}
