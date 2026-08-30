package com.bempdiff.test;

import com.bempdiff.diff.DiffRules;
import com.bempdiff.diff.OfficeTextDiff;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.parse.PackageParser;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Office 文档（docx/xlsx/pptx OpenXML）内容提取与文本 diff 的回归测试。
 *
 * <p>守护「Excel / Word 文档内容对比」需求：此前 .xlsx/.docx 归 STATIC（二进制仅 sha 比对），
 * 点开无内容可读。现在解析文档内容为可读文本，走行级 unified diff（+新增/-删除/ 未变）。
 * 构造最小的 OpenXML zip（word/document.xml、xl/sharedStrings.xml + worksheet、ppt/slides/slideN.xml），
 * 对提取与 diff 做确定性断言。</p>
 */
public final class OfficeTextDiffTest {

    // ----------------------------- 分类路由 -----------------------------

    public void testClassify_officeExtensions() throws java.io.IOException {
        Asserts.assertEquals(".docx → OFFICE", FileClass.OFFICE, PackageParser.classify("docs/方案.docx"));
        Asserts.assertEquals(".xlsx → OFFICE", FileClass.OFFICE, PackageParser.classify("data/清单.xlsx"));
        Asserts.assertEquals(".pptx → OFFICE", FileClass.OFFICE, PackageParser.classify("slides/demo.pptx"));
        Asserts.assertEquals("大写 .XLSX → OFFICE", FileClass.OFFICE, PackageParser.classify("DATA/A.XLSX"));
        Asserts.assertEquals("旧版 .doc → OFFICE（分类相同，解析器给出不支持提示）", FileClass.OFFICE, PackageParser.classify("legacy/a.doc"));
        Asserts.assertEquals("旧版 .xls → OFFICE", FileClass.OFFICE, PackageParser.classify("legacy/b.xls"));
        // 回归：常见扩展名不被误伤
        Asserts.assertEquals("回归: .png 仍是 STATIC", FileClass.STATIC, PackageParser.classify("img/logo.png"));
        Asserts.assertEquals("回归: .class 仍是 CLASS", FileClass.CLASS, PackageParser.classify("com/x/A.class"));
        Asserts.assertEquals("回归: .zip 仍是 ARCHIVE", FileClass.ARCHIVE, PackageParser.classify("a.zip"));
    }

    // ----------------------------- docx 提取 -----------------------------

    public void testExtractDocx_paragraphs() throws java.io.IOException {
        byte[] zip = makeDocx("第一段标题", "第二段正文内容");
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertContains("应含第一段", text, "第一段标题");
        Asserts.assertContains("应含第二段", text, "第二段正文内容");
        Asserts.assertTrue("段落应换行", text.contains("第一段标题\n第二段正文内容"));
    }

    public void testExtractDocx_richRun() throws java.io.IOException {
        // 富文本：一个段落内多个 <w:r>，<w:t> 应拼接
        byte[] zip = makeDocxRich("Hello ", "World", " 你好");
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertEquals("富文本 run 拼接", "Hello World 你好", text.trim());
    }

    // ----------------------------- xlsx 提取 -----------------------------

    public void testExtractXlsx_cells() throws java.io.IOException {
        byte[] zip = makeXlsx(
                new String[]{"名称", "数量"},
                new String[][]{{"s", ""}, {"s", ""}},
                new String[][]{{"0", "100"}, {"1", "200"}},
                new String[][]{{"", ""}, {"", ""}});
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertContains("应含表名头", text, "sheet1.xml");
        Asserts.assertContains("应含 A1=名称（共享字符串）", text, "A1=名称");
        Asserts.assertContains("应含 B1=100（数值）", text, "B1=100");
        Asserts.assertContains("应含 A2=数量", text, "A2=数量");
        Asserts.assertContains("应含 B2=200", text, "B2=200");
    }

    public void testExtractXlsx_inlineStr() throws java.io.IOException {
        byte[] zip = makeXlsxInline("内联字符串");
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertContains("内联字符串应被提取", text, "A1=内联字符串");
    }

    // ----------------------------- pptx 提取 -----------------------------

