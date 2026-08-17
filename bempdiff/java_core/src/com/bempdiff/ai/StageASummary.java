package com.bempdiff.ai;

import java.util.List;

/** 阶段A 概览输出（§5.4 / T09）。 */
public final class StageASummary {
    private String overallRisk;          // LOW / MEDIUM / HIGH
    private String impactScope;          // 影响模块/对外接口（自由文本）
    private List<String> testThemes;     // 测试要点（全局）
    private List<FileRisk> fileRisks;    // 每文件初评
    private String contextInfluence;     // 项目级上下文如何影响整体结论（AI 增强）

    public String getOverallRisk() { return overallRisk; }
    public void setOverallRisk(String v) { this.overallRisk = v; }
    public String getImpactScope() { return impactScope; }
    public void setImpactScope(String v) { this.impactScope = v; }
    public List<String> getTestThemes() { return testThemes; }
    public void setTestThemes(List<String> v) { this.testThemes = v; }
    public List<FileRisk> getFileRisks() { return fileRisks; }
    public void setFileRisks(List<FileRisk> v) { this.fileRisks = v; }
    public String getContextInfluence() { return contextInfluence; }
    public void setContextInfluence(String v) { this.contextInfluence = v; }
}
