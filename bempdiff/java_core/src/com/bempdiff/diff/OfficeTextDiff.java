package com.bempdiff.diff;

import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Office 文档（OpenXML）内容解析与文本 diff（需求：Excel / Word 文档内容对比）。
 *
 * <p>现状：.xlsx/.docx 等此前归 STATIC（二进制），只做 sha256/大小比对，点开无内容可读。
 * 本服务把它们解析为可读文本后走行级 unified diff，前端 DiffView 直接复用既有渲染链路，
 * 用户可直观看到新增(+)/删除(-)/修改行。支持：</p>
 * <ul>
 *   <li>.docx/.docm/.dotx — 提取 {@code word/document.xml} 的段落文本（按段落换行）；</li>
 *   <li>.xlsx/.xlsm/.xltx — 提取工作表单元格（共享字符串/内联字符串/数值），按行拼为
 *       {@code Row N: A1=值 | B1=值 | ...}，可读且行级 diff 有语义；</li>
 *   <li>.pptx/.pptm — 提取幻灯片 {@code ppt/slides/slide*.xml} 的文本；</li>
 *   <li>.xls/.xlsm（旧版二进制）— 用 Apache POI HSSF 解析工作表单元格，按行拼为
 *       {@code Row N: A1=值 | B1=值 | ...}，与 xlsx 输出结构一致；</li>
 *   <li>.doc/.ppt（旧版二进制 OLE）— 现有解析器未覆盖，明确提示暂不支持（可另存为 OpenXML 后再比对）。</li>
 * </ul>
 *
 * <p>与 {@link FrontendTextDiff} 同构：返回 {@link DecompiledUnit}（oldSource/newSource/diffText/engine），
 * 报告 / UI / AI 复用同一渲染与深读路径。</p>
 */
public final class OfficeTextDiff {

    /** ZIP 魔数（PK\x03\x04），用于区分 OpenXML(zip) 与旧版二进制 OLE(.doc/.xls/.ppt)。 */
    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

    /** WordprocessingML 命名空间 URI（docx 段落/文本限定，避免误抓 drawingML 文本框 <a:p><a:t>）。 */
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** 工作表/幻灯片分节标题的前缀与后缀（多处复用，统一定为常量避免重复字面量）。 */
    private static final String SHEET_DIVIDER_PREFIX = "===== ";
    private static final String SHEET_DIVIDER_SUFFIX = " =====\n";

    /** 对单个 Office 文档做「内容提取 + 双栏 diff」（默认规则，供顶层条目）。 */
    public DecompiledUnit diff(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                               LogicalEntry oldEntry, LogicalEntry newEntry, String key, FileClass fc) {
        return diff(oldSnap, newSnap, oldEntry, newEntry, key, fc, DiffRules.DEFAULT);
    }

    /** 对单个 Office 文档做「内容提取 + 双栏 diff」（带忽略规则，供顶层条目）。 */
    public DecompiledUnit diff(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                               LogicalEntry oldEntry, LogicalEntry newEntry, String key, FileClass fc,
                               DiffRules rules) {
        try {
            byte[] oldBytes = (oldEntry != null) ? readEntryBytesSafe(oldSnap, oldEntry) : null;
            byte[] newBytes = (newEntry != null) ? readEntryBytesSafe(newSnap, newEntry) : null;
            return diffBytes(oldBytes, newBytes, key, fc, rules);
        } catch (Exception e) {
            return DecompiledUnit.fail(key, safeMsg(e));
        }
    }

