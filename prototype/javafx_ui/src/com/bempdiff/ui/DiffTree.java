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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    /**
     * 差异树过滤条件：按变更类型勾选显示 + 文件名模糊/正则搜索。
     * 所有开关默认开启（=显示全部），与历史默认行为一致。
     */
    public static final class TreeFilter {
        public boolean showModified = true;
        public boolean showAdded = true;
        public boolean showDeleted = true;
        public boolean showUnchanged = true;
        public String search = "";
        public boolean regex = false;

        private Pattern compiled;
        private String error;

        /** 预编译搜索条件；正则非法时 compiled=null 且 error 非空（此时 matchesSearch 退化为匹配全部，避免误隐藏）。 */
        public void compileSearch() {
            compiled = null;
            error = null;
            if (search != null) search = search.trim(); // 去除首尾空白，避免 " x" 匹配不到 "x"
            if (search == null || search.isBlank()) return;
            if (regex) {
                try {
                    compiled = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException ex) {
                    error = "正则语法错误: " + ex.getDescription();
                }
            }
        }

        public String error() { return error; }

        public boolean statusEnabled(DiffStatus st) {
            switch (st) {
                case MODIFIED:   return showModified;
                case ADDED:      return showAdded;
                case DELETED:    return showDeleted;
                case UNCHANGED:  return showUnchanged;
                default:         return true;
            }
        }

        /** 文件名（含路径）是否通过搜索条件：空搜索匹配全部；正则用 find；模糊用不区分大小写子串。 */
        public boolean matchesSearch(String key) {
            if (search == null || search.isBlank()) return true;
            if (compiled != null) {
                // 正则：对完整路径（含目录）匹配，与模糊模式语义一致（用户常输入含 / 的路径片段）
                return compiled.matcher(key).find();
            }
            if (regex && error != null) return true; // 正则非法：不应用过滤，避免误隐藏
            return key.toLowerCase().contains(search.toLowerCase());
        }
    }

    /**
     * 构建差异文件树。
     * @param filter 过滤条件（按变更类型勾选 + 文件名搜索）；传 null 等价于显示全部。
     * 目录节点惰性创建（仅被通过过滤的叶子引用时才生成），故空目录自动剪枝。
     */
    @SuppressWarnings("unused")
    public static TreeResult build(DiffResult diff, PackageSnapshot oldSnap, PackageSnapshot newSnap, TreeFilter filter) {
        if (filter != null) filter.compileSearch();
        TreeItem<String> root = new TreeItem<>("差异文件树");
        Map<String, TreeItem<String>> dirCache = new HashMap<>();
        Map<String, DiffStatus> statusMap = new LinkedHashMap<>();
        Map<String, String> layerMap = new LinkedHashMap<>();

        DiffStatus[] order = {DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED, DiffStatus.UNCHANGED};
        for (DiffStatus st : order) {
            if (filter != null && !filter.statusEnabled(st)) {
                continue; // 该变更类型未勾选：跳过
            }
            for (String key : diff.get(st)) {
                if (filter != null && !filter.matchesSearch(key)) {
                    continue; // 不匹配搜索条件
                }
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
