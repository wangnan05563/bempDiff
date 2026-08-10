package com.bempdiff.ai;

import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.model.DecompiledUnit;

import java.util.List;
import java.util.Map;

/**
 * AI 两阶段分析接口（T09/T10/T11，§5.4）。对应详细设计 §5.4。
 *  - stageA：发「差异清单 + 每文件截断 diff 摘要」，产出整体风险/影响/测试主题 + 每文件初评
 *  - stageB：仅对 high/medium 风险或用户勾选文件，发完整反编译 diff，逐文件深读
 *  - 成本闸门：阶段前预估 token，超阈值返回 gateWarn=true，由 UI 二次确认
 */
public interface AiAnalyzer {

    /** 构建阶段A prompt（纯函数，便于单测；不触网）。 */
    String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled,
                             AiConfig cfg);

    /** 构建阶段B 单文件 prompt（纯函数）。 */
    String buildStageBPrompt(String key, DecompiledUnit unit, AiConfig cfg);

    /** 预估 token（粗略：字符数 / 2），供成本闸门判断。 */
    default double estimateTokens(String prompt) {
        return prompt.length() / 2.0;
    }

    StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg);

    List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg);

    /** 连接测试（设置弹窗「测试」按钮，FR9.4）。 */
    boolean testConnection(AiConfig cfg);

    /** 阶段B 候选请求。 */
    class DecompileReq {
        public final String key;
        public final DecompiledUnit unit;
        public DecompileReq(String key, DecompiledUnit unit) {
            this.key = key; this.unit = unit;
        }
    }
}