    /**
     * 读取条目字节，兼容两种模式：
     * - folder 模式：snap.getFile() 是目录，PackageParser.readEntryBytes 用 ZipFile 打开会抛「拒绝访问」，
     *   若 EntrySource 指向磁盘真实文件则直接 Files.readAllBytes；
     * - package 模式：条目在包内（outerEntry 为包内相对路径），回退 PackageParser.readEntryBytes 抽取。
     */
    private static byte[] readEntryBytesSafe(PackageSnapshot snap, LogicalEntry e) throws IOException {
        // e==null 为防御性守卫（调用方已前置判空）；按返回类型回退空数组而非 null
        if (e == null) return new byte[0];
        com.bempdiff.model.EntrySource src = e.getSrc();
        if (src != null && !src.isNested() && src.getOuterEntry() != null) {
            java.nio.file.Path p = java.nio.file.Paths.get(src.getOuterEntry());
            if (java.nio.file.Files.isRegularFile(p)) {
                return java.nio.file.Files.readAllBytes(p);
            }
        }
        return new PackageParser().readEntryBytes(snap, e);
    }

    /**
     * 直接对原始字节做「内容提取 + 双栏 diff」（供归档内部 Office 条目复用）。
     * 任一侧字节为 null 表示该侧不存在（新增/删除文档），语义与 FrontendTextDiff 一致。
     */
    public DecompiledUnit diffBytes(byte[] oldBytes, byte[] newBytes, String key, FileClass fc, DiffRules rules) {
        try {
            String oldText = (oldBytes != null) ? extract(oldBytes, fc) : null;
            String newText = (newBytes != null) ? extract(newBytes, fc) : null;

            String engine = "office-text";
            String diff;
            if (oldText == null) {
                // 新增文档：每行加 "+ " 前缀（含说明行），保证前端 DiffView 行号计数连续、右栏归属正确
                diff = prefixLines("+ ", "// [新增文件] 老侧无此文档\n" + (newText == null ? "" : newText));
            } else if (newText == null) {
                // 删除文档：每行加 "- " 前缀，同理保证行号计数
                diff = prefixLines("- ", "// [删除文件] 新侧无此文档（文档移除，需确认引用方）\n" + oldText);
            } else {
                diff = LineDiff.unified(oldText, newText, rules);
            }
            // 短哈希：Office 文档提取后字节可能不可重复（POI 解析受版本影响），用原始字节更稳
            String oldHash = (oldBytes != null)
                    ? com.bempdiff.util.ShortHash.ofBytes(oldBytes) : "0000000";
            String newHash = (newBytes != null)
                    ? com.bempdiff.util.ShortHash.ofBytes(newBytes) : "0000000";
            return new DecompiledUnit(key, oldText, newText, diff, engine, "", true, oldHash, newHash);
        } catch (Exception e) {
            return DecompiledUnit.fail(key, safeMsg(e));
        }
    }

