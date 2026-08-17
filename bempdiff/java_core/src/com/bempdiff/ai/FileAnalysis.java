package com.bempdiff.ai;

import java.util.List;

/** 阶段B 逐文件深读输出（§5.4 / T10）。 */
public final class FileAnalysis {
    private String key;
    private String intent;       // 改动意图
    private String risk;         // LOW / MEDIUM / HIGH
    private String impact;       // 影响范围
    private List<String> testPoints;  // 测试要点
    private String contextInfluence;  // 项目级上下文如何影响本文件结论（AI 增强）

    public FileAnalysis(String key, String intent, String risk, String impact, List<String> testPoints) {
        this(key, intent, risk, impact, testPoints, "");
    }

    public FileAnalysis(String key, String intent, String risk, String impact,
                        List<String> testPoints, String contextInfluence) {
        this.key = key;
        this.intent = intent;
        this.risk = risk;
        this.impact = impact;
        this.testPoints = testPoints;
        this.contextInfluence = contextInfluence == null ? "" : contextInfluence;
    }

    public String getKey() { return key; }
    public String getIntent() { return intent; }
    public String getRisk() { return risk; }
    public String getImpact() { return impact; }
    public List<String> getTestPoints() { return testPoints; }
    public String getContextInfluence() { return contextInfluence; }
}
