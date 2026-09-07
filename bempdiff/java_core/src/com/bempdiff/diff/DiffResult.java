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
    /**
     * 版本改名配对（旧侧 key → 新侧 key）：DiffEngine 在把「版本等价路径 + 内容不同」的文件
     * 配对为 MODIFIED 时记录。内容级 diff / AI / 报告按 MODIFIED 的 key（旧侧路径）取新侧内容时，
     * 必须先经此映射翻译成新侧 key，否则 newSnap 取不到对应条目而误判为「整文件删除」（用户实测）。
     * 反向映射（新侧 key → 旧侧 key）供差异树以「新包名」展示后，内容层按新 key 反查旧侧条目。
     */
    private final Map<String, String> renamedToNew = new LinkedHashMap<>();
    private final Map<String, String> newToOld = new LinkedHashMap<>();

    /** 记录一条改名配对（旧侧 key → 新侧 key），供内容层按旧 key 反查新侧条目。 */
    public void recordRename(String oldKey, String newKey) {
        if (oldKey != null && newKey != null) {
            renamedToNew.put(oldKey, newKey);
            newToOld.put(newKey, oldKey);
        }
    }

    /** 按旧侧 key 反查配对后的新侧 key；未配对返回 null。 */
    public String newKeyFor(String oldKey) {
        return (oldKey == null) ? null : renamedToNew.get(oldKey);
    }

    /** 按新侧 key 反查配对前的旧侧 key（差异树以新包名展示后，内容层按新 key 反查旧侧条目）；未配对返回 null。 */
    public String oldKeyFor(String newKey) {
        return (newKey == null) ? null : newToOld.get(newKey);
    }

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
