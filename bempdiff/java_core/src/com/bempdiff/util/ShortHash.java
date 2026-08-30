package com.bempdiff.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 7 位短哈希（类比 <code>git rev-parse --short=7</code>）。
 *
 * <p>供 {@code DecompiledUnit.oldHash / newHash} 字段使用，前端 filebar 渲染
 * <code>85e03b9 ↔ 1e4a0c</code> 风格徽标。
 *
 * <p>输入约定：
 * <ul>
 *   <li>{@link #ofBytes(byte[])}：对 class 原始字节做 SHA-1 → 取前 7 hex。</li>
 *   <li>{@link #ofMeta(String, long, long)}：对 path + size + mtime 拼串做 SHA-1 → 7 hex（文本类）。</li>
 *   <li>{@link #ofPathAndSize(String, long)}：对 path + size 拼串做 SHA-1 → 7 hex（归档/Office 等无 mtime 场景）。</li>
 * </ul>
 *
 * <p>空输入回退 {@code "0000000"}，便于前端「缺失侧」展示与判别。
 */
public final class ShortHash {

    /** 一侧缺失时的占位哈希。 */
    public static final String EMPTY = "0000000";

    private static final int LEN = 7;

    private ShortHash() {}

    /** 原始字节 → 7 位 hex。null/空数组回退 {@link #EMPTY}。 */
    public static String ofBytes(byte[] data) {
        if (data == null || data.length == 0) return EMPTY;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            // 4 字节 → 8 hex → 取前 7。SHA-1 头 4 字节已有 ~2^32 种取值，碰撞概率与 git --short=7 一致
            StringBuilder sb = new StringBuilder(LEN);
            for (int i = 0; i < 4; i++) {
                String h = String.format("%02x", d[i] & 0xff);
                sb.append(h);
            }
            return sb.substring(0, LEN);
        } catch (NoSuchAlgorithmException e) {
            return EMPTY;
        }
    }

    /** 文本类：path + size + mtime 拼串 → 7 位 hex。任一关键字段为 null 时回退 {@link #EMPTY}。 */
    public static String ofMeta(String path, long size, long mtime) {
        if (path == null) return EMPTY;
        // 用 "|" 隔避免 "a/1" 与 "a1/" 拼到同一结果；size/mtime 用 Long.toString 避免 Locale
        String key = path + "|" + size + "|" + mtime;
        return ofBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    /** 归档/Office 等无 mtime：path + size 拼串 → 7 位 hex。 */
    public static String ofPathAndSize(String path, long size) {
        if (path == null) return EMPTY;
        return ofMeta(path, size, 0L);
    }
}
