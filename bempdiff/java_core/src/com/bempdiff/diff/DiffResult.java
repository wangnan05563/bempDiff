package com.bempdiff.diff;

import com.bempdiff.model.LogicalEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 差异结果（prototype: compute_diff）。状态 -> key 列表（保持排序）。 */
public final class DiffResult {
    private final Map<DiffStatus, List<String>> byStatus = new LinkedHashMap<>();
    private final Map<String, LogicalEntry> oldEntries;   // 供 UI/导出回填 layer
    private final Map<String, LogicalEntry> newEntries;

    public DiffResult(Map<String, LogicalEntry> oldEntries, Map<String, LogicalEntry> newEntries) {
        this.oldEntries = oldEntries;
        this.newEntries = newEntries;
        for (DiffStatus s : DiffStatus.values()) byStatus.put(s, new ArrayList<>());
    }

    public void put(DiffStatus s, String key) {
        byStatus.get(s).add(key);
    }

    /**
     * 一次性用新集合替换某状态下的 key 列表。
     * 供版本对齐在计算完成后统一重建 DELETED/ADDED，避免循环内对 List 反复 remove（O(N)）造成整体 O(N²)。
     */
    public void replaceList(DiffStatus s, Collection<String> keys) {
        byStatus.put(s, new ArrayList<>(keys));
    }

    public Map<DiffStatus, List<String>> getByStatus() {
        return byStatus;
    }

    public List<String> get(DiffStatus s) {
        return byStatus.get(s);
    }

    public Map<String, LogicalEntry> getOldEntries() {
        return oldEntries;
    }

    public Map<String, LogicalEntry> getNewEntries() {
        return newEntries;
    }
}
