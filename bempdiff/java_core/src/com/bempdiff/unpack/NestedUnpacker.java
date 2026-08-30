package com.bempdiff.unpack;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 多线程逐层物理展开引擎（M-A Task2）。
 *
 * <p>目标：把快照内的"叶子容器"（未被展开过的 {@code FileClass.ARCHIVE/JAR}）递归解压，
 * 将内部原子文件按 {@code 容器key/内层路径} 平面化追加进 entries，使 {@code DiffEngine} 的差异统计与
 * AI 候选覆盖递归子文件。每个叶子容器作为一个任务提交到有界线程池（并行），容器内层递归展开；
 * 等待屏障用 {@code Future.get(timeout)}：超时回收未完成任务并标记 {@link UnpackReport#incomplete}，
 * 绝不提前放行后续 AI 阶段。</p>
 *
 * <p>原子文件真实写盘（物理平铺）：{@code EntrySource} 指向临时磁盘文件，`readEntryBytes` 磁盘直读，
 * 保证多级嵌套内容无需逐级 ZipFile 二次开启。累计解压字节用 {@link UnpackOptions#totalBytesCap}
 * 做 zip bomb 集群防御。</p>
 */
public final class NestedUnpacker {
    /** 单条目硬上限，防御单条 zip bomb。与 PackageParser/ParseConfig 共用同一来源（默认 256MB），
     *  保持解析层与解包层上限一致，避免顶层/嵌套口径分裂。 */
    private static final long HARD_CAP = ParseConfig.DEFAULT_ENTRY_CAP_BYTES;

    /**
     * 解包进度回调：收到「已完成 root 容器数 / 待解包 root 总数」，供上层同步到 job 进度。
     * 并发解包下的累加上报由调用方自行节流（flatten 内部保证计数线程安全，回调可空）。
     */
    public interface UnpackProgress {
        void onProgress(int done, int total);
    }

    private final UnpackOptions opts;
    private final Path tempRoot;

    /** 内存态原子单文件大小上限（字节）：超过则落盘，避免大文件驻留内存。 */
    private static final int MEMO_MAX_BYTES = 64 * 1024;
    /** 全局内存态原子累计软上限：超过后后续小文件自动回退磁盘，保证 RAM 有界（防长会话累积）。 */
    private static final long GLOBAL_MEMO_CAP = 256L * 1024 * 1024;
    private static final AtomicLong globalMemoBytes = new AtomicLong();

    /**
     * 是否把小文件原子装载到内存（省磁盘、减少落盘垃圾）。小文件且全局内存未超上限才返回 true；
     * 返回 true 时由调用方用内存态 EntrySource，false 则落盘。给单测：可传小/大字节分别验证。
     */
    public static boolean tryMemoize(byte[] data) {
        if (data == null || data.length > MEMO_MAX_BYTES) return false;
        return globalMemoBytes.addAndGet(data.length) <= GLOBAL_MEMO_CAP;
    }

    public NestedUnpacker(UnpackOptions opts, Path tempRoot) {
        this.opts = (opts == null) ? new UnpackOptions() : opts;
        try {
            this.tempRoot = (tempRoot != null) ? tempRoot : Files.createTempDirectory("bempdiff-unpack");
            Files.createDirectories(this.tempRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建解包临时目录", e);
        }
    }

    /**
     * 对快照做物理平铺展开，返回含递归原子文件的新快照；未命中容器则原样返回（报告收尾）。
     */
    public PackageSnapshot flatten(PackageSnapshot snap, UnpackReport report) {
        return flatten(snap, report, null);
    }

    /** 带进度回调的解包：{@code progress} 为空时退化为无回调版本（保持原有调用方/测试兼容）。
     *  <p>约定：返回快照的条目顺序不做保证（内部经 {@code ConcurrentHashMap} 并发摊平后重建），
     *  依赖稳定顺序的下游必须自行排序；DiffEngine 已用 {@code TreeSet} 归序，故不依赖此序。</p> */
    public PackageSnapshot flatten(PackageSnapshot snap, UnpackReport report, UnpackProgress progress) {
        Map<String, LogicalEntry> src = snap.getEntries();
        List<String> roots = new ArrayList<>();
        for (Map.Entry<String, LogicalEntry> e : src.entrySet()) {
            FileClass fc = e.getValue().getFileClass();
            if ((fc == FileClass.ARCHIVE || fc == FileClass.JAR) && !hasChildPrefix(src, e.getKey())) {
                roots.add(e.getKey());
            }
        }
        if (roots.isEmpty()) {
            report.finish();
            return snap;
        }

        ConcurrentHashMap<String, LogicalEntry> out = new ConcurrentHashMap<>(src);
        AtomicLong bytes = new AtomicLong();
        // 已解包完成的 root 容器数：并发 flatten 结束后按 total 回调，供上层做节流进度上报。
        AtomicInteger doneRoots = new AtomicInteger();
        int totalRoots = roots.size();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, opts.threadPoolSize));
        report.setThreadCount(opts.threadPoolSize);
        List<Future<?>> futures = new ArrayList<>();
        for (String root : roots) {
            LogicalEntry rootEntry = src.get(root);
            futures.add(pool.submit(() -> {
                try {
                    byte[] rootBytes = new PackageParser().readEntryBytes(snap, rootEntry); // 读源包内该容器字节
                    expand(snap, root, rootBytes, null, 0, out, report, bytes);
                } catch (Exception ex) {
                    report.addError(new UnpackError(root, "root.read", msg(ex)));
                } finally {
                    if (progress != null) progress.onProgress(doneRoots.incrementAndGet(), totalRoots);
                }
            }));
        }
        // —— 等待屏障 ——：阻塞直至全部解包任务在整体 deadline 内完成；超时不提前放行后续 AI，仅标记部分完成。
        // 关键：每个 future 等待「剩余整体预算」，而非被 perItemTimeout 单独截断。perItemTimeout 视为单个根容器的
        // 平摊预算；若对每个 future 都 cap 在 perItemTimeout，则一个展开上百子文件的深层 webapp WAR 会过早超时，
        // 该 root 及其余 root 均被截断，造成展开不完整并诱发海量假 DEL/ADD（非确定性）。整体 deadline 仍是防挂死的护栏。
        long budget = opts.perItemTimeoutMs * Math.max(1L, roots.size());
        long deadline = System.currentTimeMillis() + budget;
        waitBarrier: {
            try {
                for (Future<?> f : futures) {
                    long remain = deadline - System.currentTimeMillis();
                    if (remain <= 0) {
                        report.markIncomplete();
                        break;
                    }
                    try {
                        f.get(Math.max(1, remain), TimeUnit.MILLISECONDS);
                    } catch (TimeoutException te) {
                        report.markIncomplete();
                        break;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                report.markIncomplete();
                break waitBarrier; // 中断应立即放弃，不再逐个等待剩余任务
            } catch (ExecutionException ee) {
                report.markIncomplete();
                break waitBarrier; // 某根容器任务已异常退出：后续任务再等也无完整结果，及时止损，避免空等
            } finally {
                pool.shutdownNow();
            }
        }
        report.finish();
        // 收尾：去除"已被物理平铺展开"的容器键。容器展开后其内部叶子已平面化进入 out，若仍保留容器键，
        // 会造成容器级与叶子级重复计入差异（容器 MOD + 叶子 ADD/DEL），且让差异树把叶子当成容器后代
        // 一切折叠掉——统计(86/84) 与差异树(2/0) 口径分裂（用户反馈）。故仅当容器确未展开（无扁平后代）时保留。
        // 两趟收集消除 O(C×N)：先取出全部归档容器键集合，再一次性过滤，避免对每个容器全表扫描 hasChildPrefix。
        java.util.Set<String> containers = new java.util.HashSet<>();
        for (Map.Entry<String, LogicalEntry> e : out.entrySet()) {
            LogicalEntry v = e.getValue();
            FileClass fc = v.getFileClass();
            if (fc == FileClass.ARCHIVE || fc == FileClass.JAR) containers.add(e.getKey());
        }
        Map<String, LogicalEntry> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, LogicalEntry> e : out.entrySet()) {
            if (containers.contains(e.getKey()) && hasChildPrefix(out, e.getKey())) {
                continue; // 已展开：内部叶子接管，丢弃容器键
            }
            ordered.put(e.getKey(), e.getValue());
        }
        return new PackageSnapshot(snap.getFile(), snap.getType(), snap.getVersion(), ordered);
    }

    /** 快照内是否已有 k+"/" 前缀的条目（说明 k 内部已被展开过，非叶子容器）。 */
    private static boolean hasChildPrefix(Map<String, LogicalEntry> m, String k) {
        String pre = k + "/";
        for (String x : m.keySet()) {
            if (x.startsWith(pre)) return true;
        }
        return false;
    }

    /** 递归展开一个容器：原子文件写磁盘并入 out，内层容器继续下钻（受 maxDepth 约束）。
     *  {@code cBytes}（小容器内存字节）与 {@code cDisk}（大容器流式落盘临时文件）二选一作为容器数据来源，
     *  大容器走磁盘以规避整读大 WAR 的高峰内存；本层 finally 统一回收该容器源临时文件。 */
    private void expand(PackageSnapshot snap, String containerKey, byte[] cBytes, Path cDisk, int depth,
                        ConcurrentHashMap<String, LogicalEntry> out, UnpackReport report, AtomicLong bytes) throws IOException {
        Path cFile;
        long cLen;
        if (cDisk != null) {
            cFile = cDisk;
            cLen = Files.size(cDisk);
        } else {
            cFile = writeTemp(cBytes);
            cLen = cBytes.length;
        }
        if (cLen > HARD_CAP) {
            report.addError(new UnpackError(containerKey, "expand", "容器超过单条上限"));
            if (cDisk == null) try { Files.deleteIfExists(cFile); } catch (IOException ignored) {}
            return;
        }
        if (bytes.addAndGet(cLen) > opts.totalBytesCap) {
            // 累计解压总量超限：停止下钻，容器本身保留为原子条目，避免磁盘耗尽。
            report.markIncomplete();
            out.putIfAbsent(containerKey, diskAtom(containerKey, cFile));
            return;
        }
        if (depth > opts.maxDepth) {
            // 深度护栏触顶：容器降级为原子条目保留（不丢失结构），不继续递归。
            out.putIfAbsent(containerKey, diskAtom(containerKey, cFile));
            report.addSuccess(containerKey);
            return;
        }
        try (ZipFile zf = new ZipFile(cFile.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String inner = sanitize(e.getName(), containerKey);
                String childKey = containerKey + "/" + inner;
                // 比对级忽略扩展名：扁平化为原子文件前过滤（如 MANIFEST.MF），与解析阶段口径一致
                if (ParseConfig.ignoredExt(childKey, opts.ignoreExtensions)) continue;
                FileClass fc = classify(inner);
                byte[] data = null;
                Path disk = null;
                if (e.getSize() > MEMO_MAX_BYTES || e.getSize() < 0) {
                    // 大条目（或声明 size 未知）：流式解压到临时文件，避免整读进内存抬高峰值。
                    disk = streamEntryToDisk(zf, e);
                    if (disk == null) {
                        report.addError(new UnpackError(childKey, "expand", "条目读取超限或失败"));
                        continue;
                    }
                } else {
                    data = readAll(zf, e);
                    if (data == null) {
                        report.addError(new UnpackError(childKey, "expand", "条目读取超限或失败"));
                        continue;
                    }
                }
                long itemLen = (disk != null) ? Files.size(disk) : data.length;
                long add = bytes.addAndGet(itemLen);
                if (add > opts.totalBytesCap) {
                    report.markIncomplete();
                    if (disk != null) try { Files.deleteIfExists(disk); } catch (IOException ignored) {}
                    continue;
                }
                if (fc == FileClass.ARCHIVE || fc == FileClass.JAR) {
                    // 内层仍是容器：递归下钻到原子层（disk 或内存 byte[] 均转交 expand，由其层负责寿命）。
                    expand(snap, childKey, data, disk, depth + 1, out, report, bytes);
                } else {
                    Layer layer = (fc == FileClass.CLASS) ? Layer.L1 : Layer.L0;
                    LogicalEntry le;
                    if (data != null && tryMemoize(data)) {
                        // 小文件走内存（不落盘）：避免大量小原子文件在磁盘累积；超全局上限自动回退磁盘。
                        le = new LogicalEntry(childKey, layer, fc, data.length, sha256(data),
                                EntrySource.memoryBacked(data));
                    } else if (data != null) {
                        Path atom = writeAtom(data);
                        le = new LogicalEntry(childKey, layer, fc, data.length, sha256(data),
                                new EntrySource(atom.toString(), null));
                    } else {
                        disk.toFile().deleteOnExit();
                        le = new LogicalEntry(childKey, layer, fc, itemLen, sha256Of(disk),
                                new EntrySource(disk.toString(), null));
                    }
                    out.putIfAbsent(childKey, le);
                    report.addSuccess(childKey);
                }
            }
        } catch (IOException e) {
            report.addError(new UnpackError(containerKey, "expand", msg(e)));
        } finally {
            // 容器源数据（writeTemp 生成或流式落盘）本层用完后即删，避免临时文件堆积极限磁盘。
            try { Files.deleteIfExists(cFile); } catch (IOException ignored) { }
        }
    }

    /** 深度/总量护栏时把容器降级为可读的磁盘原子条目（保留内容源，便于后续导出/分析读取）。 */
    private LogicalEntry diskAtom(String key, Path file) throws IOException {
        file.toFile().deleteOnExit();
        FileClass fc = classify(key);
        return new LogicalEntry(key, (fc == FileClass.CLASS) ? Layer.L1 : Layer.L0,
                fc, Files.size(file), sha256Of(file), new EntrySource(file.toString(), null));
    }

    private Path writeTemp(byte[] data) throws IOException {
        Path p = Files.createTempFile(tempRoot, "bem-unp-", ".zip");
        Files.write(p, data);
        return p;
    }

    private Path writeAtom(byte[] data) throws IOException {
        Path p = Files.createTempFile(tempRoot, "bem-atom-", ".bin");
        p.toFile().deleteOnExit();
        Files.write(p, data);
        return p;
    }

    /** 把大条目流式解压到临时文件（受 HARD_CAP 字节计数约束，不整读进内存）；溢出/失败返回 null 并清理。 */
    private Path streamEntryToDisk(ZipFile zf, ZipEntry e) {
        long declared = e.getSize();
        if (declared > HARD_CAP) return null;
        Path p = null;
        try (InputStream in = zf.getInputStream(e)) {
            p = Files.createTempFile(tempRoot, "bem-ent-", ".bin");
            try (OutputStream out = Files.newOutputStream(p)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int r;
                while ((r = in.read(buf)) != -1) {
                    total += r;
                    if (total > HARD_CAP) { try { Files.deleteIfExists(p); } catch (IOException ignored) {} return null; }
                    out.write(buf, 0, r);
                }
            }
            return p;
        } catch (IOException ex) {
            if (p != null) try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            return null;
        }
    }

    private static FileClass classify(String name) {
        return PackageParser.classify(name);
    }

    private static byte[] readAll(ZipFile zf, ZipEntry e) {
        long declared = e.getSize();
        if (declared > HARD_CAP) return null;
        try (InputStream in = zf.getInputStream(e)) {
            int hint = (declared > 0 && declared < HARD_CAP) ? (int) declared : 8192;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(hint);
            byte[] buf = new byte[8192];
            long total = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                total += r;
                if (total > HARD_CAP) return null;
                bos.write(buf, 0, r);
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    /** Zip Slip 防御：条目名归一化并拒绝绝对路径/穿越段（与 PackageParser 一致）。 */
    private static String sanitize(String raw, String ctx) {
        String n = raw.replace('\\', '/');
        if (n.startsWith("/") || n.indexOf(':') >= 0 || n.contains("..")) {
            throw new IllegalStateException("拒绝非法/穿越条目名: " + raw + " in " + ctx);
        }
        return n;
    }

    private static String sha256(byte[] data) {
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

    /** 对临时磁盘文件流式计算 SHA-256（大条目落盘后不回读整文件进内存）。 */
    private static String sha256Of(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            }
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算临时文件 SHA-256", e);
        }
    }

    private static String msg(Throwable t) {
        String m = (t == null) ? "" : t.getMessage();
        return (m == null || m.isEmpty()) ? String.valueOf(t) : m;
    }
}