package com.bempdiff.config;

import java.util.Arrays;
import java.util.List;

/** 解析配置（§5.1 / FR9.5）。对应 prototype: INTERNAL_PREFIXES 等默认值。 */
public final class ParseConfig {
    /** 命中这些前缀的 lib jar 视为内部业务码，展开其 class 为 L1（§5.2.1） */
    private List<String> internalPrefixes = Arrays.asList("com/", "cn/");
    /** L1：是否展开内部 lib（默认 true） */
    private boolean expandInternalLib = true;
    /** L2：是否展开第三方 lib（默认 false，仅 jar 级） */
    private boolean expandThirdPartyLib = false;
    /** 普通 jar 比对时，是否把所有 class 当 L1 展开（同构件两版场景，--expand-all） */
    private boolean expandAllForPlainJar = false;
    /** 单条目大小上限，超过则跳过内容读取（防 OOM） */
    private long maxEntryBytes = 64L * 1024 * 1024;

    public List<String> getInternalPrefixes() {
        return internalPrefixes;
    }

    public void setInternalPrefixes(List<String> internalPrefixes) {
        this.internalPrefixes = internalPrefixes;
    }

    public boolean isExpandInternalLib() {
        return expandInternalLib;
    }

    public void setExpandInternalLib(boolean v) {
        this.expandInternalLib = v;
    }

    public boolean isExpandThirdPartyLib() {
        return expandThirdPartyLib;
    }

    public void setExpandThirdPartyLib(boolean v) {
        this.expandThirdPartyLib = v;
    }

    public boolean isExpandAllForPlainJar() {
        return expandAllForPlainJar;
    }

    public void setExpandAllForPlainJar(boolean v) {
        this.expandAllForPlainJar = v;
    }

    public long getMaxEntryBytes() {
        return maxEntryBytes;
    }

    public void setMaxEntryBytes(long v) {
        this.maxEntryBytes = v;
    }
}
