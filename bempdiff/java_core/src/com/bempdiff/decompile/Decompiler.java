package com.bempdiff.decompile;

import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.diff.DiffRules;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.OutputSinkFactory.Sink;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkClass;
import org.benf.cfr.reader.api.OutputSinkFactory.SinkType;
import org.benf.cfr.reader.api.SinkReturns.Decompiled;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 反编译集成（T07/T08，§5.3 / §5.4）。对应 prototype: decompile.py
 *  - CFR（生产首选）优先；失败降级 javap（JDK 自带，签名级）
 *  - GBK 容错解码：CFR 在有中文常量的 class 上按系统编码输出 stdout（prototype 已踩坑，Java 侧同形）
 *  - 对 L1 修改/新增/删除类做双栏源码 diff（统一格式，供双栏视图 / AI 深读）
 *
 * 性能修复（P0-1，见性能测试报告 §6）：
 *  - 旧实现对每个 class 用 `new ProcessBuilder(java -jar cfr.jar).waitFor()` 起**全新 JVM 子进程**，
 *    逐类冷启动 ~1.4s/类 + 每子进程重读大 jar → 并发 I/O/CPU 争用、亚线性扩展（实测 T16 无收益、外部并发 3.8× 退化）。
 *  - 新实现：当 cfr.jar 位于运行时 classpath 上时，改用 **CFR 进程内 API（CfrDriver）**，纯进程内反编译、无子进程、无冷启动；
 *    由调用方线程池并行驱动即可线性扩展。运行时若检测不到 CfrDriver（cfr.jar 未上 classpath），
 *    自动回退到原 ProcessBuilder 方案，保证真实产品行为零回归。
 */
public final class Decompiler {

    private final Path cfrJar;   // 可能为 null（仅用 javap / 或走进程内 CFR）
    private final String javaBin;

    /** cfr.jar 在运行时 classpath 上、进程内 CFR API 可用时为 true；否则回退 ProcessBuilder。 */
    private final boolean inProcessCfrAvailable;

    private static final String JAVAP = "javap";

