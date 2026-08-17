package com.bempdiff.diff;

import java.util.regex.Pattern;

/**
 * 行级 diff 的“忽略不重要差异”规则（P0-②）。
 *  - ignoreWhitespace：移除所有空白（彻底忽略空白差异，含有无空白，对标 Beyond Compare）。
 *  - ignoreComments：剥离“整行注释”行（// 或 # 开头、单行块注释、HTML 注释单行、* javadoc 续行）；
 *    不剥离行内注释，避免误伤 http 协议地址等 URL 与字符串字面量。
 *  - ignoreRegex：用户自定义正则，命中的子串从行中移除（高级项；正则非法时自动忽略，不使比对崩溃）。
 *
 * 归一化仅用于“行匹配”（决定两行是否视为相同），diff 输出仍显示原始内容（符合 Beyond Compare 的忽略语义）。
 */
public final class DiffRules {

    public static final DiffRules DEFAULT = new DiffRules(false, false, null);

    private final boolean ignoreWhitespace;
    private final boolean ignoreComments;
    private final String ignoreRegex;
    private final Pattern ignorePattern;

    private DiffRules(boolean ignoreWhitespace, boolean ignoreComments, String ignoreRegex) {
        this.ignoreWhitespace = ignoreWhitespace;
        this.ignoreComments = ignoreComments;
        this.ignoreRegex = ignoreRegex; // 保留 null（无规则）；空串由下面忽略
        Pattern p = null;
        if (this.ignoreRegex != null && !this.ignoreRegex.isEmpty()) {
            try {
                p = Pattern.compile(this.ignoreRegex);
            } catch (Exception e) {
                p = null; // 正则非法：忽略该规则，不使比对崩溃
            }
        }
        this.ignorePattern = p;
    }

    public static DiffRules of(boolean ignoreWhitespace, boolean ignoreComments, String ignoreRegex) {
        return new DiffRules(ignoreWhitespace, ignoreComments, ignoreRegex);
    }

    public boolean isIgnoreWhitespace() { return ignoreWhitespace; }
    public boolean isIgnoreComments() { return ignoreComments; }
    public String getIgnoreRegex() { return ignoreRegex; }
    public boolean hasCustom() { return ignoreWhitespace || ignoreComments || (ignorePattern != null); }

    /** 归一化单行（仅用于行匹配；DEFAULT 下逐字符原样返回，零回归）。 */
    public String normalize(String line) {
        if (line == null) return "";
        String s = line;
        if (ignoreWhitespace) {
            s = s.replaceAll("\\s+", ""); // 移除所有空白：彻底忽略空白差异（含有无），对标 Beyond Compare
        }
        if (ignoreComments) {
            s = stripWholeLineComment(s);
        }
        if (ignorePattern != null) {
            s = ignorePattern.matcher(s).replaceAll("");
        }
        return s;
    }

    /** 仅剥离“整行注释”，不触碰行内注释（安全：避免 http:// 等误伤）。 */
    private static String stripWholeLineComment(String line) {
        String t = line.trim();
        if (t.isEmpty()) return line;
        if (t.startsWith("//") || t.startsWith("#")) return "";
        if (t.startsWith("/*") && t.endsWith("*/")) return "";
        if (t.startsWith("<!--") && t.endsWith("-->")) return "";
        if (t.startsWith("*")) return ""; // javadoc / 块注释续行
        return line;
    }
}
