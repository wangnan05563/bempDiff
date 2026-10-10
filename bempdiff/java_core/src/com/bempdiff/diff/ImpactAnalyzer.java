package com.bempdiff.diff;

import com.bempdiff.model.DecompiledUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * FR-012 AI 变更影响面分析（静态调用关系 + 风险等级）：
 * 在反编译源码集内构建「谁引用了谁」的引用索引，对实质变更类输出
 * 影响面（受影响类清单 + 证据链）与风险等级，供 AI 提示词与报告消费。
 *
 * <p>风险等级模型（与 ApiCompat 签名分级联动，行为级仍由 AI 两阶段覆盖）：</p>
 * <ul>
 *   <li>CRITICAL：变更类被删除但仍被其它源码引用（调用方必然编译失败）</li>
 *   <li>HIGH：API 移除/签名变更（ApiCompat 判定）且存在引用方</li>
 *   <li>MEDIUM：仅实现变更且被其它类引用（行为可能受影响，需回归）</li>
 *   <li>LOW：无引用方的孤岛变更或纯新增（影响面收敛在本类）</li>
 * </ul>
 *
 * <p>引用判定（轻量可解释，证据链 = 引用方文件与 import 行）：调用方源码中出现
 * 目标类 simpleName（词边界）且满足其一——同包 / import 该类 / 全限定名出现。
 * 反编译源码集是自洽世界：集内引用即包内/跨包依赖的真实子集，误报由证据链人工复核兜底。</p>
 */
public final class ImpactAnalyzer {

    private ImpactAnalyzer() {
    }

    /** 风险等级（CRITICAL > HIGH > MEDIUM > LOW）。 */
    public enum Risk { CRITICAL, HIGH, MEDIUM, LOW }

    /** 单个变更类的影响面条目：风险 + 引用方证据链。 */
    public static final class ImpactItem {
        public final Risk risk;
        public final String changedClass;          // 变更类 FQN（由 key 推导）
        public final String status;                // MODIFIED / DELETED / ADDED
        public final String reason;                // 一句话判定依据（可解释）
        public final List<String> referencedBy = new ArrayList<>(); // 证据链：引用方 key

        ImpactItem(Risk risk, String changedClass, String status, String reason) {
            this.risk = risk;
            this.changedClass = changedClass;
            this.status = status;
            this.reason = reason;
        }
    }

    /** 影响面结果：条目 + 汇总计数 + 渲染。 */
    public static final class Result {
        public final List<ImpactItem> items = new ArrayList<>();
        public int critical, high, medium, low;

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public Risk maxRisk() {
            if (critical > 0) return Risk.CRITICAL;
            if (high > 0) return Risk.HIGH;
            if (medium > 0) return Risk.MEDIUM;
            return Risk.LOW;
        }

        /** 报告章节：影响面清单 + 证据链（与 AI 章节解耦，非 AI 报告同样可读）。 */
        public String toMarkdownSection() {
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("### 变更影响面（静态调用关系）\n\n");
            sb.append("| 风险 | 变更类 | 状态 | 判定依据 | 受影响引用方 |\n|---|---|---|---|---|\n");
            for (ImpactItem it : items) {
                String badge = switch (it.risk) {
                    case CRITICAL -> "**<span class=\"sev-breaking\">CRITICAL</span>**";
                    case HIGH -> "**<span class=\"sev-high\">HIGH</span>**";
                    case MEDIUM -> "**<span class=\"sev-med\">MEDIUM</span>**";
                    case LOW -> "LOW";
                };
                String refs = it.referencedBy.isEmpty() ? "（集内无引用方）"
                        : String.join("<br>", it.referencedBy);
                sb.append("| ").append(badge)
                  .append(" | `").append(it.changedClass).append("`")
                  .append(" | ").append(it.status)
                  .append(" | ").append(it.reason)
                  .append(" | ").append(refs).append(" |\n");
            }
            sb.append("\n> 影响面口径：反编译源码集内引用关系（import/同包 + 名称匹配），")
              .append("行为级影响由两阶段 AI 分析深化。\n\n");
            return sb.toString();
        }

