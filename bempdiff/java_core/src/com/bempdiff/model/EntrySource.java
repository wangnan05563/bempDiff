package com.bempdiff.model;

/**
 * 反编译/提取定位信息（prototype: _extract_bytes 的 src）。
 * outerEntry：外层 zip 内条目（jar 自身，或 war 内 lib/<x>.jar）。
 * innerEntry：若为内嵌 jar 中的 class，则为内部路径；否则 null（该文件本身就是外层条目）。
 */
public final class EntrySource {
    private final String outerEntry;
    private final String innerEntry;

    public EntrySource(String outerEntry, String innerEntry) {
        this.outerEntry = outerEntry;
        this.innerEntry = innerEntry;
    }

    public String getOuterEntry() {
        return outerEntry;
    }

    public String getInnerEntry() {
        return innerEntry;
    }

    /** 该文件字节是否位于内嵌 jar 内部 */
    public boolean isNested() {
        return innerEntry != null;
    }
}