    /** 给文本每一行统一加 diff 前缀，与 FrontendTextDiff.prefixLines 语义一致。 */
    private static String prefixLines(String prefix, String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            // L4 修复：跳过纯空行，避免 diffText 末尾产生多余的 "prefix + 空行" 噪音。
            if (line.isEmpty()) continue;
            sb.append(prefix).append(line).append("\n");
        }
        return sb.toString();
    }

    /** L8 修复：异常消息可能为 null（如裸 NPE），返回稳定兜底文案避免向用户展示 "null"。 */
    private static String safeMsg(Throwable e) {
        String m = e.getMessage();
        return (m != null && !m.isEmpty()) ? m : e.getClass().getSimpleName();
    }

    /**
     * 按文件类型提取文档可读文本。
     * - OpenXML（zip 容器）：按内部条目名判定 docx/xlsx/pptx；
     * - 旧版二进制 OLE（以 D0CF11E0A1B11AE1 魔数开头）：用 POI HSSF 解析 .xls，
     *   .doc/.ppt 仍不支持，给出明确转格式提示。
     */
    public static String extract(byte[] data, FileClass fc) { // NOSONAR(S1172) fc 为公共测试 API 形参（extract(zip, fc) 被测试直接调用），不能移除
        if (data == null) return null;
        if (!isZip(data)) {
            if (isOle(data)) {
                // OLE 容器统一交给 HSSF 尝试：真正的 .xls 能被解析，.doc/.ppt 会抛异常，
                // 在 extractXls 内部转成「请另存为 .xlsx/.docx」的明确提示。
                return extractXls(data);
            }
            throw new IllegalArgumentException(
                    "无法识别的文件格式（既非 OpenXML zip，也非旧版二进制 OLE），无法解析文档内容");
        }
        // M1 修复：改为枚举 zip 条目名判定文档类型，避免对整包字节做 UTF-8 decode（数十 MB 文档
        // 会生成更大 String 并全量扫描）。zip 局部文件头中的条目名是明文存储，直接列表比对即可。
        List<String> names = listZipEntries(data, "");
        if (containsAny(names, "word/document.xml")) {
            return extractDocx(data);
        }
        if (containsAny(names, "xl/sharedStrings.xml", "xl/worksheets/")) {
            return extractXlsx(data);
        }
        if (containsAny(names, "ppt/slides/")) {
            return extractPptx(data);
        }
        throw new IllegalArgumentException("未能识别 Office 文档内部结构（缺少 word/document.xml / xl/worksheets / ppt/slides），可能不是标准 OpenXML 文件");
    }

    /** 判断字节是否为旧版二进制 OLE 容器（header 魔数 D0CF11E0A1B11AE1，.doc/.xls/.ppt 共用）。 */
    private static boolean isOle(byte[] data) {
        if (data == null || data.length < 8) return false;
        byte[] magic = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };
        for (int i = 0; i < 8; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    /** 判断条目名集合是否含任一目标子串（用于文档类型判定）。 */
    private static boolean containsAny(List<String> names, String... needles) {
        for (String n : names) {
            for (String needle : needles) {
                if (n.contains(needle)) return true;
            }
        }
        return false;
    }

    /** 判断字节是否为 ZIP 容器（OpenXML 的 docx/xlsx/pptx 都是 zip）。 */
    public static boolean isZip(byte[] data) {
        if (data == null || data.length < 4) return false;
        for (int i = 0; i < 4; i++) {
            if (data[i] != ZIP_MAGIC[i]) return false;
        }
        return true;
    }

    // ----------------------------- docx -----------------------------

    /** 提取 docx 文本：word/document.xml 中所有段落（&lt;w:p&gt;），段落内拼接 &lt;w:t&gt; 文本。 */
    public static String extractDocx(byte[] zip) {
        byte[] xml = readZipEntry(zip, "word/document.xml")
                .orElseThrow(() -> new IllegalArgumentException("docx 缺少 word/document.xml"));
        Document doc = parseXml(xml);
        StringBuilder sb = new StringBuilder();
        NodeList paras = doc.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < paras.getLength(); i++) {
            Node p = paras.item(i);
            // L7 修复：限定 WordprocessingML 命名空间的段落，跳过 drawingML 文本框（<a:p><a:t>）噪声，
            // 避免与正文段落重复提取同一段文本。
            if (p instanceof Element && W_NS.equals(p.getNamespaceURI())) {
                // L3 统一：与 pptx 一致，先 trim 再跳过空行（去除冗余空白行噪声）。
                String line = collectText((Element) p, "t").trim();
                if (!line.isEmpty()) sb.append(line).append('\n');
            }
        }
        return trimTrailingBlank(sb.toString());
    }

    // ----------------------------- xls (OLE/HSSF) -----------------------------

    /**
     * 提取旧版二进制 .xls 文本：用 POI HSSF 打开工作表，按行与单元格格式化输出，
     * 与 xlsx 的 {@code Row N: A1=值 | B1=值 | ...} 结构保持一致，走统一 diff 渲染。
     * .doc/.ppt 同为 OLE 容器，其根流结构 HSSF 不识别会抛异常，转成明确转格式提示。
     */
    public static String extractXls(byte[] data) {
        try (InputStream in = new ByteArrayInputStream(data);
             Workbook wb = new HSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter(); // POI 内置：数值/日期/文本按显示格式格式化
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                sb.append(xlsSheetText(wb.getSheetAt(s), s, fmt));
            }
            String out = sb.toString();
            if (out.trim().isEmpty()) {
                throw new IllegalArgumentException(".xls 未解析到任何工作表内容（可能为空或非标准 .xls）");
            }
            return trimTrailingBlank(out);
        } catch (IllegalArgumentException e) {
            // 结构校验错误直接上抛，保留具体原因
            throw e;
        } catch (Exception e) {
            // HSSF 打不开 OLE 根流 = 大概率是 .doc/.ppt（Word/PPT 二进制格式），给出转格式提示。
            // 用原异常信息兜底，避免误判（某些损坏/加密 .xls 也会走到这里）。
            throw new IllegalArgumentException("无法解析该旧版二进制 Office 文件（如为 .doc/.ppt 请另存为 .docx/.pptx；如为 .xls 请确认未被加密或损坏）：" + safeMsg(e));
        }
    }

    /** 单个 .xls 工作表的文本：分节标题 + 逐行「Row N: A1=值 | ...」。 */
    private static String xlsSheetText(Sheet sheet, int sheetIndex, DataFormatter fmt) {
        StringBuilder sb = new StringBuilder();
        sb.append(sheetHeader(sheet, sheetIndex));
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            String rowText = xlsRowText(sheet.getRow(r), fmt);
            if (rowText == null) continue;
            sb.append(rowText).append('\n');
        }
        return sb.toString();
    }

    /** 格式化单个 .xls 行为「Row N: A1=值 | ...」；空行/全部空单元格返回 null（跳过）。 */
    private static String xlsRowText(Row row, DataFormatter fmt) {
        if (row == null) return null; // 稀疏行：无单元格跳过
        StringBuilder line = new StringBuilder("Row ").append(row.getRowNum() + 1).append(": ");
        List<String> cells = new ArrayList<>();
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                String ref = cell.getAddress().formatAsString(); // 如 A1
                String value = fmt.formatCellValue(cell);
                if (value == null) value = "";
                // 值也可能为空字符串（空单元格会 formatCellValue 返回 ""，跳过降低噪声）
                if (!value.isEmpty()) cells.add(ref + "=" + value);
            }
        }
        if (cells.isEmpty()) return null;
        return line.append(String.join(" | ", cells)).toString();
    }

    /** 工作表分节标题（默认名用 Sheet+序号补位）。 */
    private static String sheetHeader(Sheet sheet, int index) {
        String sheetName = sheet.getSheetName();
        if (sheetName == null || sheetName.isEmpty()) sheetName = "Sheet" + (index + 1);
        return SHEET_DIVIDER_PREFIX + sheetName + SHEET_DIVIDER_SUFFIX;
    }

    // ----------------------------- xlsx -----------------------------

    /** 提取 xlsx 文本：sharedStrings + 每个工作表 sheetN.xml 的行（&lt;row&gt;）与单元格（&lt;c&gt;）。 */
    public static String extractXlsx(byte[] zip) {
        // M1 修复：单次遍历 zip 把所有 worksheet + sharedStrings 的字节收集到 Map，
        // 避免旧实现「每个 sheet 调一次 readZipEntry 全扫整个 zip」（O(sheets × zip大小)）。
        Map<String, byte[]> entries = readZipEntriesByPrefix(zip, "xl/worksheets/");
        byte[] sharedXml = readZipEntry(zip, "xl/sharedStrings.xml").orElse(null);
        List<String> shared = (sharedXml != null) ? readSharedStringsFromXml(sharedXml) : new ArrayList<>();
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("xlsx 缺少 xl/worksheets/ 工作表");
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, byte[]> en : entries.entrySet()) {
            String sheetName = sheetDisplayName(en.getKey());
            byte[] xml = en.getValue();
            sb.append(SHEET_DIVIDER_PREFIX).append(sheetName).append(SHEET_DIVIDER_SUFFIX);
            if (xml == null) continue;
            sb.append(xlsxSheetBody(parseXml(xml), shared));
        }
        return trimTrailingBlank(sb.toString());
    }

    /** 从工作表 zip 路径取展示名（去目录前缀，如 xl/worksheets/sheet1.xml → sheet1.xml）。 */
    private static String sheetDisplayName(String sheetPath) {
        String sheetName = sheetPath;
        int slash = sheetName.lastIndexOf('/');
        if (slash >= 0) sheetName = sheetName.substring(slash + 1);
        return sheetName;
    }

    /** 单个 worksheet 的逐行文本（不含分节标题，标题由调用方拼）。 */
    private static String xlsxSheetBody(Document doc, List<String> shared) {
        StringBuilder sb = new StringBuilder();
        NodeList rows = doc.getElementsByTagNameNS("*", "row");
        for (int r = 0; r < rows.getLength(); r++) {
            Node rowNode = rows.item(r);
            if (rowNode instanceof Element) {
                String rowText = xlsxRowText((Element) rowNode, r, shared);
                if (rowText == null) {
                    sb.append('\n');
                } else {
                    sb.append(rowText).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** 格式化单个 <row> 为「Row N: A1=值 | ...」；无单元格返回 null。 */
    private static String xlsxRowText(Element row, int rowIndex, List<String> shared) {
        String rowNum = row.getAttribute("r");
        StringBuilder line = new StringBuilder("Row ");
        line.append(rowNum.isEmpty() ? (rowIndex + 1) : rowNum).append(": ");
        List<String> cells = new ArrayList<>();
        NodeList cs = row.getElementsByTagNameNS("*", "c");
        for (int c = 0; c < cs.getLength(); c++) {
            if (!(cs.item(c) instanceof Element)) continue;
            Element cell = (Element) cs.item(c);
            String ref = cell.getAttribute("r");
            String value = xlsxCellValue(cell, shared);
            // 值也可能为空：内联/共享字符串缺内容时兜底为空串（不丢列对齐）
            if (value == null) value = "";
            cells.add((ref.isEmpty() ? "c" + c : ref) + "=" + value);
        }
        if (cells.isEmpty()) return null;
        return line.append(String.join(" | ", cells)).toString();
    }

    /** 单元格取值：共享字符串 idx → 文本；inlineStr/str → 文本；其余为 <v> 原值。 */
    private static String xlsxCellValue(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("s".equals(type)) {
            // 共享字符串：<v>idx</v>
            String v = firstText(cell, "v");
            if (v.isEmpty()) return "";
            int idx = parseIdx(v);
            return (idx >= 0 && idx < shared.size()) ? shared.get(idx) : "";
        } else if ("inlineStr".equals(type) || "str".equals(type)) {
            // 内联字符串：<is><t>..</t></is>
            return collectText(cell, "t");
        } else {
            // 数值 / 公式结果 / 布尔：<v>..
            return firstText(cell, "v");
        }
    }

    /** 读取 xl/sharedStrings.xml 的共享字符串列表（<si><t>..</t></si>，含富文本 <r>）。 */
    private static List<String> readSharedStringsFromXml(byte[] xml) {
        List<String> out = new ArrayList<>();
        if (xml == null) return out;
        try {
            Document doc = parseXml(xml);
            NodeList sis = doc.getElementsByTagNameNS("*", "si");
            for (int i = 0; i < sis.getLength(); i++) {
                Node si = sis.item(i);
                if (si instanceof Element) out.add(collectText((Element) si, "t"));
            }
        } catch (Exception ignored) {
            // sharedStrings 缺失或损坏：单元格按 v 索引直显，尽力而为
        }
        return out;
    }

    // ----------------------------- pptx -----------------------------

    /** 提取 pptx 文本：ppt/slides/slideN.xml 的所有 <a:t> 文本，按段落（</a:p>）换行。 */
    public static String extractPptx(byte[] zip) {
        List<String> slides = listZipEntries(zip, "ppt/slides/");
        if (slides.isEmpty()) throw new IllegalArgumentException("pptx 缺少 ppt/slides/ 幻灯片");
        StringBuilder sb = new StringBuilder();
        for (String slidePath : slides) {
            String name = slidePath.substring(slidePath.lastIndexOf('/') + 1);
            sb.append(SHEET_DIVIDER_PREFIX).append(name).append(SHEET_DIVIDER_SUFFIX);
            byte[] xml = readZipEntry(zip, slidePath).orElse(null);
            if (xml == null) continue;
            Document doc = parseXml(xml);
            NodeList paras = doc.getElementsByTagNameNS("*", "p");
            for (int i = 0; i < paras.getLength(); i++) {
                Node p = paras.item(i);
                if (!(p instanceof Element)) continue;
                String line = collectText((Element) p, "t").trim();
                if (!line.isEmpty()) sb.append(line).append('\n');
            }
        }
        return trimTrailingBlank(sb.toString());
    }

    // ----------------------------- 工具 -----------------------------

    /** 收集元素内所有指定标签（不限命名空间）的文本并拼接。 */
    private static String collectText(Element root, String tag) {
        NodeList ts = root.getElementsByTagNameNS("*", tag);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ts.getLength(); i++) {
            Node n = ts.item(i);
            sb.append(n.getTextContent());
        }
        return sb.toString();
    }

    /** 取元素内第一个指定标签的文本（空串兜底）。 */
    private static String firstText(Element root, String tag) {
        NodeList ns = root.getElementsByTagNameNS("*", tag);
        if (ns.getLength() == 0) return "";
        return ns.item(0).getTextContent();
    }

    private static int parseIdx(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            // S3 修复：返回哨兵 -1 表示解析失败；调用点 idx >= 0 守卫会拒绝 -1，
            // 避免退化显示 shared.get(0)（单元格内容错位到第一条共享字符串）。
            return v >= 0 ? v : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 解析 XML（禁用外部实体，防护 XXE；Office XML 均为 UTF-8）。 */
    private static Document parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setExpandEntityReferences(false);
            disableDoctypeDeclaration(f);
            f.setXIncludeAware(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML 解析失败：" + safeMsg(e));
        }
    }

    /** 关闭 DOCTYPE 声明以防护 XXE：老 JDK 无此 feature 时静默跳过（仅弱化该层防护，不影响解析）。 */
    private static void disableDoctypeDeclaration(DocumentBuilderFactory f) {
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // 老 JDK 无此 feature：跳过
        }
    }

    /** 从 zip 字节中读取指定条目（精确名，未命中时尝试小写匹配一次）；缺失/读取失败返回 Optional.empty()。 */
    private static Optional<byte[]> readZipEntry(byte[] zip, String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals(path) || name.toLowerCase(Locale.ROOT).equals(lowerPath)) {
                    return Optional.of(zis.readAllBytes());
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** 列出 zip 内前缀匹配的条目名（用于枚举 xl/worksheets/、ppt/slides/）。 */
    private static List<String> listZipEntries(byte[] zip, String prefix) {
        List<String> out = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) out.add(name);
            }
        } catch (IOException ignored) {
            // 读取异常：返回已收集条目
        }
        return out;
    }

    /** M1 修复：单次遍历 zip，收集指定前缀（含其自身精确名）的所有条目字节到 Map，避免每条目重复全扫。 */
    private static Map<String, byte[]> readZipEntriesByPrefix(byte[] zip, String prefix) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    out.put(name, zis.readAllBytes());
                }
            }
        } catch (IOException ignored) {
            // 读取异常：返回已收集条目
        }
        return out;
    }

    /** 去掉尾部连续空行（diff 更干净）。 */
    private static String trimTrailingBlank(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') end--;
            else break;
        }
        return s.substring(0, end);
    }
}
