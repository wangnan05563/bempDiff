package com.bempdiff.diff;

import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 差异计算（T05/T06，FR3/FR4）。对应 prototype: compute_diff / compute_stats。
 *  - 全量差异：key 集合对称差 + sha256 比对（MODIFIED 以 sha 不等为准，非 size）
 *  - 删除类：仅老包有 -> DELETED（评审点#5，后续单列为破坏性变更）
 *  - 非文本边界：STATIC/OTHER 仍按 sha 判定 MODIFIED，但内容 diff 交由上层跳过（FR4.8）
 */
public final class DiffEngine {

    public DiffResult compute(PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        Map<String, LogicalEntry> old = oldSnap.getEntries();
        Map<String, LogicalEntry> now = newSnap.getEntries();
        DiffResult r = new DiffResult(old, now);
        Set<String> keys = new TreeSet<>();
        keys.addAll(old.keySet());
        keys.addAll(now.keySet());
        for (String k : keys) {
            LogicalEntry o = old.get(k);
            LogicalEntry n = now.get(k);
            if (o == null && n == null) {
                r.put(DiffStatus.UNCHANGED, k);
            } else if (o == null) {
                r.put(DiffStatus.ADDED, k);
            } else if (n == null) {
                r.put(DiffStatus.DELETED, k);
            } else if (!o.getSha256().equals(n.getSha256())) {
                r.put(DiffStatus.MODIFIED, k);
            } else {
                r.put(DiffStatus.UNCHANGED, k);
            }
        }
        return r;
    }

    public DiffStats stats(DiffResult r) {
        DiffStats s = new DiffStats();
        s.setAdded(r.get(DiffStatus.ADDED).size());
        s.setDeleted(r.get(DiffStatus.DELETED).size());
        s.setModified(r.get(DiffStatus.MODIFIED).size());
        s.setUnchanged(r.get(DiffStatus.UNCHANGED).size());
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                if (k.endsWith(".jar") && k.contains("/lib/")) {
                    s.setJarChanged(s.getJarChanged() + 1);
                } else {
                    s.setBizChanged(s.getBizChanged() + 1);
                }
            }
        }
        return s;
    }

    /**
     * 收集「L1 业务 class 候选」（MODIFIED/ADDED/DELETED 中、层级为 L1 的 class）。
     * 单一事实来源：消除 compare/decompile/report/export/ai 以及 UI 中重复 4+ 遍的同形逻辑。
     * 返回全量候选（不截断），调用方按需 subList / Math.min。
     */
    @SuppressWarnings("unused")
    public static List<String> collectL1ClassCandidates(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        List<String> cands = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                LogicalEntry oe = oldSnap.getEntries().get(k);
                LogicalEntry ne = newSnap.getEntries().get(k);
                boolean isClass = (oe != null && oe.getFileClass() == FileClass.CLASS && oe.getLayer() == Layer.L1)
                        || (ne != null && ne.getFileClass() == FileClass.CLASS && ne.getLayer() == Layer.L1);
                if (isClass) cands.add(k);
            }
        }
        return cands;
    }
}