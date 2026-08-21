import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.report.MarkdownReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

/**
 * 活体冒烟：生成样本报告，验证代码差异章节「仅变更行 + 行号 + 类型」新格式。
 * 仅用于人工核对，不参与 TestRunner 套件。
 */
public class SampleReportDemo {
    public static void main(String[] args) throws Exception {
        // 模拟一个修改类文件的 unified diff（无 hunk 头，与 LineDiff / Decompiler.simpleDiff 输出一致）
        String diff = " package com.x;\n"
                + " import java.util.List;\n"
                + " public class Billing {\n"
                + "   public void calc() {\n"
                + "-    int oldRate = 5;\n"
                + "+    int newRate = 8;\n"
                + "+    if (newRate > 7) {\n"
                + "+      audit.log(\"rate exceeded\");\n"
                + "+    }\n"
                + "   }\n"
                + " }\n";

        LinkedHashMap<String, com.bempdiff.model.LogicalEntry> entries = new LinkedHashMap<>();
        PackageSnapshot oldSnap = new PackageSnapshot(Path.of("billing-old.war"), null, "1.0.0", entries);
        PackageSnapshot newSnap = new PackageSnapshot(Path.of("billing-new.war"), null, "2.0.0", entries);

        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "com/x/Billing.class");
        r.put(DiffStatus.ADDED, "com/x/Audit.class");
        r.put(DiffStatus.DELETED, "com/x/Legacy.class");

        DiffStats stats = new DiffStats();
        stats.setAdded(1);
        stats.setDeleted(1);
        stats.setModified(1);
        stats.setUnchanged(0);
        stats.setBizChanged(3);
        stats.setJarChanged(0);

        LinkedHashMap<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("com/x/Billing.class",
                new DecompiledUnit("com/x/Billing.class", "o", "n", diff, "cfr", "", true));

        String md = new MarkdownReport(15).render(oldSnap, newSnap, r, stats, decompiled, new LinkedHashMap<>());

        Files.createDirectories(Paths.get("e2e_work"));
        Files.writeString(Paths.get("e2e_work/sample_report_diff_only.md"), md);
        System.out.println("报告已生成: e2e_work/sample_report_diff_only.md\n");

        // 打印代码差异章节片段供终端直查
        int start = md.indexOf("## 三、");
        int end = md.indexOf("## 六、");
        if (start < 0) start = 0;
        if (end < 0) end = md.length();
        System.out.println("========== 代码差异章节片段 ==========");
        System.out.println(md.substring(start, end));
    }
}
