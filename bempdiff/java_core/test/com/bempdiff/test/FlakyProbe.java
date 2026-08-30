package com.bempdiff.test;

import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.server.CompareOptions;
import com.bempdiff.unpack.NestedUnpacker;
import com.bempdiff.unpack.UnpackOptions;
import com.bempdiff.unpack.UnpackReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 排查 HTTP 服务端 auto-unpack 的 flaky：用与 BempServer 一致的 CompareOptions 默认解包参数，
 * 分别对 old/new 做 flatten，输出 entry 数、含 .war 键数、report.incomplete/errors/耗时，
 * 并跑 DiffEngine 看差异数。参数化 tempRoot（-Dbempdiff.workDir=）与 JRE（本机 java）以便对照。
 */
public final class FlakyProbe {
    public static void main(String[] args) throws Exception {
        String oldZ = System.getProperty("bempdiff.oldZip",
                "E:\\testC\\BEMP5.0V202301-02-036M059(20260703-1104).zip");
        String newZ = System.getProperty("bempdiff.newZip",
                "E:\\testC\\BEMP5.0V202301-02-036M061(20260707-1135).zip");
        String workDir = System.getProperty("bempdiff.workDir",
                "C:/Users/hspcadmin/.bempdiff/runtime/probe");
        Path base = Paths.get(workDir);
        if (Files.exists(base)) {
            try (var st = Files.walk(base)) {
                st.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ig) {} });
            }
        }
        Files.createDirectories(base);

        CompareOptions opts = CompareOptions.fromRequest(Map.of("options", Map.of("unpackNested", true)));
        UnpackOptions uo = opts.toUnpackOptions(); // 与 E2eVerify 一致：共用同一份 UnpackOptions
        PackageParser pp = new PackageParser();
        PackageSnapshot os = pp.parse(Paths.get(oldZ), opts.toParseConfig(), false);
        PackageSnapshot ns = pp.parse(Paths.get(newZ), opts.toParseConfig(), false);

        long t0 = System.currentTimeMillis();
        UnpackReport ro = new UnpackReport("old");
        PackageSnapshot fo = new NestedUnpacker(uo, base.resolve("old")).flatten(os, ro, null);
        long t1 = System.currentTimeMillis();
        UnpackReport rn = new UnpackReport("new");
        PackageSnapshot fn = new NestedUnpacker(uo, base.resolve("new")).flatten(ns, rn, null);
        long t2 = System.currentTimeMillis();

        System.out.println("== probe ==");
        System.out.println("old flatten: entries=" + fo.getEntries().size()
                + " 含.war=" + fo.getEntries().keySet().stream().filter(k -> k.contains(".war")).count()
                + " incomplete=" + ro.isIncomplete() + " errors=" + ro.getErrors().size()
                + " fileCount=" + ro.getFileCount() + " ms=" + (t1 - t0));
        String dump = System.getProperty("bempdiff.dumpOld", "");
        if (!dump.isEmpty()) {
            java.util.List<String> lines = fo.getEntries().keySet().stream().sorted()
                    .map(k -> k + "\t" + fo.getEntries().get(k).getSize())
                    .collect(java.util.stream.Collectors.toList());
            Files.write(Paths.get(dump), lines);
            System.out.println("dumped old keys (" + lines.size() + ") -> " + dump);
        }
        System.out.println("new flatten: entries=" + fn.getEntries().size()
                + " 含.war=" + fn.getEntries().keySet().stream().filter(k -> k.contains(".war")).count()
                + " incomplete=" + rn.isIncomplete() + " errors=" + rn.getErrors().size()
                + " fileCount=" + rn.getFileCount() + " ms=" + (t2 - t1));

        DiffEngine engine = new DiffEngine();
        DiffResult dr = engine.compute(fo, fn);
        DiffStats s = engine.stats(dr);
        System.out.println("diff: ADD=" + s.getAdded() + " DEL=" + s.getDeleted()
                + " MOD=" + s.getModified() + " UNCH=" + s.getUnchanged());
        System.out.println("done in " + (System.currentTimeMillis() - t0) + "ms");
    }
}