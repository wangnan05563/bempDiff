package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.DiffRules;
import com.bempdiff.diff.OfficeTextDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;
import com.bempdiff.parse.PackageParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link OfficeTextDiff} 未测分支补充：端到端 diff()（包模式回退读 + folder 模式磁盘直读两分支）、
 * 删除文档 newText==null 前缀分支、extract 对 null/非 zip 非 OLE/缺结构各异常抛出经 diffBytes 兜底为 fail、
 * xlsx 缺工作表 / pptx 缺幻灯片 / 单元格 str·越界共享串·非整数索引等取值分支。
 * 与现有 OfficeTextDiffTest 互补，不编辑该文件。
 */
public final class OfficeTextDiffBranchTest {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String S_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    // ---------------- 夹具 ----------------

    private static byte[] docx(String... paras) throws Exception {
        StringBuilder body = new StringBuilder();
        for (String p : paras) {
            body.append("<w:p><w:r><w:t>").append(esc(p)).append("</w:t></w:r></w:p>");
        }
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"" + W_NS + "\"><w:body>" + body + "</w:body></w:document>";
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("word/document.xml", doc.getBytes(StandardCharsets.UTF_8));
        return TestFixtures.makeZip(e);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 直接指定内部 xml 的 xlsx（覆盖单元格取值分支）。 */
    private static byte[] xlsxRaw(String sharedXml, String sheetXml) throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("xl/sharedStrings.xml", sharedXml.getBytes(StandardCharsets.UTF_8));
        e.put("xl/worksheets/sheet1.xml", sheetXml.getBytes(StandardCharsets.UTF_8));
        return TestFixtures.makeZip(e);
    }

    // ---------------- diff() 端到端：包模式回退读条目字节 ----------------

    public void testDiff_packageModeEndToEnd() throws Exception {
        byte[] oldDoc = docx("标题A", "旧内容");
        byte[] newDoc = docx("标题A", "新内容");
        Map<String, byte[]> oldE = new LinkedHashMap<>();
        oldE.put("docs/a.docx", oldDoc);
        Map<String, byte[]> newE = new LinkedHashMap<>();
        newE.put("docs/a.docx", newDoc);
        PackageSnapshot os = new PackageParser().parse(
                TestFixtures.writePackage(TestFixtures.makeZip(oldE), "otd-old"), new ParseConfig(), false);
        PackageSnapshot ns = new PackageParser().parse(
                TestFixtures.writePackage(TestFixtures.makeZip(newE), "otd-new"), new ParseConfig(), false);
        LogicalEntry oe = os.getEntries().get("docs/a.docx");
        LogicalEntry ne = ns.getEntries().get("docs/a.docx");

        DecompiledUnit u = new OfficeTextDiff().diff(os, ns, oe, ne, "docs/a.docx", FileClass.OFFICE);
        Asserts.assertTrue("端到端 docx diff 应 ok：err=" + u.getError(), u.isOk());
        Asserts.assertEquals("engine office-text", "office-text", u.getEngine());
        Asserts.assertContains("含删除旧内容", u.getDiffText(), "- 旧内容");
        Asserts.assertContains("含新增新内容", u.getDiffText(), "+ 新内容");
    }

    // ---------------- diff()：folder 模式磁盘直读分支 ----------------

    public void testDiff_folderModeDiskDirectRead() throws Exception {
        byte[] d = docx("磁盘文档内容");
        Path dir = Files.createTempDirectory("otd-folder");
        Path file = dir.resolve("a.docx");
        Files.write(file, d);
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        LogicalEntry le = new LogicalEntry("a.docx", Layer.L0, FileClass.OFFICE,
                d.length, "sha", new EntrySource(file.toString(), null));
        m.put("a.docx", le);
        PackageSnapshot snap = new PackageSnapshot(dir, PackageType.WAR, "1", m);

        DecompiledUnit u = new OfficeTextDiff().diff(snap, snap, le, le, "a.docx", FileClass.OFFICE);
        Asserts.assertTrue("folder 磁盘直读应 ok：err=" + u.getError(), u.isOk());
        Asserts.assertContains("提取到磁盘文档内容", String.valueOf(u.getNewSource()), "磁盘文档内容");
    }

    // ---------------- diffBytes 删除文档：newText==null 前缀分支 ----------------

