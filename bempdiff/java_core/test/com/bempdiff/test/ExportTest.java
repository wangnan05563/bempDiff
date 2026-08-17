package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.export.AssetExporter;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** 差异资产导出测试：class 导出(命名空间)、Zip Slip 防护、jar 导出、反编译源码 zip(Top-K)。 */
public final class ExportTest {

    private static Map<String, byte[]> warWithThirdParty() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("WEB-INF/classes/com/internal/A.class", TestFixtures.compileClass("com.internal.A", TestFixtures.SRC_A_V1));
        Map<String, byte[]> tp = new LinkedHashMap<>();
        tp.put("org/apache/T.class", TestFixtures.compileClass("org.apache.T", TestFixtures.SRC_T));
        e.put("WEB-INF/lib/third-party.jar", TestFixtures.makeZip(tp));
        return e;
    }

    public void testExportDiffClasses_namespacePreserved() throws IOException {
        Path war = TestFixtures.writePackage(TestFixtures.makeWar(warWithThirdParty(), "1"), "exp-class");
        PackageParser parser = new PackageParser();
        PackageSnapshot snap = parser.parse(war, new ParseConfig(), false);
        LogicalEntry a = snap.getEntries().get("WEB-INF/classes/com/internal/A.class");

        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/classes/com/internal/A.class");
        // 把 A 放进两个快照（修改类需两侧都有）
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        m.put("WEB-INF/classes/com/internal/A.class", a);
        PackageSnapshot oldSnap = new PackageSnapshot(war, snap.getType(), null, m);
        PackageSnapshot newSnap = new PackageSnapshot(war, snap.getType(), null, m);

        Path outDir = Files.createTempDirectory("bdexp");
        Path classesDir = new AssetExporter().exportDiffClasses(r, oldSnap, newSnap, outDir);
        Path target = classesDir.resolve("WEB-INF/classes/com/internal/A.class");
        Asserts.assertTrue("应保留命名空间路径导出", Files.exists(target));
        Asserts.assertTrue("导出文件不应为空", Files.size(target) > 0);
    }

    public void testExportDiffClasses_zipSlipRefused() throws IOException {
        Path war = TestFixtures.writePackage(TestFixtures.makeWar(warWithThirdParty(), "1"), "exp-slip");
        PackageParser parser = new PackageParser();
        PackageSnapshot snap = parser.parse(war, new ParseConfig(), false);
        LogicalEntry a = snap.getEntries().get("WEB-INF/classes/com/internal/A.class");

        // 恶意 key 含 ".."，但 src 指向真实存在的 A（读字节成功），写盘前应被 Zip Slip 防护拦截
        LogicalEntry evil = new LogicalEntry("../escape/evil.class", Layer.L1, FileClass.CLASS,
                a.getSize(), a.getSha256(), a.getSrc());
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        m.put("../escape/evil.class", evil);
        PackageSnapshot newSnap = new PackageSnapshot(war, snap.getType(), null, m);

        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "../escape/evil.class");

        Path outDir = Files.createTempDirectory("bdexp-slip");
        boolean threw = false;
        try {
            new AssetExporter().exportDiffClasses(r, newSnap, newSnap, outDir);
        } catch (IOException e) {
            threw = e.getMessage() != null && e.getMessage().contains("Zip Slip");
        }
        Asserts.assertTrue("越界写盘应被拒绝(Zip Slip)", threw);
    }

    public void testExportDiffJars() throws IOException {
        Path war = TestFixtures.writePackage(TestFixtures.makeWar(warWithThirdParty(), "1"), "exp-jar");
        PackageParser parser = new PackageParser();
        PackageSnapshot snap = parser.parse(war, new ParseConfig(), false);
        LogicalEntry tp = snap.getEntries().get("WEB-INF/lib/third-party.jar");
        Asserts.assertNotNull("third-party.jar 应存在", tp);

        DiffResult r = new DiffResult(new LinkedHashMap<>(), new LinkedHashMap<>());
        r.put(DiffStatus.MODIFIED, "WEB-INF/lib/third-party.jar");
        Map<String, LogicalEntry> m = new LinkedHashMap<>();
        m.put("WEB-INF/lib/third-party.jar", tp);
        PackageSnapshot newSnap = new PackageSnapshot(war, snap.getType(), null, m);

        Path outDir = Files.createTempDirectory("bdexp-jar");
        Path jarsDir = new AssetExporter().exportDiffJars(r, newSnap, newSnap, outDir);
        Path target = jarsDir.resolve("WEB-INF_lib_third-party.jar");
        Asserts.assertTrue("差异 jar 应导出", Files.exists(target));
    }

    public void testExportDecompiledSources_topK() throws IOException {
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        decompiled.put("a.class", new DecompiledUnit("a.class", "o", "n", "da", "cfr", "", true));
        decompiled.put("b.class", new DecompiledUnit("b.class", "o", "n", "db", "cfr", "", true));
        decompiled.put("c.class", new DecompiledUnit("c.class", "o", "n", "dc", "cfr", "", true));

        Path outDir = Files.createTempDirectory("bdexp-src");
        Path zip = new AssetExporter().exportDecompiledSources(decompiled, outDir, 2);
        Asserts.assertTrue("zip 应生成", Files.exists(zip));

        int entries = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) entries++;
        }
        // Top-K=2 -> 2 个 .java + 1 个 MANIFEST.txt = 3
        Asserts.assertEquals("zip 应包含 2 源码 + 1 清单", 3, entries);
    }
}
