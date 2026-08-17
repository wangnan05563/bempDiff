package com.bempdiff.model;

import java.nio.file.Path;
import java.util.Map;

/** 单包解析结果 */
public final class PackageSnapshot {
    private final Path file;
    private final PackageType type;
    private final String version;          // FR1.7 自动提取，可能为 null
    private final Map<String, LogicalEntry> entries;  // key -> entry（保持插入顺序）

    public PackageSnapshot(Path file, PackageType type, String version, Map<String, LogicalEntry> entries) {
        this.file = file;
        this.type = type;
        this.version = version;
        this.entries = entries;
    }

    public Path getFile() {
        return file;
    }

    public PackageType getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, LogicalEntry> getEntries() {
        return entries;
    }

    public int getTotal() {
        return entries.size();
    }
}
