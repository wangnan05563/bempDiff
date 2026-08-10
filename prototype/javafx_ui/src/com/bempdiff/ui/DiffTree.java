package com.bempdiff.ui;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由 DiffResult 构建差异文件树（按路径层级）。叶子节点的 value 即完整 key（供 cell 着色与选中回查），
 * layerMap 提供 [L0/L1/L2] 展示标签。TreeItem 无 userData，故用 value 承载 key。
 */
public final class DiffTree {

    private DiffTree() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static final class TreeResult {
        public final TreeItem<String> root;
        public final Map<String, DiffStatus> statusMap;
        public final Map<String, String> layerMap;
        public TreeResult(TreeItem<String> root, Map<String, DiffStatus> statusMap, Map<String, String> layerMap) {
            this.root = root; this.statusMap = statusMap; this.layerMap = layerMap;
        }
    }

    @SuppressWarnings("unused")
    public static TreeResult build(DiffResult diff, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        TreeItem<String> root = new TreeItem<>("差异文件树");
        Map<String, TreeItem<String>> dirCache = new HashMap<>();
        Map<String, DiffStatus> statusMap = new LinkedHashMap<>();
        Map<String, String> layerMap = new LinkedHashMap<>();

        DiffStatus[] order = {DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.UNCHANGED};
        for (DiffStatus st : order) {
            for (String key : diff.get(st)) {
                statusMap.put(key, st);
                String layer = layerOf(key, oldSnap, newSnap);
                layerMap.put(key, layer);
                String[] parts = key.split("/");
                TreeItem<String> parent = root;
                StringBuilder acc = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    acc.append(parts[i]).append("/");
                    String dirKey = acc.toString();
                    final int idx = i;
                    TreeItem<String> d = dirCache.computeIfAbsent(dirKey, k -> new TreeItem<>(parts[idx]));
                    if (d.getParent() == null) {
                        parent.getChildren().add(d);
                    }
                    parent = d;
                }
                // 叶子 value = 完整 key（选中回查用）
                TreeItem<String> leaf = new TreeItem<>(key);
                parent.getChildren().add(leaf);
            }
        }
        root.setExpanded(true);
        return new TreeResult(root, statusMap, layerMap);
    }

    private static String layerOf(String key, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        LogicalEntry e = oldSnap.getEntries().get(key);
        if (e == null) e = newSnap.getEntries().get(key);
        if (e == null) return "?";
        return e.getLayer().name();
    }

    /** 取破坏性变更（删除的 L1 class）——对应 FR4.7 / FR6.4。 */
    public static List<String> destructiveChanges(DiffResult diff, PackageSnapshot oldSnap) {
        List<String> out = new ArrayList<>();
        for (String key : diff.get(DiffStatus.DELETED)) {
            LogicalEntry e = oldSnap.getEntries().get(key);
            if (e != null && e.getFileClass() == com.bempdiff.model.FileClass.CLASS
                    && e.getLayer() == com.bempdiff.model.Layer.L1) {
                out.add(key);
            }
        }
        return out;
    }
}