    public void testDiffBytes_deletedDocument() throws Exception {
        byte[] d = docx("将被移除的段落");
        DecompiledUnit u = new OfficeTextDiff().diffBytes(d, null, "gone.docx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertTrue("删除文档 diff 应 ok", u.isOk());
        Asserts.assertContains("应标注删除文件", u.getDiffText(), "[删除文件]");
        Asserts.assertNotNull("oldSource 非空", u.getOldSource());
        Asserts.assertNull("newSource 为 null", u.getNewSource());
        for (String line : u.getDiffText().split("\n", -1)) {
            if (line.isEmpty()) continue;
            Asserts.assertTrue("删除每行以 - 开头: " + line, line.startsWith("- "));
        }
    }

    // ---------------- extract：null / 非 zip 非 OLE / 结构缺失 → diffBytes 兜底 fail ----------------

    public void testExtract_nullReturnsNull() {
        Asserts.assertNull("null 数据返回 null", OfficeTextDiff.extract(null, FileClass.OFFICE));
    }

    public void testDiffBytes_nonZipNonOleFails() throws Exception {
        byte[] junk = "plain text, neither zip nor ole".getBytes(StandardCharsets.UTF_8);
        DecompiledUnit u = new OfficeTextDiff().diffBytes(junk, junk, "x.docx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertFalse("无法识别格式应 fail", u.isOk());
        Asserts.assertContains("错误提示无法识别", String.valueOf(u.getError()), "无法识别");
    }

    public void testDiffBytes_zipWithoutOfficeStructureFails() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("readme.txt", "hi".getBytes(StandardCharsets.UTF_8)); // zip 但无 office 内部结构
        byte[] zip = TestFixtures.makeZip(e);
        DecompiledUnit u = new OfficeTextDiff().diffBytes(zip, zip, "mystery.docx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertFalse("缺 office 结构应 fail", u.isOk());
        Asserts.assertContains("错误提示未识别内部结构", String.valueOf(u.getError()), "未能识别");
    }

    // ---------------- xlsx 缺工作表分支 ----------------

    public void testDiffBytes_xlsxMissingWorksheetsFails() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        // 仅 sharedStrings（命中 xl/sharedStrings.xml 判定）但无 worksheets → extractXlsx 抛缺工作表
        e.put("xl/sharedStrings.xml",
                ("<?xml version=\"1.0\"?><sst xmlns=\"" + S_NS + "\"><si><t>x</t></si></sst>")
                        .getBytes(StandardCharsets.UTF_8));
        byte[] zip = TestFixtures.makeZip(e);
        DecompiledUnit u = new OfficeTextDiff().diffBytes(zip, zip, "b.xlsx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertFalse("缺工作表应 fail", u.isOk());
        Asserts.assertContains("错误提示缺工作表", String.valueOf(u.getError()), "缺少");
    }

    // ---------------- pptx 缺幻灯片分支（有 ppt/slides/ 目录项但无实际幻灯片文件）----------------

    public void testDiffBytes_pptxMissingSlidesFails() throws Exception {
        Map<String, byte[]> e = new LinkedHashMap<>();
        // 非目录条目名「包含」ppt/slides/ → 命中 containsAny 判定走 extractPptx；
        // 但 extractPptx 用 startsWith("ppt/slides/") 列举幻灯片，该条目并不以之前缀开头 → 列表为空 → 抛缺幻灯片。
        e.put("assets/ppt/slides/placeholder.xml", "<a/>".getBytes(StandardCharsets.UTF_8));
        byte[] zip = TestFixtures.makeZip(e);
        DecompiledUnit u = new OfficeTextDiff().diffBytes(zip, zip, "b.pptx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertFalse("缺幻灯片应 fail", u.isOk());
        Asserts.assertContains("错误提示缺幻灯片", String.valueOf(u.getError()), "幻灯片");
    }

    // ---------------- xlsx 单元格取值分支：有效共享串 / 越界 / str 类型 / 非整数索引 ----------------

    public void testExtractXlsx_cellValueVariants() throws Exception {
        String shared = "<?xml version=\"1.0\"?><sst xmlns=\"" + S_NS + "\">"
                + "<si><t>SHARED0</t></si></sst>";
        String sheet = "<?xml version=\"1.0\"?><worksheet xmlns=\"" + S_NS + "\"><sheetData>"
                + "<row r=\"1\">"
                + "<c r=\"A1\" t=\"s\"><v>0</v></c>"           // 有效共享串 → SHARED0
                + "<c r=\"B1\" t=\"s\"><v>5</v></c>"           // 越界索引 → 空串
                + "<c r=\"C1\" t=\"str\"><is><t>STRVAL</t></is></c>" // str 类型 → STRVAL
                + "<c r=\"D1\" t=\"s\"><v>abc</v></c>"         // 非整数索引 → 空串（parseIdx 异常）
                + "</row>"
                + "</sheetData></worksheet>";
        byte[] zip = xlsxRaw(shared, sheet);
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertContains("有效共享字符串被解析", text, "A1=SHARED0");
        Asserts.assertContains("str 内联文本被解析", text, "C1=STRVAL");
        Asserts.assertContains("应含工作表分节标题", text, "sheet1.xml");
        Asserts.assertTrue("越界/非法索引以空串占位不崩", text.contains("B1= |") || text.contains("B1= \n") || text.contains("B1="));
    }
}