        /** 提示词段（进 AI 阶段A 上下文，验收②）。 */
        public String toPromptSection() {
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("【变更影响面】（静态调用关系判定，供评估波及范围时引用）\n");
            for (ImpactItem it : items) {
                sb.append("- [").append(it.risk).append("] ").append(it.changedClass)
                  .append("（").append(it.status).append("）").append(it.reason);
                if (!it.referencedBy.isEmpty()) {
                    sb.append("；被引用：").append(String.join(", ", it.referencedBy));
                }
                sb.append("\n");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /** key（com/demo/Svc.java）→ FQN（com.demo.Svc）。 */
    static String keyToFqn(String key) {
        return key.endsWith(".java") ? key.substring(0, key.length() - 5).replace('/', '.') : key;
    }

    /** 包名（com.demo.Svc → com.demo）。 */
    static String pkgOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    /** simpleName（com.demo.Svc → Svc；嵌套取最末段）。 */
    static String simpleOf(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** 源码中 package 声明。 */
    static String pkgOfSource(String src) {
        java.util.regex.Matcher m = Pattern.compile("^\\s*package\\s+([\\w.]+)", Pattern.MULTILINE).matcher(src);
        return m.find() ? m.group(1) : "";
    }

    /** 源码 import 的 FQN 集合（不含 static/通配符展开）。 */
    static Set<String> importsOf(String src) {
        Set<String> out = new LinkedHashSet<>();
        java.util.regex.Matcher m = Pattern.compile("^\\s*import\\s+(?!static\\s)([\\w.]+);", Pattern.MULTILINE).matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * 影响面分析：在 decompiled 集内建立引用索引，对 apiCompat 分级条目与
     * 删除类输出影响面。行为级波及（实现变更的语义影响）由 AI 深读覆盖。
     *
     * @param units     反编译候选（key → 双侧源码）
     * @param apiCompat FR-007 静态签名分级（判定 HIGH 的依据；null 视为无签名变更）
     * @param deletedKeys 集内已删除的类 key（DiffResult.DELETED 中以 .java 结尾者）
     */
    public static Result analyze(Map<String, DecompiledUnit> units, ApiCompat.Result apiCompat, List<String> deletedKeys) {
        Result res = new Result();
        if (units == null || units.isEmpty()) return res;

        // 1) 引用索引：类 FQN → 引用它的源码 key 集合（对 old/new 两侧源码都扫，取并集——
        //    变更类的影响方可能只存在于其中一侧）
        Map<String, Set<String>> refIndex = new HashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<String, DecompiledUnit> e : units.entrySet()) {
            DecompiledUnit u = e.getValue();
            if (u == null || !u.isOk()) continue;
            if (u.getOldSource() != null) sources.put(e.getKey() + "#old", u.getOldSource());
            if (u.getNewSource() != null) sources.put(e.getKey() + "#new", u.getNewSource());
        }
        // 待判定类集合 = ApiCompat 条目类 ∪ 删除类 ∪ 反编译集中的修改类候选
        //（decompileTop 只为 MODIFIED/ADDED/DELETED 候选生成 unit，双侧源码俱在即修改类）
        Set<String> targets = new LinkedHashSet<>();
        Map<String, ApiCompat.Level> sigLevel = new HashMap<>();
        if (apiCompat != null) {
            for (ApiCompat.Item it : apiCompat.items) {
                targets.add(it.cls);
                // 同类多级取最严：REMOVED/SIG → 视为签名级
                if (it.level != ApiCompat.Level.ADDED_API) sigLevel.put(it.cls, it.level);
            }
        }
        for (String dk : deletedKeys == null ? List.<String>of() : deletedKeys) {
            targets.add(keyToFqn(dk));
            sigLevel.put(keyToFqn(dk), ApiCompat.Level.REMOVED_API);
        }
        Set<String> addedClasses = new LinkedHashSet<>();
        for (Map.Entry<String, DecompiledUnit> e : units.entrySet()) {
            DecompiledUnit u = e.getValue();
            if (u == null || !u.isOk()) continue;
            String fqn = keyToFqn(e.getKey());
            if (u.getOldSource() != null && u.getNewSource() != null) {
                targets.add(fqn); // 修改类：实现变更 → MEDIUM/LOW（ApiCompat 命中时升级 HIGH）
            } else if (u.getNewSource() != null) {
                addedClasses.add(fqn); // 纯新增：影响面收敛在本类，不进影响面清单
            }
        }
        targets.removeAll(addedClasses);
        if (targets.isEmpty()) return res;

        // 2) 扫描每个源码文件对目标的引用
        Map<String, List<String>> refEvidence = new HashMap<>(); // 目标 FQN → 引用方 key 列表（去 #old/#new 后缀）
        for (Map.Entry<String, String> se : sources.entrySet()) {
            String srcKey = se.getKey().split("#", 2)[0];
            String src = se.getValue();
            String srcFqn = keyToFqn(srcKey);
            String srcPkg = pkgOfSource(src);
            Set<String> imports = importsOf(src);
            for (String target : targets) {
                if (target.equals(srcFqn)) continue; // 自引用不算影响方
                String simple = simpleOf(target);
                // 词边界匹配 simpleName（排除注释中的提及不现实——证据链留人工复核）
                if (!Pattern.compile("\\b" + Pattern.quote(simple) + "\\b").matcher(src).find()) continue;
                boolean samePkg = pkgOf(target).equals(srcPkg);
                boolean imported = imports.contains(target);
                boolean fqnUsed = src.contains(target);
                if (samePkg || imported || fqnUsed) {
                    refEvidence.computeIfAbsent(target, k -> new ArrayList<>());
                    if (!refEvidence.get(target).contains(srcKey)) refEvidence.get(target).add(srcKey);
                    refIndex.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(srcKey);
                }
            }
        }

        // 3) 逐目标输出影响面
        for (String target : targets) {
            List<String> refs = refEvidence.getOrDefault(target, List.of());
            String status;
            String reason;
            Risk risk;
            if (sigLevel.get(target) == ApiCompat.Level.REMOVED_API && deletedKeys != null
                    && deletedKeys.stream().anyMatch(k -> keyToFqn(k).equals(target))) {
                status = "DELETED";
                risk = refs.isEmpty() ? Risk.LOW : Risk.CRITICAL;
                reason = refs.isEmpty() ? "整类删除且集内无引用方"
                        : "整类删除但仍有 " + refs.size() + " 个引用方（编译失败风险）";
            } else if (sigLevel.containsKey(target)) {
                status = "MODIFIED";
                risk = refs.isEmpty() ? Risk.LOW : Risk.HIGH;
                reason = refs.isEmpty() ? "公开签名变更但集内无引用方"
                        : "公开签名变更且被 " + refs.size() + " 个类引用";
            } else {
                status = "MODIFIED";
                risk = refs.isEmpty() ? Risk.LOW : Risk.MEDIUM;
                reason = refs.isEmpty() ? "实现变更且集内无引用方"
                        : "实现变更且被 " + refs.size() + " 个类引用（行为波及待 AI 深读）";
            }
            ImpactItem it = new ImpactItem(risk, target, status, reason);
            it.referencedBy.addAll(refs);
            res.items.add(it);
            switch (risk) {
                case CRITICAL -> res.critical++;
                case HIGH -> res.high++;
                case MEDIUM -> res.medium++;
                case LOW -> res.low++;
            }
        }
        return res;
    }
}
