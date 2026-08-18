package com.bempdiff.server;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.DiffRules;

/** 一次比对的选项（由前端请求携带，或取默认值）。 */
public final class CompareOptions {
    private boolean expandAll = false;
    private int topK = 12;
    private String internalPrefixes = "com.hundsun";
    private String cfrJar = ""; // 为空则依赖进程内 CFR（cfr 在 classpath 上时自动启用）

    // P0-②：忽略不重要差异
    private boolean ignoreWhitespace = false; // 折叠空白（避免纯空白 / 行尾空白噪声）
    private boolean ignoreComments = false;    // 剥离整行注释
    private String ignoreRegex = "";           // 自定义正则（命中子串从行中移除；非法则忽略）

    public boolean isExpandAll() {
        return expandAll;
    }

    public void setExpandAll(boolean expandAll) {
        this.expandAll = expandAll;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public String getInternalPrefixes() {
        return internalPrefixes;
    }

    public void setInternalPrefixes(String internalPrefixes) {
        this.internalPrefixes = internalPrefixes;
    }

    public String getCfrJar() {
        return cfrJar;
    }

    public void setCfrJar(String cfrJar) {
        this.cfrJar = cfrJar;
    }

    public boolean isIgnoreWhitespace() {
        return ignoreWhitespace;
    }

    public void setIgnoreWhitespace(boolean ignoreWhitespace) {
        this.ignoreWhitespace = ignoreWhitespace;
    }

    public boolean isIgnoreComments() {
        return ignoreComments;
    }

    public void setIgnoreComments(boolean ignoreComments) {
        this.ignoreComments = ignoreComments;
    }

    public String getIgnoreRegex() {
        return ignoreRegex;
    }

    public void setIgnoreRegex(String ignoreRegex) {
        this.ignoreRegex = ignoreRegex;
    }

    public static CompareOptions fromRequest(java.util.Map<String, Object> req) {
        CompareOptions o = new CompareOptions();
        Object optsObj = req.get("options");
        if (optsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> opts = (java.util.Map<String, Object>) optsObj;
            o.setExpandAll(Json.bool(opts, "expandAll", false));
            o.setTopK(Json.intv(opts, "topK", 12));
            o.setInternalPrefixes(Json.str(opts, "internalPrefixes", "com.hundsun"));
            o.setCfrJar(Json.str(opts, "cfrJar", ""));
            o.setIgnoreWhitespace(Json.bool(opts, "ignoreWhitespace", false));
            o.setIgnoreComments(Json.bool(opts, "ignoreComments", false));
            o.setIgnoreRegex(Json.str(opts, "ignoreRegex", ""));
        }
        return o;
    }

    /** 由比对选项派生行级 diff 的忽略规则（正则非法时 DiffRules 内部自动忽略）。 */
    public DiffRules toDiffRules() {
        return DiffRules.of(ignoreWhitespace, ignoreComments, ignoreRegex);
    }

    public ParseConfig toParseConfig() {
        ParseConfig c = new ParseConfig();
        c.setInternalPrefixes(java.util.Arrays.asList(internalPrefixes.split("[,;\\s]+")));
        c.setExpandInternalLib(expandAll);
        c.setExpandAllForPlainJar(expandAll);
        c.setMaxEntryBytes(8L * 1024 * 1024);
        return c;
    }
}