package com.bempdiff.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 包文件名的智能版本识别与排序工具。
 *
 * <p>支持两类版本形态（取文件名去扩展名后「最右端」的版本段）：
 *  - 点分式：{@code app-1.6.1.war} → {@code 1.6.1}、{@code v2.0.1-SNAPSHOT} → {@code 2.0.1-SNAPSHOT}
 *  - 构建号/时间戳式：{@code BEMP5.0-adapterV202301-02-036M061(20260707-1135).zip}
 *    → {@code 036M061(20260707-1135)}（数字+字母+括号时间戳，可带 -/ _/ . 分隔）
 *
 * <p>「同名不同版本」判定采用两两比较的公共前缀法（比正则更稳）：
 * 两个文件名去扩展名后公共前缀 ≥4 字符、剩余尾段都形似版本（含数字且只含
 * 数字/字母/括号/._-）、且尾段不同 → 视为同一包的不同版本。此时按
 * {@link #compare} 版本序（数字段数值优先、字母段字典序、逐段比较）自动排
 * 旧→新，供「智能识别后自动比对」使用。</p>
 */
public final class PackageVersion {

    private PackageVersion() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 点分式版本（可带 v 前缀与 -SNAPSHOT 等后缀），匹配到即优先采用。 */
    private static final Pattern DOTTED =
            Pattern.compile("[Vv]?(\\d+(?:\\.\\d+)*+(?:[-_][A-Za-z0-9]+)?)$");

    /** 构建号/时间戳式尾段：字母前缀 + 数字 + 字母数字混排 + 可选括号时间戳 + 可选 - 数字。 */
    private static final Pattern BUILD_TAIL = Pattern.compile(
            "([A-Za-z]*\\d{2,}[A-Za-z]*\\d*(?:\\([^()]*\\))?(?:[-_][A-Za-z0-9]+)?)$");

    /** 版本尾段合法字符集（用于「形似版本」判定）。 */
    private static final String VERSION_TAIL_CHARS = "[A-Za-z0-9()._\\-]+";

    /** 版本尾段最短长度（公共前缀法的安全下限）。 */
    private static final int MIN_COMMON_PREFIX = 4;

    /** 从文件名提取版本号（用于展示/报告；提取不到返回 null）。 */
    public static String extractFromFileName(String fileName) {
        if (fileName == null) return null;
        String stem = stripExtension(fileName);
        Matcher dm = DOTTED.matcher(stem);
        if (dm.find()) return dm.group(1);
        // 构建号式：find() 从左往右，取最后一个匹配（最右端的版本段）
        Matcher bm = BUILD_TAIL.matcher(stem);
        String best = null;
        while (bm.find()) best = bm.group(1);
        return best;
    }

    /** 两个文件名是否为「同一包的不同版本」（同基名 + 版本尾段不同）。 */
    public static boolean sameBaseDifferentVersion(String fileA, String fileB) {
        if (fileA == null || fileB == null || fileA.equals(fileB)) return false;
        String sa = stripExtension(fileName(fileA));
        String sb = stripExtension(fileName(fileB));
        if (sa.equals(sb)) return false;
        String lcp = longestCommonPrefix(sa, sb);
        if (lcp.length() < MIN_COMMON_PREFIX) return false;
        String ra = sa.substring(lcp.length());
        String rb = sb.substring(lcp.length());
        if (ra.isEmpty() || rb.isEmpty()) return false;
        if (!looksVersionTail(ra) || !looksVersionTail(rb)) return false;
        return !ra.equals(rb);
    }

    /**
     * 版本排序（数字段数值优先、字母段忽略大小写字典序、逐段比较；a 旧于 b 返回负数）。
     * 适用于点分式与构建号式两种形态的混排比较。
     */
    public static int compare(String a, String b) {
        String va = (a == null) ? "" : a;
        String vb = (b == null) ? "" : b;
        List<String> ta = tokenize(va);
        List<String> tb = tokenize(vb);
        int n = Math.max(ta.size(), tb.size());
        for (int i = 0; i < n; i++) {
            String x = (i < ta.size()) ? ta.get(i) : "";
            String y = (i < tb.size()) ? tb.get(i) : "";
            int c = compareToken(x, y);
            if (c != 0) return c;
        }
        return 0;
    }

    /**
     * 智能配对：两个文件若为同名不同版本，返回按版本升序的 {旧, 新}；否则原样返回 {a, b}。
     * 供 CLI/HTTP 入口统一使用，保证「自动比对」方向一致。
     */
    public static String[] orderOldNew(String a, String b) {
        if (sameBaseDifferentVersion(a, b)
                && compare(extractFromFileName(a), extractFromFileName(b)) > 0) {
            return new String[]{b, a};
        }
        return new String[]{a, b};
    }

    // ----------------------------- 工具 -----------------------------

    /** 取路径的文件名部分（兼容 Windows 反斜杠）。 */
    static String fileName(String path) {
        if (path == null) return "";
        String n = path.replace('\\', '/');
        int idx = n.lastIndexOf('/');
        return (idx >= 0) ? n.substring(idx + 1) : n;
    }

    /** 去掉最后一级扩展名（.zip/.war/.jar/…）。 */
    static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot > name.lastIndexOf('/') && dot > name.lastIndexOf('\\')) {
            return name.substring(0, dot);
        }
        return name;
    }

    /** 两字符串公共前缀。 */
    static String longestCommonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return a.substring(0, i);
    }

    /** 尾段是否形似版本：含数字且只由 数字/字母/括号/._- 组成。 */
    static boolean looksVersionTail(String t) {
        if (t == null || t.isEmpty()) return false;
        return t.matches(".*\\d.*") && t.matches(VERSION_TAIL_CHARS);
    }

    /** 按「数字段 / 非数字段」交替切分（用于逐段比较）。 */
    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean d = Character.isDigit(c);
            if (cur.length() > 0 && d != digit) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            digit = d;
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static int compareToken(String x, String y) {
        boolean xn = isNumeric(x), yn = isNumeric(y);
        if (xn && yn) {
            // 数字段：数值比较（忽略前导零长度差）
            return Long.compare(Long.parseLong(x), Long.parseLong(y));
        }
        if (xn) return -1; // 数字段 < 字母段
        if (yn) return 1;
        int c = x.compareToIgnoreCase(y);
        return (c != 0) ? c : x.compareTo(y);
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
