package com.bempdiff.ai;

import java.util.List;

/** 阶段A 概览输出（§5.4 / T09）。 */
public final class StageASummary {
    private String overallRisk;          // LOW / MEDIUM / HIGH
    private String impactScope;          // 影响模块/对外接口（自由文本）
    private List<String> testThemes;     // 测试要点（全局）
    private List<FileRisk> fileRisks;    // 每文件初评
    private String contextInfluence;     // 项目级上下文如何影响整体结论（AI 增强）
    private String category;             // 分析类别（risk/breaking/impact/testpoints/custom），渲染端据此区分章节
    private CategoryConclusion conclusion = new CategoryConclusion(); // 分类化专题结论（方案B）

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
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public CategoryConclusion getConclusion() { return conclusion; }
    public void setConclusion(CategoryConclusion v) { this.conclusion = v == null ? new CategoryConclusion() : v; }
}
