package com.bempdiff.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类化分析的「专题结论」数据模型（方案B：各类别独立 schema）。
 * 承载 stageA 中按分析类别输出的差异化结论：破坏性变更专项 / 影响范围分析 /
 * 测试要点分析 / 整体风险评估。各字段与 PromptBuilders 注入的 JSON 结构一一对应，
 * 解析端（MockAiAnalyzer）按响应中实际出现的字段宽容提取；渲染端（MarkdownReport）
 * 依 {@link StageASummary#getCategory()} 只呈现对应类别的字段，实现「分析边界与内容区分」。
 *
 * <p>理念：不把各类别结论揉成一个通用摘要，而是让模型在各自聚焦维度上产出结构化
 * 字段，从而不同分析项的输出结构显著不同、重点突出。所有类别共享 {@code suggestions}
 * 承载「优化改造建议」。</p>
 */
public final class CategoryConclusion {

    private List<BreakingChange> breakingChanges = new ArrayList<>();
    private String compatibilityVerdict = "";   // 破坏性变更：兼容性结论
    private String affectedModules = "";        // 影响范围：受影响模块/服务
    private String affectedApis = "";           // 影响范围：受影响的对外接口
    private String internalCallers = "";        // 影响范围：内部调用方/依赖链路
    private String diffusion = "";              // 影响范围：扩散路径与数据流
    private List<TestPoint> testPoints = new ArrayList<>();  // 测试要点：清单
    private String riskRationale = "";          // 整体风险：风险判定依据
    private String rollbackPlan = "";           // 整体风险：回滚预案
    private String degradationPlan = "";        // 整体风险：降级预案
    private String suggestions = "";            // 全类别共享：具体可实施的优化改造建议

    public List<BreakingChange> getBreakingChanges() { return breakingChanges; }
    public void setBreakingChanges(List<BreakingChange> v) { this.breakingChanges = v == null ? new ArrayList<>() : v; }
    public String getCompatibilityVerdict() { return compatibilityVerdict; }
    public void setCompatibilityVerdict(String v) { this.compatibilityVerdict = v == null ? "" : v; }
    public String getAffectedModules() { return affectedModules; }
    public void setAffectedModules(String v) { this.affectedModules = v == null ? "" : v; }
    public String getAffectedApis() { return affectedApis; }
    public void setAffectedApis(String v) { this.affectedApis = v == null ? "" : v; }
    public String getInternalCallers() { return internalCallers; }
    public void setInternalCallers(String v) { this.internalCallers = v == null ? "" : v; }
    public String getDiffusion() { return diffusion; }
    public void setDiffusion(String v) { this.diffusion = v == null ? "" : v; }
    public List<TestPoint> getTestPoints() { return testPoints; }
    public void setTestPoints(List<TestPoint> v) { this.testPoints = v == null ? new ArrayList<>() : v; }
    public String getRiskRationale() { return riskRationale; }
    public void setRiskRationale(String v) { this.riskRationale = v == null ? "" : v; }
    public String getRollbackPlan() { return rollbackPlan; }
    public void setRollbackPlan(String v) { this.rollbackPlan = v == null ? "" : v; }
    public String getDegradationPlan() { return degradationPlan; }
    public void setDegradationPlan(String v) { this.degradationPlan = v == null ? "" : v; }
    public String getSuggestions() { return suggestions; }
    public void setSuggestions(String v) { this.suggestions = v == null ? "" : v; }

    /** 是否存在「优化改造建议」内容。 */
    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }

    /** 破坏性变更条目（breaking schema）。 */
    public static final class BreakingChange {
        private String file;
        private String changeType;
        private String change;
        private String compatImpact;
        private String severity;
        private String migrationSuggestion;

        public String getFile() { return file; }
        public void setFile(String v) { this.file = v; }
        public String getChangeType() { return changeType; }
        public void setChangeType(String v) { this.changeType = v; }
        public String getChange() { return change; }
        public void setChange(String v) { this.change = v; }
        public String getCompatImpact() { return compatImpact; }
        public void setCompatImpact(String v) { this.compatImpact = v; }
        public String getSeverity() { return severity; }
        public void setSeverity(String v) { this.severity = v; }
        public String getMigrationSuggestion() { return migrationSuggestion; }
        public void setMigrationSuggestion(String v) { this.migrationSuggestion = v; }
    }

    /** 测试要点条目（testpoints schema）。 */
    public static final class TestPoint {
        private String item;
        private String file;
        private String scenario;
        private String caseIdea;
        private String verifyFocus;

        public String getItem() { return item; }
        public void setItem(String v) { this.item = v; }
        public String getFile() { return file; }
        public void setFile(String v) { this.file = v; }
        public String getScenario() { return scenario; }
        public void setScenario(String v) { this.scenario = v; }
        public String getCaseIdea() { return caseIdea; }
        public void setCaseIdea(String v) { this.caseIdea = v; }
        public String getVerifyFocus() { return verifyFocus; }
        public void setVerifyFocus(String v) { this.verifyFocus = v; }
    }
}