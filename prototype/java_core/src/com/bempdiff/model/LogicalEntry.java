package com.bempdiff.model;

import java.util.Objects;

/**
 * 单条逻辑文件（prototype: build_logical_entries 的 entries[k]）。
 * 注意：为支持 3 万级 war 流式解析，本类只存 sha256 + size + src 定位，
 * 不缓存整条目字节（详见 parse 模块路径映射表与按需 readEntryBytes）。
 */
public final class LogicalEntry {
    private final String key;          // 命名空间唯一键
    private final Layer layer;
    private final FileClass fileClass;
    private final long size;
    private final String sha256;
    private final EntrySource src;

    public LogicalEntry(String key, Layer layer, FileClass fileClass, long size, String sha256, EntrySource src) {
        this.key = key;
        this.layer = layer;
        this.fileClass = fileClass;
        this.size = size;
        this.sha256 = sha256;
        this.src = src;
    }

    public String getKey() {
        return key;
    }

    public Layer getLayer() {
        return layer;
    }

    public FileClass getFileClass() {
        return fileClass;
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    public EntrySource getSrc() {
        return src;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogicalEntry)) return false;
        LogicalEntry e = (LogicalEntry) o;
        return layer == e.layer && fileClass == e.fileClass && size == e.size
                && Objects.equals(key, e.key) && Objects.equals(sha256, e.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, layer, fileClass, size, sha256);
    }
}
