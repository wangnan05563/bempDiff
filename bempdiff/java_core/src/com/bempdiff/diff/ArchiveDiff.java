package com.bempdiff.diff;

import com.bempdiff.model.DecompiledUnit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 归档压缩包"清单 diff"服务。
 *
 * <p>典型场景：用户比较两个文件夹，差异树中出现一个 .zip/.war/.ear/.tar.gz
 * （如"BEMP5.0-adapterV202301-02-036M061(20260707-1135).zip"，4.5 MB）。
 * 此前该类文件被归为 {@link com.bempdiff.model.FileClass#OTHER}，点击时
 * {@link BempServer#handleEntry} 走 {@link FrontendTextDiff}，尝试把整个
 * 归档的二进制当文本解码、再做 JS 美化，4 MB+ 字节在状态机里空转 ——
 * 前端一直卡在"正在反编译/美化源码…"。</p>
 *
 * <p>本类把归档当作"目录"处理：对两个归档枚举 zip 条目
 * （name + size + CRC + 标记目录），按条目名排序后做行级 diff，
 * 输出与 class 反编译同构的 {@link DecompiledUnit}。优点：
 *  - 即时（枚举条目是 O(n) 且只读中央目录，4.5 MB zip 通常 &lt; 50 ms）；
 *  - 可读（用户看到新增/删除/修改了哪些内部文件）；
 *  - 不进入 AI 管线（提示不参与内容级分析，节省 token）。
 * </p>
 *
 * <p>对 .tar/.tar.gz（gzip 压缩 tar）JDK 无内置解析器：
 * 当前实现若文件不是 zip/jar/war/ear 结构则安全降级为"清单不可读"
 * 的失败响应，不会抛异常把整条 entry 路由拖死。后续如需支持 tar，
 * 可引入 Apache Commons Compress 扩展本类。</p>
 */
public final class ArchiveDiff {

    private static final Logger LOG = Logger.getLogger(ArchiveDiff.class.getName());

    /** 单侧条目数硬上限：防止恶意/异常归档条目爆炸把 diff 文本撑爆。 */
    static final int MAX_ENTRIES_PER_SIDE = 50_000;
    /** 单条目名字符串硬上限：极端长名截断，避免 listing 越界。 */
    private static final int MAX_NAME_LEN = 512;

    public ArchiveDiff() { /* 公共无参构造，与 Decompiler/FrontendTextDiff 风格一致 */ }

    /**
     * 对单个归档文件做"清单 + 行级 diff"。
     *
     * @param key     DiffTree 中的 key（与 handleEntry 透传）
     * @param oldPath 老侧归档路径；null 表示该侧不存在（视为空）
     * @param newPath 新侧归档路径；null 表示该侧不存在（视为空）
     * @return DecompiledUnit：
     *         - ok=true：oldSource/newSource 是各自归档的条目清单（按 name 排序），
     *           diffText 是它们的行级 unified diff；engine="archive-listing"
     *         - ok=false：归档损坏/非 zip 结构 → 返回 fail(key, msg)，前端可显示错误
     *           而非无限 spinner
     */
    public DecompiledUnit diff(String key, Path oldPath, Path newPath) {
        if (oldPath == null && newPath == null) {
            return DecompiledUnit.fail(key, "归档两侧都为空");
        }
        try {
            String oldListing = (oldPath != null) ? readListing(oldPath) : null;
            String newListing = (newPath != null) ? readListing(newPath) : null;
            String diff;
            if (oldListing == null) {
                // 老侧无此归档时，早前已保证两侧非全空，故 newListing 必非 null（直接拼接）
                diff = "// [新增文件] 老侧无此归档\n" + newListing;
            } else if (newListing == null) {
                diff = "// [删除文件] 新侧无此归档（资源移除，需确认引用方）\n" + oldListing;
            } else {
                diff = LineDiff.unified(oldListing, newListing, DiffRules.DEFAULT);
            }
            // 短哈希：归档（zip/jar/war/ear）元数据拼串。无文件 mtime 可用，仅 path+size，区别两侧身份
            String oldHash = (oldPath != null)
                    ? com.bempdiff.util.ShortHash.ofPathAndSize(key, java.nio.file.Files.size(oldPath))
                    : "0000000";
            String newHash = (newPath != null)
                    ? com.bempdiff.util.ShortHash.ofPathAndSize(key, java.nio.file.Files.size(newPath))
                    : "0000000";
            return new DecompiledUnit(key, oldListing, newListing, diff,
                    "archive-listing", "", true, oldHash, newHash);
        } catch (Exception e) {
            // 损坏/非 zip 结构/IO 错误：返回失败而不是抛异常冒泡到调用方
            // （否则前端 spinner 会一直转，违反"不再卡死"承诺）。
            LOG.log(Level.WARNING, e, () -> "[ArchiveDiff] 归档清单解析失败: " + key);
            return DecompiledUnit.fail(key, "归档清单不可读（非 zip 结构或已损坏）: " + e.getMessage());
        }
    }

    /**
     * 读取归档条目清单。
     * 格式：每行一个条目，列固定宽度便于行级 diff
     *   {@code <size padded>  <crc32 padded>  <directory? d/- > <name>}
     * 按 name 排序后输出（与目录中文件序无关，diff 稳定可读）。
     */
    static String readListing(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("归档文件不存在或不是常规文件: " + file);
        }
        List<String> lines = new ArrayList<>();
        try (ZipFile zf = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            int count = 0;
            while (en.hasMoreElements()) {
                if (count++ >= MAX_ENTRIES_PER_SIDE) {
                    // 截断：把"还有更多"标在末尾，避免恶意归档把 listing 撑到内存上限
                    lines.add("... (已截断，超出 " + MAX_ENTRIES_PER_SIDE + " 条上限)");
                    break;
                }
                ZipEntry e = en.nextElement();
                lines.add(formatEntry(e));
            }
        } catch (IOException ioe) {
            // 非 zip 结构（tar/tar.gz 暂未支持）→ 让调用方走 fail 分支
            throw new IOException("无法作为 zip 读取（可能非 zip 结构或已损坏）: " + ioe.getMessage(), ioe);
        }
        Collections.sort(lines);
        return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n");
    }

    private static String formatEntry(ZipEntry e) {
        long size = e.getSize();
        long crc = e.getCrc();
        boolean dir = e.isDirectory();
        String name = e.getName();
        if (name.length() > MAX_NAME_LEN) {
            name = name.substring(0, MAX_NAME_LEN) + "...";
        }
        // CRC: zip 规范中目录条目 CRC 为 0；用 8 位十六进制补零便于列对齐
        String crcHex = (crc >= 0) ? String.format("%08x", crc) : "--------";
        // size: zip 规范中目录/未确定条目 size 可能为 -1
        String sizeStr = (size >= 0) ? String.format("%10d", size) : "         -";
        return sizeStr + "  " + crcHex + "  " + (dir ? "d " : "- ") + name;
    }

    /**
     * 仅暴露给测试：把 zip 字节流读到 listing（用于无需落盘的小 fixture）。
     */
    public static String readListingFromBytes(byte[] zipBytes) throws IOException {
        Path tmp = Files.createTempFile("bempdiff-archdiff-", ".zip");
        try {
            Files.write(tmp, zipBytes);
            return readListing(tmp);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {
                // 清理失败静默：仅测试用临时文件，残留不影响结果
            }
        }
    }

    /** 仅测试用：判别 Path 是否是 zip 类归档（不抛异常的"能否打开"探测）。 */
    public static boolean isZip(Path file) {
        if (file == null || !Files.isRegularFile(file)) return false;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] sig = new byte[4];
            int n = in.read(sig);
            if (n < 4) return false;
            // zip 中央目录条目起始 magic: PK\x03\x04 (0x50 0x4B 0x03 0x04)
            return sig[0] == 0x50 && sig[1] == 0x4B && sig[2] == 0x03 && sig[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }
}
