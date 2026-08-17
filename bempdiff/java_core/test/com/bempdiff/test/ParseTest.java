package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 包解析引擎测试：类型识别、L0/L1/L2 分层、版本提取、命名空间、Zip Slip 防护、嵌套读取、配置接线。 */
public final class ParseTest {

    private final PackageParser parser = new PackageParser();

    private static Map<String, byte[]> warEntries() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        e.put("WEB-INF/classes/com/other/X.class", TestFixtures.compileClass("com.other.X", TestFixtures.SRC_X));
        // 内部 lib（含 com/internal 前缀）应展开为 L1
        Map<String, byte[]> inner = new LinkedHashMap<>();
        inner.put("com/internal/B.class", TestFixtures.compileClass("com.internal.B", TestFixtures.SRC_B));
        e.put("WEB-INF/lib/internal-core.jar", TestFixtures.makeZip(inner));
        // 第三方 lib（org/apache）仅 jar 级 L2
        Map<String, byte[]> tp = new LinkedHashMap<>();
        tp.put("org/apache/T.class", TestFixtures.compileClass("org.apache.T", TestFixtures.SRC_T));
        e.put("WEB-INF/lib/third-party.jar", TestFixtures.makeZip(tp));
        return e;
    }

    public void testDetectType_war() throws IOException {
        Path p = TestFixtures.writePackage(
                TestFixtures.makeWar(warEntries(), "1.0.0"), "detect-war");
        Asserts.assertEquals("应为 WAR", PackageType.WAR, parser.detectType(p));
    }

    public void testDetectType_fatJar() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("BOOT-INF/classes/com/x/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        e.put("BOOT-INF/lib/y.jar", TestFixtures.makeZip(new LinkedHashMap<>()));
        Path p = TestFixtures.writePackage(TestFixtures.makeZip(e), "detect-fat");
        Asserts.assertEquals("应为 FAT_JAR", PackageType.FAT_JAR, parser.detectType(p));
    }

    public void testDetectType_plainJar() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("com/x/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Path p = TestFixtures.writePackage(TestFixtures.makeZip(e), "detect-jar");
        Asserts.assertEquals("应为 JAR", PackageType.JAR, parser.detectType(p));
    }

    public void testParseWar_layering() throws IOException {
        Path p = TestFixtures.writePackage(TestFixtures.makeWar(warEntries(), "2.3.4"), "parse-war");
        PackageSnapshot snap = parser.parse(p, new ParseConfig(), false);
        Map<String, LogicalEntry> entries = snap.getEntries();

        Asserts.assertEquals("版本应来自 MANIFEST", "2.3.4", snap.getVersion());
        Asserts.assertNotNull("A.class 应存在", entries.get("WEB-INF/classes/com/internal/A.class"));
        Asserts.assertEquals("A 应为 L1", Layer.L1, entries.get("WEB-INF/classes/com/internal/A.class").getLayer());
        Asserts.assertEquals("A 应为 CLASS", FileClass.CLASS, entries.get("WEB-INF/classes/com/internal/A.class").getFileClass());

        // 内部 lib 应展开为带命名空间的 L1 条目
        LogicalEntry b = entries.get("WEB-INF/lib/internal-core.jar/com/internal/B.class");
        Asserts.assertNotNull("内部 lib 的 B.class 应展开", b);
        Asserts.assertEquals("B 应为 L1", Layer.L1, b.getLayer());
        Asserts.assertTrue("B 的 src 应为嵌套", b.getSrc().isNested());

        // 第三方 lib 仅 jar 级 L2（不展开）
        LogicalEntry tp = entries.get("WEB-INF/lib/third-party.jar");
        Asserts.assertNotNull("third-party.jar 应存在", tp);
        Asserts.assertEquals("third-party 应为 L2", Layer.L2, tp.getLayer());
        Asserts.assertEquals("third-party 应为 JAR", FileClass.JAR, tp.getFileClass());
        Asserts.assertNull("third-party 内部类不应展开",
                entries.get("WEB-INF/lib/third-party.jar/org/apache/T.class"));

        // MANIFEST 属 L0
        Asserts.assertEquals("MANIFEST 应为 L0", Layer.L0, entries.get("META-INF/MANIFEST.MF").getLayer());
    }

    public void testExtractVersion_fromPom() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("META-INF/maven/com/foo/bar/pom.properties", "version=9.9.9\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        e.put("com/x/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Path p = TestFixtures.writePackage(TestFixtures.makeZip(e), "pom-ver");
        PackageSnapshot snap = parser.parse(p, new ParseConfig(), false);
        Asserts.assertEquals("版本应来自 pom.properties", "9.9.9", snap.getVersion());
    }

    public void testZipSlip_rejected() throws IOException {
        // 含路径穿越条目的 war：该条目应被 sanitizeKey 拒绝并跳过，解析不崩溃
        Map<String, byte[]> e = warEntries();
        e.put("../evil/Escape.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Path p = TestFixtures.writePackage(TestFixtures.makeWar(e, "1.0"), "zipslip");
        PackageSnapshot snap = parser.parse(p, new ParseConfig(), false);
        Asserts.assertNull("穿越条目不得进入解析结果", snap.getEntries().get("../evil/Escape.class"));
        Asserts.assertNotNull("合法条目仍应解析", snap.getEntries().get("WEB-INF/classes/com/internal/A.class"));
    }

    public void testReadEntryBytes_nested() throws IOException {
        Path p = TestFixtures.writePackage(TestFixtures.makeWar(warEntries(), "1.0"), "readnest");
        PackageSnapshot snap = parser.parse(p, new ParseConfig(), false);
        LogicalEntry b = snap.getEntries().get("WEB-INF/lib/internal-core.jar/com/internal/B.class");
        byte[] bytes = parser.readEntryBytes(snap, b);
        Asserts.assertTrue("应读回嵌套 class 字节", bytes != null && bytes.length > 0);
        // 前 4 字节应为 class 魔数 CAFEBABE
        Asserts.assertEquals("class 魔数校验", (byte) 0xCA, bytes[0]);
        Asserts.assertEquals("class 魔数校验", (byte) (byte) 0xFE, bytes[1]);
    }

    public void testExpandAllForPlainJar_wiring() throws IOException {
        // 普通 jar 中一个非内部前缀的 class（otherpkg/ 不在 com/、cn/ 内）：默认 L0，开启 expandAllForPlainJar 后变 L1
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("otherpkg/Y.class", TestFixtures.compileClass("otherpkg.Y",
                "package otherpkg; public class Y { public int v=1; }"));
        Path p = TestFixtures.writePackage(TestFixtures.makeZip(e), "plainjar");

        ParseConfig cfgDefault = new ParseConfig();
        PackageSnapshot snapDefault = parser.parse(p, cfgDefault, false);
        Asserts.assertEquals("默认非前缀 class 应为 L0", Layer.L0,
                snapDefault.getEntries().get("otherpkg/Y.class").getLayer());

        ParseConfig cfgExpand = new ParseConfig();
        cfgExpand.setExpandAllForPlainJar(true);
        PackageSnapshot snapExpand = parser.parse(p, cfgExpand, false);
        Asserts.assertEquals("开启 expandAllForPlainJar 后应为 L1", Layer.L1,
                snapExpand.getEntries().get("otherpkg/Y.class").getLayer());
    }
}
