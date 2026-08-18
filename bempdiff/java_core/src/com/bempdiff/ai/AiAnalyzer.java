package com.bempdiff.ai;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.util.List;
import java.util.Map;

/**
 * AI 两阶段分析接口（T09/T10/T11，§5.4）。对应详细设计 §5.4。
 *  - stageA：发「差异清单 + 每文件截断 diff 摘要」，产出整体风险/影响/测试主题 + 每文件初评
 *  - stageB：仅对 high/medium 风险或用户勾选文件，发完整 diff，逐文件深读（按文件类型区分 Java / 前端 JS/HTML/CSS）
 *  - 成本闸门：阶段前预估 token，超阈值返回 gateWarn=true，由 UI 二次确认
 */
public interface AiAnalyzer {

    /** 构建阶段A prompt（纯函数，便于单测；不触网）。 */
    String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled,
                             AiConfig cfg);

    /** 构建阶段B 单文件 prompt（纯函数）。fc 用于区分 Java 类与前端源码，生成对应领域措辞。 */
    String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg);

    /** 构建阶段A prompt（项目级上下文增强）：默认委托无上下文实现。 */
    default String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        return buildStageAPrompt(diff, decompiled, cfg);
    }

    /** 构建阶段B 单文件 prompt（项目级上下文增强）：默认委托无上下文实现。 */
    default String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx) {
        return buildStageBPrompt(key, unit, fc, cfg);
    }

    /** 预估 token（粗略：字符数 / 2），供成本闸门判断。 */
    default double estimateTokens(String prompt) {
        return prompt.length() / 2.0;
    }

    StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg);

    List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg);

    /** 阶段A（项目级上下文增强）：默认委托无上下文实现，保证向后兼容；实现类可重写以注入上下文。 */
    default StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled,
                                 AiConfig cfg, ProjectContext ctx) {
        return stageA(diff, decompiled, cfg);
    }

    /** 阶段B（项目级上下文增强）：默认委托无上下文实现，保证向后兼容；实现类可重写以注入上下文。 */
    default List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx) {
        return stageB(candidates, cfg);
    }

    /** 构建阶段A prompt（聚焦类别增强）：focus 非空时追加聚焦指令，引导模型在该维度深入。 */
    default String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        return buildStageAPrompt(diff, decompiled, cfg, ctx);
    }

    /** 构建阶段B 单文件 prompt（聚焦类别增强）。 */
    default String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx, String focus) {
        return buildStageBPrompt(key, unit, fc, cfg, ctx);
    }

    /** 阶段A（聚焦类别增强）。 */
    default StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        return stageA(diff, decompiled, cfg, ctx);
    }

    /** 阶段B（聚焦类别增强）。 */
    default List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx, String focus) {
        return stageB(candidates, cfg, ctx);
    }

    /** 连接测试（设置弹窗「测试」按钮，FR9.4）。 */
    boolean testConnection(AiConfig cfg);

    /** 阶段B 候选请求。fileClass 用于 prompt 领域分支（Java / 前端 JS/HTML/CSS）。 */
    class DecompileReq {
        public final String key;
        public final DecompiledUnit unit;
        public final FileClass fileClass;
        public DecompileReq(String key, DecompiledUnit unit, FileClass fileClass) {
            this.key = key; this.unit = unit; this.fileClass = fileClass;
        }
    }
}
