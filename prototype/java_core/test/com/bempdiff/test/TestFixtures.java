package com.bempdiff.test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试夹具：生成真实可反编译的 .class 字节，并构造受控的 war/jar 包，
 * 使单元测试能对任意场景做精确断言（含嵌套 lib、版本信息、Zip Slip 条目等）。
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** 用进程内 javac 把源码编译为真实 .class 字节（供反编译/解析夹具使用）。 */
    public static byte[] compileClass(String fqcn, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("无可用 javac（需完整 JDK）");
        Path work = Files.createTempDirectory("bdfix-compile");
        Path srcFile = work.resolve(fqcn.replace('.', '/') + ".java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, source, StandardCharsets.UTF_8);
        Path outDir = work.resolve("out");
        Files.createDirectories(outDir);
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> units = fm.getJavaFileObjects(srcFile);
            boolean ok = compiler.getTask(null, fm, null,
                    List.of("-d", outDir.toString(), "-encoding", "UTF-8"), null, units).call();
            if (!ok) throw new IllegalStateException("编译失败: " + fqcn);
        }
        Path cls = outDir.resolve(fqcn.replace('.', '/') + ".class");
        return Files.readAllBytes(cls);
    }

    /** 把 条目名->字节 的内存映射打成 zip（jar/war 本质都是 zip）。 */
    public static byte[] makeZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    /** 构造 war：自动写入 MANIFEST（含 Implementation-Version），再叠加其余条目。 */
    public static byte[] makeWar(Map<String, byte[]> entries, String version) throws IOException {
        Map<String, byte[]> all = new TreeMap<>();
        StringBuilder mf = new StringBuilder("Manifest-Version: 1.0\r\n");
        if (version != null) mf.append("Implementation-Version: ").append(version).append("\r\n");
        mf.append("\r\n");
        all.put("META-INF/MANIFEST.MF", mf.toString().getBytes(StandardCharsets.UTF_8));
        all.putAll(entries);
        return makeZip(all);
    }

    /** 把字节落盘为临时包文件，返回 Path（PackageParser.parse 需要 Path）。 */
    public static Path writePackage(byte[] data, String prefix) throws IOException {
        Path dir = Files.createTempDirectory("bdfix-pkg");
        Path p = dir.resolve(prefix + ".bin");
        Files.write(p, data);
        return p;
    }

    // ---- 预置真实 class 源码（含中文常量，用于验证 GBK 容错解码）----

    public static final String SRC_A_V1 = """
        package com.internal;
        public class A {
            private String name = "票据名称V1";
            public int add(int a, int b) { return a + b; }
        }
        """;

    public static final String SRC_A_V2 = """
        package com.internal;
        public class A {
            private String name = "票据名称V2";
            public int add(int a, int b) { return a + b + 1; }
            public void newMethod() {}
        }
        """;

    public static final String SRC_B = """
        package com.internal;
        public class B {
            public String hello() { return "hi"; }
        }
        """;

    public static final String SRC_X = """
        package com.other;
        public class X {
            public int value = 42;
        }
        """;

    public static final String SRC_T = """
        package org.apache;
        public class T {
            public void run() {}
        }
        """;
}
