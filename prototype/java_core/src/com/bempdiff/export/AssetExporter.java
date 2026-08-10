package com.bempdiff.export;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 差异资产导出（T13，FR6）。对应需求 §FR6 / 详细设计 §5.6。
 *  - exportDiffClasses：复制 ADDED/DELETED/MODIFIED 的 class 字节（保持命名空间）
 *  - exportDiffJars：复制变化的 lib/*.jar（L2 变化）
 *  - exportDecompiledSources：Top-K 完整源码 + manifest 清单（zip）
 */
public final class AssetExporter {

    private final PackageParser parser = new PackageParser();

    /** ① 差异 class 目录：仅复制变化的 class（保持命名空间路径），返回输出目录。 */
    public Path exportDiffClasses(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                  Path outDir) throws IOException {
        Path classesDir = outDir.resolve("diff-classes");
        Files.createDirectories(classesDir);
        for (DiffStatus st : new DiffStatus[]{DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.MODIFIED}) {
            for (String k : r.get(st)) {
                exportSingleClass(k, st, oldSnap, newSnap, classesDir);
            }
        }
        return classesDir;
    }

    private void exportSingleClass(String k, DiffStatus st, PackageSnapshot oldSnap,
                                   PackageSnapshot newSnap, Path classesDir) throws IOException {
        LogicalEntry oe = oldSnap.getEntries().get(k);
        LogicalEntry ne = newSnap.getEntries().get(k);
        if (!isClassEntry(oe) && !isClassEntry(ne)) {
            return;
        }
        // 删除类用老包；其余用新包（新增/修改）
        LogicalEntry srcEntry = (st == DiffStatus.DELETED) ? oe : ne;
        PackageSnapshot srcSnap = (st == DiffStatus.DELETED) ? oldSnap : newSnap;
        if (srcEntry == null) {
            return;
        }
        byte[] bytes = parser.readEntryBytes(srcSnap, srcEntry);
        // 越界防护（双保险）：即使解析层漏过恶意条目名，写盘前也要确认落在 diff-classes/ 内
        Path target = classesDir.resolve(k).normalize();
        Path classesDirNorm = classesDir.toAbsolutePath().normalize();
        if (!target.toAbsolutePath().normalize().startsWith(classesDirNorm)) {
            throw new IOException("拒绝越界写文件（Zip Slip）: " + k);
        }
        Files.createDirectories(target.getParent());
        Path statusFile = target.getParent().resolve("." + st.name() + "." + target.getFileName());
        Files.write(target, bytes);
        // 标记状态（避免同名覆盖歧义，供人工核对）
        Files.write(statusFile, (st.name() + " " + k).getBytes(StandardCharsets.UTF_8));
    }

    /** ② 差异 jar 目录：仅复制变化的 lib/*.jar（L2 变化）。 */
    public Path exportDiffJars(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap,
                               Path outDir) throws IOException {
        Path jarsDir = outDir.resolve("diff-jars");
        Files.createDirectories(jarsDir);
        for (DiffStatus st : new DiffStatus[]{DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.MODIFIED}) {
            for (String k : r.get(st)) {
                exportSingleJar(k, st, oldSnap, newSnap, jarsDir);
            }
        }
        return jarsDir;
    }

    private void exportSingleJar(String k, DiffStatus st, PackageSnapshot oldSnap,
                                 PackageSnapshot newSnap, Path jarsDir) throws IOException {
        if (!k.endsWith(".jar") || !k.contains("/lib/")) {
            return;
        }
        LogicalEntry ne = newSnap.getEntries().get(k);
        LogicalEntry oe = oldSnap.getEntries().get(k);
        LogicalEntry srcEntry = (st == DiffStatus.DELETED) ? oe : ne;
        PackageSnapshot srcSnap = (st == DiffStatus.DELETED) ? oldSnap : newSnap;
        if (srcEntry == null) {
            return;
        }
        byte[] bytes = parser.readEntryBytes(srcSnap, srcEntry);
        Path target = jarsDir.resolve(k.replace("/", "_"));
        Files.write(target, bytes);
    }

    /** ③ 反编译源码 zip：Top-K 完整源码 + manifest 清单。 */
    public Path exportDecompiledSources(Map<String, DecompiledUnit> decompiled, Path outDir,
                                        int topK) throws IOException {
        Files.createDirectories(outDir);
        Path zip = outDir.resolve("decompiled-sources.zip");
        int shown = 0;
        try (OutputStream fos = Files.newOutputStream(zip);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            StringBuilder manifest = new StringBuilder();
            manifest.append("# 反编译源码导出清单（Top-K=").append(topK).append("）\n");
            manifest.append("# key | engine | ok\n");
            for (Map.Entry<String, DecompiledUnit> e : decompiled.entrySet()) {
                if (shown >= topK) break;
                DecompiledUnit u = e.getValue();
                String name = e.getKey().replace("/", "_") + ".java";
                zos.putNextEntry(new ZipEntry(name));
                String content;
                if (u.getNewSource() != null) {
                    content = u.getNewSource();
                } else if (u.getOldSource() != null) {
                    content = u.getOldSource();
                } else {
                    content = "// 反编译失败：" + u.getError();
                }
                zos.write(content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                manifest.append(e.getKey()).append(" | ").append(u.getEngine())
                        .append(" | ").append(u.isOk()).append("\n");
                shown++;
            }
            zos.putNextEntry(new ZipEntry("MANIFEST.txt"));
            zos.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zip;
    }

    private static boolean isClassEntry(LogicalEntry e) {
        return e != null && e.getFileClass() == FileClass.CLASS && e.getLayer() == Layer.L1;
    }
}