    /** P1-1 反编译缓存：sha256(class bytes) → 源码，access-order LRU max 256，避免重复 CFR。 */
    private static final int MAX_DECOMPILE_CACHE = 256;
    private final Map<String, String> decompileCache =
            Collections.synchronizedMap(new LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_DECOMPILE_CACHE;
                }
            });

    public Decompiler(Path cfrJar, String javaBin) {
        this.cfrJar = cfrJar;
        this.javaBin = javaBin;
        boolean avail;
        try {
            Class.forName("org.benf.cfr.reader.api.CfrDriver", false,
                    Decompiler.class.getClassLoader());
            avail = true;
        } catch (Throwable t) {
            avail = false;
        }
        this.inProcessCfrAvailable = avail;
    }

    /** 反编译单个 .class 字节，返回完整源码（prototype: decompile.decompile）。
     *  P1-1：sha256(bytes) 命中缓存则直接返回，否则 CFR 并写入缓存。 */
    private String decompileOne(byte[] classBytes) throws IOException {
        String cacheKey = sha256Hex(classBytes);
        String cached = decompileCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        File tmp = File.createTempFile("bempdiff-cls-", ".class");
        // 注：finally 中已显式删除，无需 deleteOnExit（避免长生命周期 GUI 累积路径引用）。
        Files.write(tmp.toPath(), classBytes);
        try {
            String result;
            if (inProcessCfrAvailable) {
                try {
                    result = cfrInProcess(tmp);
                } catch (Exception cfrFail) {
                    result = "// [降级] CFR 进程内失败：" + cfrFail.getMessage() + "；已回退 javap\n" + javap(tmp);
                }
            } else if (cfrJar != null && cfrJar.toFile().isFile()) {
                try {
                    result = cfr(tmp, cfrJar);
                } catch (IOException cfrFail) {
                    result = "// [降级] CFR 失败：" + cfrFail.getMessage() + "；已回退 javap\n" + javap(tmp);
                }
            } else {
                result = javap(tmp);
            }
            decompileCache.put(cacheKey, result);
            return result;
        } finally {
            Files.delete(tmp.toPath());
        }
    }

    /**
     * 进程内 CFR 反编译（P0-1 核心修复）。
     * 用 CfrDriver 直接在宿主 JVM 内反编译，捕获 DECOMPILED 输出，无子进程、无冷启动。
     * 每次调用自建一个 driver 实例（CFR 非线程安全共享实例），由调用方线程池并行驱动以线性扩展。
     */
    private String cfrInProcess(File classFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        OutputSinkFactory sinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> sinkClasses) {
                if (sinkType == SinkType.JAVA) {
                    return Collections.singletonList(SinkClass.DECOMPILED);
                }
                return Collections.emptyList();
            }

            @Override
            public Sink<Decompiled> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return new Sink<Decompiled>() {
                        @Override
                        public void write(Decompiled d) {
                            // DECOMPILED 槽投递 SinkReturns.Decompiled（非 String），取 getJava() 得源码
                            sb.append(d.getJava());
                        }
                    };
                }
                return null;
            }
        };
        CfrDriver driver = new CfrDriver.Builder()
                .withOutputSink(sinkFactory)
                .build();
        driver.analyse(Collections.singletonList(classFile.getAbsolutePath()));
        String out = sb.toString();
        if (out.trim().isEmpty()) {
            throw new IOException("CFR 进程内返回空");
        }
        return out;
    }

    private String cfr(File classFile, Path cfrJar) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-jar", cfrJar.toString(), classFile.getAbsolutePath(),
                "--sugarenums", "false", "--hideutf", "false", "--silent", "true");
        // P1-3：stderr 并入 stdout 单流读取，消除双管道缓冲满导致的进程死锁（CFR 子进程）。
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            String out = decode(readAll(p.getInputStream()));
            int code = p.waitFor();
            if (code != 0 || out.trim().isEmpty()) {
                throw new IOException(out.trim().isEmpty() ? "CFR 返回空" : out.trim());
            }
            return out;
        } catch (InterruptedException ie) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(ie);
        } finally {
            if (p.isAlive()) p.destroyForcibly();   // 防止子进程（CFR/java）泄漏
        }
    }

    private String javap(File classFile) throws IOException {
        // 注意：javaBin 指向 java 启动器；javap 是同目录下的独立可执行文件，需另行推导，
        // 不可直接复用 javaBin（否则等价于 `java -p -c`，会报 "-p requires module path specification"）。
        ProcessBuilder pb = new ProcessBuilder(javapBin(), "-p", "-c", classFile.getAbsolutePath());
        // P1-3：stderr 并入 stdout 单流读取，消除双管道死锁（javap 子进程）。
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            String out = decode(readAll(p.getInputStream()));
            int code = p.waitFor();
            if (code != 0) throw new IOException(out.trim().isEmpty() ? "javap 失败" : out.trim());
            return "// [降级] javap 签名级反编译（环境无 CFR）\n" + out;
        } catch (InterruptedException ie) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(ie);
        } finally {
            if (p.isAlive()) p.destroyForcibly();   // 防止子进程（javap/java）泄漏
        }
    }

    /** 对单个 key 做双栏源码 diff（默认规则）。
     *  先取出两侧 class 字节，再委托 {@link #decompileBytes(byte[], byte[], String, DiffRules)} 完成反编译与 diff，避免逻辑重复。 */
    public DecompiledUnit decompile(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                    LogicalEntry oldEntry, LogicalEntry newEntry, String key) {
        return decompile(oldSnap, newSnap, oldEntry, newEntry, key, DiffRules.DEFAULT);
    }

    /** 对单个 key 做双栏源码 diff（P0-②：带忽略规则）。 */
    public DecompiledUnit decompile(PackageSnapshot oldSnap, PackageSnapshot newSnap,
                                    LogicalEntry oldEntry, LogicalEntry newEntry, String key, DiffRules rules) {
        try {
            byte[] oldBytes = (oldEntry != null) ? readBytes(oldSnap, oldEntry) : null;
            byte[] newBytes = (newEntry != null) ? readBytes(newSnap, newEntry) : null;
            return decompileBytes(oldBytes, newBytes, key, rules);
        } catch (IOException e) {
            return DecompiledUnit.fail(key, e.getMessage());
        } catch (Exception e) {
            return DecompiledUnit.fail(key, e.toString());
        }
    }

    /** 直接对原始 class 字节做反编译并双栏源码 diff（供 LibJarDiff 对 lib jar 内部 class 使用；默认规则）。 */
    public DecompiledUnit decompileBytes(byte[] oldBytes, byte[] newBytes, String key) {
        return decompileBytes(oldBytes, newBytes, key, DiffRules.DEFAULT);
    }

    /** 直接对原始 class 字节做反编译并双栏源码 diff（带忽略规则）。
     *  任一側字节为 null 表示该侧不存在（新增类/删除类），与 decompile() 语义一致。 */
    public DecompiledUnit decompileBytes(byte[] oldBytes, byte[] newBytes, String key, DiffRules rules) {
        try {
            String oldSrc = (oldBytes != null) ? decompileOne(oldBytes) : null;
            String newSrc = (newBytes != null) ? decompileOne(newBytes) : null;
            String diff = unifiedDiff(oldSrc, newSrc, rules);
            boolean degraded = (oldSrc != null && oldSrc.startsWith("// [降级]"))
                    || (newSrc != null && newSrc.startsWith("// [降级]"));
            String engine = degraded ? JAVAP : (inProcessCfrAvailable ? "cfr(in-process)" : "cfr");
            return new DecompiledUnit(key, oldSrc, newSrc, diff, engine, "", true);
        } catch (IOException e) {
            return DecompiledUnit.fail(key, e.getMessage());
        } catch (Exception e) {
            return DecompiledUnit.fail(key, e.toString());
        }
    }

    private byte[] readBytes(PackageSnapshot snap, LogicalEntry entry) throws IOException {
        // 复用 PackageParser.readEntryBytes（同包下可见性为包级，这里用反射/公开方法均可；
        // 简化：直接复制解析器逻辑调用其 public 方法）
        return new com.bempdiff.parse.PackageParser().readEntryBytes(snap, entry);
    }

    /** 从 java 启动器路径推导同目录的 javap 可执行文件路径（Windows 为 javap.exe）。 */
    private String javapBin() {
        String j = javaBin;
        if (j != null && j.endsWith("java.exe")) {
            return j.substring(0, j.length() - "java.exe".length()) + "javap.exe";
        }
        if (j != null && j.endsWith("java")) {
            return j.substring(0, j.length() - 4) + "javap";
        }
        return "javap"; // 兜底：依赖 PATH
    }

    /** 生成统一格式 diff 文本（默认规则，等价于旧行为）。 */
    private static String unifiedDiff(String oldSrc, String newSrc) {
        return unifiedDiff(oldSrc, newSrc, DiffRules.DEFAULT);
    }

    /** 生成统一格式 diff 文本（P0-②：带忽略规则）。 */
    private static String unifiedDiff(String oldSrc, String newSrc, DiffRules rules) {
        List<String> a = oldSrc == null ? new ArrayList<>() : lines(oldSrc);
        List<String> b = newSrc == null ? new ArrayList<>() : lines(newSrc);
        StringBuilder sb = new StringBuilder();
        if (oldSrc == null) sb.append("// [新增类] 老包无此文件\n");
        if (newSrc == null) sb.append("// [删除类] 新包无此文件（潜在破坏性变更）\n");
        // 简单 LCS 行级 diff（够用，不引入第三方库）
        List<String> diff = simpleDiff(a, b, rules);
        for (String line : diff) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private static List<String> lines(String s) {
        return Arrays.asList(s.split("\n", -1));
    }

    /** 轻量行级 diff（LCS），输出带 +/- 前缀的合并视图（默认规则）。 */
    private static List<String> simpleDiff(List<String> a, List<String> b) {
        return simpleDiff(a, b, DiffRules.DEFAULT);
    }

    /** 轻量行级 diff（LCS），带忽略规则：归一化行用于匹配，输出仍为原始行（P0-②）。 */
    private static List<String> simpleDiff(List<String> a, List<String> b, DiffRules rules) {
        int n = a.size();
        int m = b.size();
        List<String> ak = new ArrayList<>(n);
        List<String> bk = new ArrayList<>(m);
        for (String s : a) ak.add(rules.normalize(s));
        for (String s : b) bk.add(rules.normalize(s));
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                dp[i][j] = ak.get(i).equals(bk.get(j)) ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
        List<String> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (ak.get(i).equals(bk.get(j))) {
                out.add("  " + a.get(i)); // 输出原始行
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add("- " + a.get(i++));
            } else {
                out.add("+ " + b.get(j++));
            }
        }
        while (i < n) out.add("- " + a.get(i++));
        while (j < m) out.add("+ " + b.get(j++));
        return out;
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    /** P1-1：sha256 字节数组，返回 hex 字符串（用作反编译缓存键）。 */
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** CFR/javap 中文常量按系统编码输出：utf-8 -> gbk -> 容错（prototype: _decode） */
    private static String decode(byte[] bs) {
        for (String enc : new String[]{StandardCharsets.UTF_8.name(), "gbk", "latin-1"}) {
            try {
                return new String(bs, enc);
            } catch (Exception e) {
                // 尝试下一种编码
            }
        }
        try {
            return new String(bs, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return new String(bs); // 最后兜底
        }
    }
}