    public void testExtractPptx_slides() throws java.io.IOException {
        byte[] zip = makePptx("第一页标题", "第二页正文");
        String text = OfficeTextDiff.extract(zip, FileClass.OFFICE);
        Asserts.assertContains("应含 slide1 标记", text, "slide1.xml");
        Asserts.assertContains("应含第一页文本", text, "第一页标题");
        Asserts.assertContains("应含第二页文本", text, "第二页正文");
    }

    // ----------------------------- diff -----------------------------

    public void testDiff_docxModified() throws java.io.IOException {
        byte[] oldZip = makeDocx("第一段", "被修改的旧内容", "保留段");
        byte[] newZip = makeDocx("第一段", "修改后的新内容", "保留段");
        DecompiledUnit u = new OfficeTextDiff().diffBytes(oldZip, newZip, "方案.docx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertTrue("docx 内容 diff 应 ok：err=" + u.getError(), u.isOk());
        Asserts.assertEquals("engine 应为 office-text", "office-text", u.getEngine());
        Asserts.assertContains("diff 应含删除行（旧内容）", u.getDiffText(), "- 被修改的旧内容");
        Asserts.assertContains("diff 应含新增行（新内容）", u.getDiffText(), "+ 修改后的新内容");
        Asserts.assertContains("diff 应含保留段（未变）", u.getDiffText(), "保留段");
        Asserts.assertNotNull("oldSource 应可用", u.getOldSource());
        Asserts.assertNotNull("newSource 应可用", u.getNewSource());
    }

    public void testDiff_xlsxModified() throws java.io.IOException {
        byte[] oldZip = makeXlsx(
                new String[]{"名称", "数量"}, new String[][]{{"s", ""}}, new String[][]{{"0", "100"}}, new String[][]{{"", ""}});
        byte[] newZip = makeXlsx(
                new String[]{"名称", "数量"}, new String[][]{{"s", ""}}, new String[][]{{"0", "999"}}, new String[][]{{"", ""}});
        DecompiledUnit u = new OfficeTextDiff().diffBytes(oldZip, newZip, "清单.xlsx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertTrue("xlsx 内容 diff 应 ok：err=" + u.getError(), u.isOk());
        // 行级 diff：整行替换（- Row 1: ... B1=100 / + Row 1: ... B1=999）
        Asserts.assertContains("diff 应含删除行（旧值 100）", u.getDiffText(), "- Row 1: A1=");
        Asserts.assertContains("diff 应含旧单元格值 B1=100", u.getDiffText(), "B1=100");
        Asserts.assertContains("diff 应含新增行（新值 999）", u.getDiffText(), "+ Row 1: A1=");
        Asserts.assertContains("diff 应含新单元格值 B1=999", u.getDiffText(), "B1=999");
    }

    public void testDiff_officeAdded() throws java.io.IOException {
        byte[] newZip = makeDocx("全新文档", "内容");
        DecompiledUnit u = new OfficeTextDiff().diffBytes(null, newZip, "新文档.docx", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertTrue("新增 Office 文档应 ok", u.isOk());
        Asserts.assertContains("应标注新增", u.getDiffText(), "[新增文件]");
        Asserts.assertNull("老侧 null", u.getOldSource());
    }

    // ----------------------------- 旧版二进制 OLE -----------------------------

    public void testLegacyDocUnsupported() throws java.io.IOException {
        // 真实 OLE 容器头魔数（D0CF11E0A1B11AE1）+ 假尾部：isOle=true → 走 extractXls，
        // 触发「旧版二进制 .doc/.ppt 请另存为 .docx/.pptx」的明确提示。
        byte[] ole = new byte[64];
        byte[] magic = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
        System.arraycopy(magic, 0, ole, 0, magic.length);
        DecompiledUnit u = new OfficeTextDiff().diffBytes(ole, ole, "legacy.doc", FileClass.OFFICE, DiffRules.DEFAULT);
        Asserts.assertFalse("旧版二进制 .doc 应 fail", u.isOk());
        Asserts.assertTrue("错误信息应提示另存为 docx", u.getError().contains("docx"));
    }

    public void testNotZipBytes() throws java.io.IOException {
        Asserts.assertFalse("非 zip 字节 isZip=false", OfficeTextDiff.isZip("abc".getBytes(StandardCharsets.UTF_8)));
        Asserts.assertTrue("PK 魔数 isZip=true", OfficeTextDiff.isZip(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00}));
        Asserts.assertFalse("null isZip=false", OfficeTextDiff.isZip(null));
    }

    // ----------------------------- 夹具：构造最小 OpenXML -----------------------------

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String S_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";

    /** 构造 docx：若干段落（每段一个 run）。 */
    private static byte[] makeDocx(String... paragraphs) throws java.io.IOException {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                .append(esc(p)).append("</w:t></w:r></w:p>");
        }
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"" + W_NS + "\"><w:body>" + body + "</w:body></w:document>";
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("word/document.xml", doc.getBytes(StandardCharsets.UTF_8));
        return TestFixtures.makeZip(e);
    }

    /** 构造 docx：一个段落内多个 run（富文本拼接）。 */
    private static byte[] makeDocxRich(String... runs) throws java.io.IOException {
        StringBuilder rs = new StringBuilder();
        for (String r : runs) {
            rs.append("<w:r><w:t xml:space=\"preserve\">").append(esc(r)).append("</w:t></w:r>");
        }
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"" + W_NS + "\"><w:body><w:p>" + rs + "</w:p></w:body></w:document>";
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("word/document.xml", doc.getBytes(StandardCharsets.UTF_8));
        return TestFixtures.makeZip(e);
    }

    /**
     * 构造 xlsx：sharedStrings（共享字符串数组）+ sheet1.xml（rows/cells）。
     * cellsType[c][r] 为单元格类型（"" 数值 / "s" 共享字符串），cellsVal 为 <v> 文本。
     */
    private static byte[] makeXlsx(String[] shared, String[][] cellsType, String[][] cellsVal, String[][] cellsInline) throws java.io.IOException {
        StringBuilder sst = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"" + S_NS + "\">");
        for (String s : shared) sst.append("<si><t>").append(esc(s)).append("</t></si>");
        sst.append("</sst>");

        int rows = cellsType.length;
        int cols = rows == 0 ? 0 : cellsType[0].length;
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"" + S_NS + "\"><sheetData>");
        for (int r = 0; r < rows; r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < cols; c++) {
                String ref = "" + (char) ('A' + c) + (r + 1);
                String type = cellsType[r][c];
                if ("s".equals(type)) {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"s\"><v>").append(cellsVal[r][c]).append("</v></c>");
                } else if ("inlineStr".equals(type)) {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                         .append(esc(cellsInline[r][c])).append("</t></is></c>");
                } else {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(cellsVal[r][c]).append("</v></c>");
                }
            }
            sheet.append("</row>");
        }
        sheet.append("</sheetData></worksheet>");

        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("xl/sharedStrings.xml", sst.toString().getBytes(StandardCharsets.UTF_8));
        e.put("xl/worksheets/sheet1.xml", sheet.toString().getBytes(StandardCharsets.UTF_8));
        return TestFixtures.makeZip(e);
    }

    /** 构造 xlsx：单格 inlineStr。 */
    private static byte[] makeXlsxInline(String value) throws java.io.IOException {
        return makeXlsx(new String[0],
                new String[][]{{"inlineStr"}}, new String[][]{{""}}, new String[][]{{value}});
    }

    /** 构造 pptx：每个 <a:t> 一条文本（两页）。 */
    private static byte[] makePptx(String... texts) throws java.io.IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        int i = 1;
        for (String t : texts) {
            String slide = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<p:sld xmlns:p=\"" + P_NS + "\" xmlns:a=\"" + A_NS + "\">"
                    + "<p:cSld><p:spTree><p:sp><p:txBody>"
                    + "<a:p><a:r><a:t>" + esc(t) + "</a:t></a:r></a:p>"
                    + "</p:txBody></p:sp></p:spTree></p:cSld></p:sld>";
            e.put("ppt/slides/slide" + i + ".xml", slide.getBytes(StandardCharsets.UTF_8));
            i++;
        }
        return TestFixtures.makeZip(e);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
