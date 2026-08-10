package com.bempdiff.ai;

import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 两阶段 prompt 构造工具（离线可验证，不触网）。对应详细设计 §5.4 数据契约。
 *  - 阶段A：差异清单 + 每文件截断 diff 摘要（前 cfg.stageAFileSampleLines 行，最多 cfg.stageATopK 文件）
 *  - 阶段B：仅发完整 diffText（已脱敏）
 *  - 脱敏：公网模型模式正则擦除疑似证件号/密钥字面量（详见 sanitize）
 */
public final class PromptBuilders {

    private PromptBuilders() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 阶段A prompt：让模型产出 JSON（整体风险/影响/测试主题 + 每文件初评）。 */
    public static String buildStageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        StringBuilder p = new StringBuilder();
        appendDiffHeader(p, diff, cfg);
        appendFileSummaries(p, diff, decompiled, cfg);
        return sanitize(p.toString(), cfg);
    }

    private static void appendDiffHeader(StringBuilder p, DiffResult diff, AiConfig cfg) {
        p.append("你是 Java 票据系统升级的差异分析助手。下面是新/老两个构建包的差异清单，");
        p.append("请据此评估整体风险等级(LOW/MEDIUM/HIGH)、影响范围、全局测试要点，");
        p.append("并对每个改动文件给出初评风险(LOW/MEDIUM/HIGH)与一句话理由。以 JSON 返回。\n\n");
        p.append("## 差异统计\n");
        p.append("新增=").append(diff.get(DiffStatus.ADDED).size())
                .append(" 删除=").append(diff.get(DiffStatus.DELETED).size())
                .append(" 修改=").append(diff.get(DiffStatus.MODIFIED).size())
                .append(" 未变=").append(diff.get(DiffStatus.UNCHANGED).size()).append("\n\n");
        p.append("## 改动文件与 diff 摘要（截断，最多 ").append(cfg.getStageATopK()).append(" 个）\n");
    }

    private static void appendFileSummaries(StringBuilder p, DiffResult diff,
                                            Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        List<String> files = collectChangedFiles(diff);
        int limit = Math.min(files.size(), cfg.getStageATopK());
        for (int i = 0; i < limit; i++) {
            appendSingleFileSummary(p, files.get(i), diff, decompiled, cfg);
        }
    }

    /** 收集所有有变动的文件列表（新增/删除/修改），按优先级排序。 */
    private static List<String> collectChangedFiles(DiffResult diff) {
        List<String> files = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : diff.get(st)) files.add(k);
        }
        return files;
    }

    /** 提取单文件摘要追加逻辑，降低 appendFileSummaries 的认知复杂度。 */
    private static void appendSingleFileSummary(StringBuilder p, String k, DiffResult diff,
                                                Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        p.append("\n### ").append(k).append(" (").append(statusName(stOf(diff, k))).append(")\n");
        DecompiledUnit u = decompiled.get(k);
        if (u != null && u.isOk() && u.getDiffText() != null) {
            String[] lines = u.getDiffText().split("\n");
            int lim = Math.min(lines.length, cfg.getStageAFileSampleLines());
            for (int i = 0; i < lim; i++) p.append(lines[i]).append("\n");
            if (lines.length > lim) p.append("... (截断，共 ").append(lines.length).append(" 行)\n");
        } else {
            p.append("(无反编译源码，或未纳入 Top-K)\n");
        }
    }

    /** 阶段B 单文件 prompt：仅发完整 diffText。 */
    public static String buildStageB(String key, DecompiledUnit unit, AiConfig cfg) {
        StringBuilder p = new StringBuilder();
        p.append("请对以下Java类改动做逐文件深读，返回 JSON：");
        p.append("改动意图(intent)、风险等级(risk: LOW/MEDIUM/HIGH)、影响范围(impact)、测试要点(testPoints数组)。\n\n");
        p.append("## 文件：").append(key).append("\n");
        p.append("```diff\n").append(unit.getDiffText() == null ? "" : unit.getDiffText()).append("\n```\n");
        return sanitize(p.toString(), cfg);
    }

    /** 脱敏：公网模型(o/a/z/q/custom 非本地)时，擦除疑似敏感字面量。 */
    public static String sanitize(String text, AiConfig cfg) {
        boolean local = "ollama".equalsIgnoreCase(cfg.getProvider())
                || cfg.getBaseUrl().contains("localhost") || cfg.getBaseUrl().contains("127.0.0.1");
        if (local) return text;  // 本地/私有化模型：代码不出机，无需脱敏
        // 擦除疑似身份证/密钥/手机号等（演示正则；量产版按业务规则细化）
        return text
                .replaceAll("\\b\\d{17}[\\dXx]\\b", "***ID***")          // 18位身份证
                .replaceAll("\\b(?:AKIA|AK|SK|KEY|SECRET|TOKEN|PASSWORD|PWD)[-.\\w=:]*[A-Za-z0-9+/=]{6,}", "***SECRET***")
                .replaceAll("\\b1[3-9]\\d{9}\\b", "***PHONE***");         // 手机号
    }

    private static DiffStatus stOf(DiffResult diff, String k) {
        for (DiffStatus st : DiffStatus.values()) {
            if (diff.get(st).contains(k)) return st;
        }
        return DiffStatus.MODIFIED;
    }

    private static String statusName(DiffStatus st) {
        switch (st) {
            case ADDED: return "新增";
            case DELETED: return "删除";
            default: return "修改";
        }
    }
}
