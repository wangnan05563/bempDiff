package com.bempdiff.server;

import com.bempdiff.config.ParseConfig;

/** 一次比对的选项（由前端请求携带，或取默认值）。 */
public final class CompareOptions {
    public boolean expandAll = false;
    public int topK = 12;
    public String internalPrefixes = "com.hundsun";
    public String cfrJar = ""; // 为空则依赖进程内 CFR（cfr 在 classpath 上时自动启用）

    public static CompareOptions fromRequest(java.util.Map<String, Object> req) {
        CompareOptions o = new CompareOptions();
        Object optsObj = req.get("options");
        if (optsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> opts = (java.util.Map<String, Object>) optsObj;
            o.expandAll = Json.bool(opts, "expandAll", false);
            o.topK = Json.intv(opts, "topK", 12);
            o.internalPrefixes = Json.str(opts, "internalPrefixes", "com.hundsun");
            o.cfrJar = Json.str(opts, "cfrJar", "");
        }
        return o;
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
