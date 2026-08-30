package com.bempdiff.model;

/**
 * 反编译/提取定位信息（prototype: _extract_bytes 的 src）。
 * outerEntry：外层 zip 内条目（jar 自身，或 war 内 lib/<x>.jar）。
 * innerEntry：若为内嵌 jar 中的 class，则为内部路径；否则 null（该文件本身就是外层条目）。
 */
public final class EntrySource {
    private final String outerEntry;
    private final String innerEntry;
    /** 内存态原子字节：非空表示该条目字节直接驻留内存（NestedUnpacker 对小文件的内存优化），读取免落盘。 */
    private final byte[] memory;

    public EntrySource(String outerEntry, String innerEntry) {
        this(outerEntry, innerEntry, null);
    }

    private EntrySource(String outerEntry, String innerEntry, byte[] memory) {
        this.outerEntry = outerEntry;
        this.innerEntry = innerEntry;
        this.memory = memory;
    }

    /** 内存态条目构造（免磁盘：小文件的原子字节直接装载到内存，读取走 readEntryBytes 时优先返回）。 */
    public static EntrySource memoryBacked(byte[] bytes) {
        return new EntrySource(null, null, bytes);
    }

    public String getOuterEntry() {
        return outerEntry;
    }

    public String getInnerEntry() {
        return innerEntry;
    }

    /** 是否内存态条目。 */
    public boolean isMemoryBacked() {
        return memory != null;
    }

    /** 内存态字节（仅当 {@link #isMemoryBacked()} 为 true 时有值）。 */
    public byte[] getMemoryBytes() {
        return memory;
    }

    /** 该文件字节是否位于内嵌 jar 内部 */
    public boolean isNested() {
        return innerEntry != null;
    }
}
