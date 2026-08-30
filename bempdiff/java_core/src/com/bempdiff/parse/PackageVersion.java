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
        if (dm.find()) {
            // 点分式匹配若紧跟在 '(' 之后（如 …036M059(20260703-1104)），说明那只是括号内的构建时间戳，
            // 并非独立的点分版本；真正的版本是前置的构建号尾段（036M059(20260703-1104)）。
            // 若不跳过 DOTTED，版本会被截断成时间戳，剥离尾段后同一包的两侧基名（M059/M061）不同，
            // 被判为不同包——自动排序与状态栏版本识别随之失效（用户 BEMP5.0V202301-02-036M0xx 场景）。
            int gs = dm.start();
            if (gs == 0 || stem.charAt(gs - 1) != '(') {
                return dm.group(1);
            }
            String build = lastBuildTail(stem);
            return (build != null) ? build : dm.group(1); // 罕见无构建尾段时回退旧行为
        }
        return lastBuildTail(stem);
    }

    /** 取最右端（最后）命中 BUILD_TAIL 的版本尾段；无命中返回 null。 */
    private static String lastBuildTail(String stem) {
        Matcher bm = BUILD_TAIL.matcher(stem);
        String best = null;
        while (bm.find()) best = bm.group(1);
        return best;
    }

    /**
     * 两个文件名是否为「同一包的不同版本」。
     *
     * <p>判定比纯公共前缀法更严格：先各自提取最右端版本尾段，剥离后**基名必须完全相同**。
     * 仅凭公共前缀 ≥4 字符会把不同组件名误判为同基名——例如
     * {@code BEMP5.0-adapterV202301-02-036M059(20260703-1104).zip} 与
     * {@code BEMP5.0-cpesmqV202301-02-036M061(20260707-1135).zip} 共享前缀 {@code BEMP5.0-}（≥4 字符），
     * 但组件名（adapter/cpesmq）不同，属于不同包；若被误判，归档自动配对时会在一侧命中多个候选而放弃配对。
     * 只有版本尾段不同且其余基名完全一致，才视为同一包的不同版本。</p>
     */
    public static boolean sameBaseDifferentVersion(String fileA, String fileB) {
        if (fileA == null || fileB == null || fileA.equals(fileB)) return false;
        String sa = stripExtension(fileName(fileA));
        String sb = stripExtension(fileName(fileB));
        if (sa.equals(sb)) return false;
        // 版本提取须基于原始文件名（含扩展名），由 extractFromFileName 内部剥离扩展名；
        // 若传已去扩展名的主干串（如 BEMP5.0-...），其内部的点号会被误判为扩展名边界，
        // 导致版本被截断（如提取成 "5"），从而误判不配对。
        String va = extractFromFileName(fileA);
        String vb = extractFromFileName(fileB);
        if (va != null && vb != null) {
            if (va.equals(vb)) return false;
            String baseA = stripVersionTail(sa, va);
            String baseB = stripVersionTail(sb, vb);
            return baseA != null && baseB != null
                    && baseA.length() >= MIN_COMMON_PREFIX && baseA.equals(baseB);
        }
        // 版本段提取失败（罕见形态）：回退公共前缀法，保持原有保守行为
        String lcp = longestCommonPrefix(sa, sb);
        if (lcp.length() < MIN_COMMON_PREFIX) return false;
        String ra = sa.substring(lcp.length());
        String rb = sb.substring(lcp.length());
        if (ra.isEmpty() || rb.isEmpty()) return false;
        if (!looksVersionTail(ra) || !looksVersionTail(rb)) return false;
        return !ra.equals(rb);
    }

    /** 从串尾剥离版本段 v；v 不在末尾返回 null（无法安全剥离，不冒险配对）。 */
    static String stripVersionTail(String s, String v) {
        if (s.endsWith(v)) return s.substring(0, s.length() - v.length());
        return null;
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
