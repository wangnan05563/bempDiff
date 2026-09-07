package com.bempdiff.ai;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 离线回放版 AiAnalyzer（验证两阶段流程，不触网、无需 API Key）。
 *  - stageA：把 buildStageAPrompt 落地为 <outDir>/stageA.prompt.txt，若同目录存在 stageA.response.txt 则解析为 StageASummary，否则返回内置兜底摘要。
 *  - stageB：对候选逐个写 <outDir>/stageB.<index>.prompt.txt，同理回放 stageB.response.txt。
 * 真实接入时替换为本类为 HttpAiAnalyzer（调用 OpenAI/Azure/Ollama 兼容接口），两阶段接口不变。
 */
public final class MockAiAnalyzer implements AiAnalyzer {

    private static final String DEFAULT_RISK = "MEDIUM";

    // ---- 回放文件名 / 兜底文案（多处重复，提取为常量规避 java:S1192） ----
    private static final String STAGE_A_PROMPT = "stageA.prompt.txt";
    private static final String STAGE_A_RESPONSE = "stageA.response.txt";
    private static final String TEST_THEME_REGRESSION = "回归核心业务流程";
    private static final String TEST_THEME_COMPAT = "校验对外接口兼容性";
    private static final String TEST_THEME_NO_DEP = "验证删除类无外部依赖";
    /** 聚焦类别键 IMPACT（switch case 与 pickObjectField 别名共用）。 */
    private static final String IMPACT = "impact";
    /** 兜底摘要「差异文件数」描述前缀。 */
    private static final String DIFF_FILE_COUNT = "差异文件数=";
    /** 离线兜底摘要正文（stageA 无真实响应时的说明文案；多个回放分支复用，规避 S1192）。 */
    private static final String DEFAULT_SUMMARY_TEXT = "未提供 stageA.response.txt，使用内置兜底摘要。差异文件数=";
    /** 已结合项目上下文的判断后缀（多处回放文案复用，规避 S1192）。 */
    private static final String BASE_JUDGMENT = "）做基础判断；";

    private final Path replayDir;   // 离线回放目录（prompt/response 同目录）

    public MockAiAnalyzer(Path replayDir) {
        this.replayDir = replayDir;
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg);
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg, ctx);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        String prompt = buildStageAPrompt(diff, decompiled, cfg);
        writePrompt(STAGE_A_PROMPT, prompt);
        String resp = readResponse(STAGE_A_RESPONSE);
        if (resp != null) return parseStageA(resp, false);
        return buildFallbackSummary(diff, null, cfg);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx);
        writePrompt(STAGE_A_PROMPT, prompt);
        String resp = readResponse(STAGE_A_RESPONSE);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        if (resp != null) return parseStageA(resp, withCtx);
        return buildFallbackSummary(diff, ctx, cfg);
    }

    private static StageASummary buildFallbackSummary(DiffResult diff, ProjectContext ctx, AiConfig cfg) {
        StageASummary s = new StageASummary();
        s.setOverallRisk(DEFAULT_RISK);
        int changed = diff.get(DiffStatus.ADDED).size() + diff.get(DiffStatus.DELETED).size()
                + diff.get(DiffStatus.MODIFIED).size();
        String reason = offlineReason(cfg);
        if (ctx != null && !ctx.isEmpty()) {
            s.setImpactScope("离线回放模式（含项目上下文[" + ctx.getBuildSystem() + "]）：" + reason + "，"
                    + DEFAULT_SUMMARY_TEXT + changed);
            s.setContextInfluence("已结合项目上下文（构建系统=" + ctx.getBuildSystem()
                    + BASE_JUDGMENT + reason + "，影响评估限于结构层面。");
        } else {
            s.setImpactScope("离线回放模式（" + reason + "）："
                    + DEFAULT_SUMMARY_TEXT + changed);
        }
        s.setTestThemes(java.util.Arrays.asList(TEST_THEME_REGRESSION, TEST_THEME_COMPAT, TEST_THEME_NO_DEP));
        s.setFileRisks(new ArrayList<>());
        return s;
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg) {
        return stageB(candidates, cfg, null);
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx) {
        return stageBInternal(candidates, cfg, ctx, null);
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus, String category) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, category);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg, ctx, focus);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
        writePrompt(STAGE_A_PROMPT, prompt);
        String resp = readResponse(STAGE_A_RESPONSE);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        if (resp != null) return parseStageA(resp, withCtx);
        // 修复：兜底摘要按聚焦维度差异化，离线/无 API Key 时不同分析项的报告内容可区分
        return buildFallbackSummary(diff, ctx, focus, cfg, null);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus, String category) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus, category);
        writePrompt(STAGE_A_PROMPT, prompt);
        String resp = readResponse(STAGE_A_RESPONSE);
        boolean withCtx = ctx != null && !ctx.isEmpty();
        if (resp != null) {
            StageASummary s = parseStageA(resp, withCtx);
            if (category != null) {
                s.setCategory(category);
            }
            return s;
        }
        // 兜底：以调用方权威 category 判定维度（唯一事实源），custom/空回落聚焦文本反推
        return buildFallbackSummary(diff, ctx, focus, cfg, category);
    }

    /** 权威类别 → FocusKind：category（调用方 normalizeCategory 产出，唯一事实源）优先；
     *  为空或 unknown（custom/整体）时按聚焦文本 {@code focus} 反推，保证兼容历史调用与默认整体分析。 */
    private static FocusKind kindOf(String category, String focus) {
        if (category != null && !category.isEmpty()) {
            switch (category) {
                case "breaking": return FocusKind.BREAKING;
                case IMPACT: return FocusKind.IMPACT;
                case "testpoints": return FocusKind.TESTPOINTS;
                case "risk": return FocusKind.RISK;
                default: break;
            }
        }
        return FocusKind.of(focus);
    }

    /** 无回放响应时的内置兜底：按聚焦维度（focus）生成不同摘要，避免所有分析项内容雷同。
     *  category 由调用方规范化给出，作为类别判定的权威来源；为空时回退按 focus 文本反推（历史路径）。 */
    private static StageASummary buildFallbackSummary(DiffResult diff, ProjectContext ctx, String focus, AiConfig cfg, String category) {
        int changed = diff.get(DiffStatus.ADDED).size() + diff.get(DiffStatus.DELETED).size()
                + diff.get(DiffStatus.MODIFIED).size();
        int added = diff.get(DiffStatus.ADDED).size();
        int deleted = diff.get(DiffStatus.DELETED).size();
        int modified = diff.get(DiffStatus.MODIFIED).size();
        FocusKind kind = kindOf(category, focus);
        String ctxTxt = (ctx != null && !ctx.isEmpty())
                ? "（含项目上下文[" + ctx.getBuildSystem() + "]）" : "";
        String reason = offlineReason(cfg);
        StageASummary s = new StageASummary();
        s.setOverallRisk(DEFAULT_RISK);
        switch (kind) {
            case BREAKING:
                s.setImpactScope("离线兜底[破坏性变更专项]" + ctxTxt + "：" + reason + DIFF_FILE_COUNT + changed
                        + "（删除=" + deleted + "，签名/结构变更需人工核对对外契约）；"
                        + "重点排查删除类、方法签名变更与接口实现变更的调用方兼容性。");
                s.setTestThemes(java.util.Arrays.asList(
                        "核对所有删除类/删除方法的调用方与引用",
                        "验证接口签名变更后的编译与运行兼容性",
                        "回归序列化/反序列化契约（字段类型变更场景）"));
                break;
            case IMPACT:
                s.setImpactScope("离线兜底[影响范围分析]" + ctxTxt + "：" + reason + DIFF_FILE_COUNT + changed
                        + "（新增=" + added + "，修改=" + modified + "，删除=" + deleted + "）；"
                        + "按依赖方向评估波及模块：先查直接依赖变更文件的调用方，再向上游链路扩散。");
                s.setTestThemes(java.util.Arrays.asList(
                        "梳理变更文件的直接/间接调用方清单",
                        "验证上下游模块之间的接口契约与数据流",
                        "针对受影响模块做链路级回归"));
                break;
            case TESTPOINTS:
                s.setImpactScope("离线兜底[测试要点分析]" + ctxTxt + "：" + reason + DIFF_FILE_COUNT + changed
                        + "，聚焦给出可执行的回归测试场景、用例思路与验证重点。");
                s.setTestThemes(java.util.Arrays.asList(
                        "核心业务流程主链路回归（含变更点前后对比）",
                        "边界与异常分支用例（空值/超长/非法输入）",
                        "兼容性用例：旧数据/旧配置/跨版本序列化"));
                break;
            case RISK:
                s.setImpactScope("离线兜底[整体风险分析]" + ctxTxt + "：" + reason + DIFF_FILE_COUNT + changed
                        + "（新增=" + added + "，修改=" + modified + "，删除=" + deleted + "），"
                        + "整体风险等级中，需结合降级与回滚预案综合评估。");
                s.setTestThemes(java.util.Arrays.asList(
                        TEST_THEME_REGRESSION,
                        TEST_THEME_COMPAT,
                        TEST_THEME_NO_DEP));
                break;
            default:
                s.setImpactScope("离线回放模式" + ctxTxt + "：" + reason + "；" + DEFAULT_SUMMARY_TEXT + changed);
                s.setTestThemes(java.util.Arrays.asList(TEST_THEME_REGRESSION, TEST_THEME_COMPAT, TEST_THEME_NO_DEP));
        }
        if (ctx != null && !ctx.isEmpty()) {
            s.setContextInfluence("已结合项目上下文（构建系统=" + ctx.getBuildSystem()
                    + BASE_JUDGMENT + reason + "，影响评估限于结构层面。");
        }
        s.setFileRisks(new ArrayList<>());
        return s;
    }

    /** 离线兜底原因精确化：区分「开关未开启」与「已开启但未配置/未持久化 API Key」。 */
    private static String offlineReason(AiConfig cfg) {
        if (cfg == null) return "未接入 LLM（未配置 AI 分析）";
        if (!cfg.isEnabled()) return "未接入 LLM（AI 分析开关未开启，请在配置中心启用）";
        return "未接入 LLM（AI 分析已开启，但未配置 API Key 或未持久化）";
    }

    /** 聚焦维度枚举：由 buildFocus 的中文指令关键词判定，用于离线兜底差异化输出。 */
    private enum FocusKind {
        BREAKING, IMPACT, TESTPOINTS, RISK, DEFAULT;

        static FocusKind of(String focus) {
            if (focus == null || focus.trim().isEmpty()) return RISK;
            if (focus.contains("破坏性") || focus.contains("兼容性") || focus.contains("接口契约")) return BREAKING;
            if (focus.contains("影响范围") || focus.contains("上下游") || focus.contains("依赖")) return IMPACT;
            if (focus.contains("测试") || focus.contains("回归")) return TESTPOINTS;
            if (focus.contains("风险") || focus.contains("降级") || focus.contains("回滚")) return RISK;
            return DEFAULT;
        }
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx, String focus) {
        return stageBInternal(candidates, cfg, ctx, focus);
    }

    /**
     * stageB 核心循环：统一无 focus 与有 focus 两个重载的处理骨架，避免循环体重复。
     * focus 为 null 时走无 focus 版本语义：兜底测试要点取 RISK 默认分支（等价于原实现）。
     */
    private List<FileAnalysis> stageBInternal(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx, String focus) {
        List<FileAnalysis> out = new ArrayList<>();
        int idx = 0;
        List<String> fallbackPoints = fallbackTestPoints(focus);
        String reason = offlineReason(cfg);
        for (DecompileReq req : candidates) {
            ProjectContext effCtx = (req.perFileCtx != null) ? req.perFileCtx : ctx;
            boolean effWithCtx = effCtx != null && !effCtx.isEmpty();
            // focus 为 null 时调用无 focus 重载的 PromptBuilders.buildStageB，保持 prompt 生成语义不变
            String prompt = (focus == null)
                    ? PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, effCtx)
                    : PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, effCtx, focus);
            writePrompt("stageB." + idx + ".prompt.txt", prompt);
            String resp = readResponse("stageB." + idx + ".response.txt");
            if (resp != null) {
                out.add(parseFileAnalysis(req.key, resp, effWithCtx));
            } else {
                String influence = effWithCtx
                        ? "已结合项目上下文（" + effCtx.getBuildSystem() + BASE_JUDGMENT + reason + "。"
                        : "";
                out.add(new FileAnalysis(req.key,
                        "离线回放模式：" + reason,
                        DEFAULT_RISK,
                        "需人工核对（离线演示结果）", new ArrayList<>(fallbackPoints), influence));
            }
            idx++;
        }
        return out;
    }

    /** 按聚焦维度给出 stageB 兜底测试要点（无回放响应时使用）。 */
    private static List<String> fallbackTestPoints(String focus) {
        switch (FocusKind.of(focus)) {
            case BREAKING: return java.util.Arrays.asList(
                    "核对本类删除/签名变更的调用方兼容性",
                    "验证接口契约与序列化格式未破坏");
            case IMPACT: return java.util.Arrays.asList(
                    "回归本类直接调用方与依赖链路",
                    "验证变更对外部模块的影响范围");
            case TESTPOINTS: return java.util.Arrays.asList(
                    "为本类变更设计正向/边界/异常用例",
                    "结合变更行号定位精确回归点");
            case RISK:
            default: return java.util.Arrays.asList("回归该类的调用方");
        }
    }

    @Override
    public boolean testConnection(AiConfig cfg) {
        // 离线回放：恒为真（真实 HttpAiAnalyzer 会发 /models 探活）
        return true;
    }

    // ---- 轻量解析（足够验证流程；量产能用 JSON 库严格解析） ----
    public static StageASummary parseStageAStatic(String resp) {
        return parseStageAStatic(resp, false);
    }

    public static StageASummary parseStageAStatic(String resp, boolean withContext) {
        StageASummary s = new StageASummary();
        s.setOverallRisk(pick(resp, "overallRisk", DEFAULT_RISK));
        s.setImpactScope(pickTextOrArray(resp, "impactScope"));
        // 注意：不再从响应读取 category——权威类别由调用方（BempServer.normCat）统一注入并覆盖，
        // 响应内模型自报的 category 不可信，与其保持一致可避免「prompt 注入」与「渲染类别」错位。
        // 测试主题：优先从 JSON 数组提取，回退 markdown 行扫描
        List<String> themes = extractStringArray(resp, "testThemes", "test_themes", "测试主题");
        if (themes.isEmpty()) {
            for (String t : resp.split("\n")) {
                String t2 = t.trim();
                if (t2.startsWith("-") && (t2.contains("测试") || t2.contains("回归") || t2.contains("验证"))) {
                    themes.add(t2.replaceFirst("^-", "").trim());
                }
            }
        }
        s.setTestThemes(themes);
        // 每文件初评风险：从 JSON 数组（fileRisks / files）提取
        List<FileRisk> risks = extractFileRiskArray(resp, "fileRisks", "files", "file_risks");
        s.setFileRisks(risks);
        if (withContext) {
            s.setContextInfluence(pickTextOrArray(resp, "contextInfluence", "上下文影响"));
        }
        // 方案B：宽容提取「分类专题结论」（conclusion/顶层字段均在响应中直接出现，不依赖嵌套层级）
        s.setConclusion(parseCategoryConclusion(resp));
        return s;
    }

    /** 分类化结论解析：按各类别字段名在响应中宽容提取；未出现的类别字段留空（无副作用）。 */
    private static CategoryConclusion parseCategoryConclusion(String resp) {
        CategoryConclusion c = new CategoryConclusion();
        c.setBreakingChanges(mapBreaking(extractObjectBodies(resp, "breakingChanges", "breaking_items")));
        c.setCompatibilityVerdict(pickTextOrArray(resp, "compatibilityVerdict"));
        c.setAffectedModules(pickTextOrArray(resp, "affectedModules"));
        c.setAffectedApis(pickTextOrArray(resp, "affectedApis"));
        c.setInternalCallers(pickTextOrArray(resp, "internalCallers"));
        c.setDiffusion(pickTextOrArray(resp, "diffusion"));
        c.setTestPoints(mapTestPoints(extractObjectBodies(resp, "testPoints", "test_points")));
        c.setRiskRationale(pickTextOrArray(resp, "riskRationale"));
        c.setRollbackPlan(pickTextOrArray(resp, "rollbackPlan"));
        c.setDegradationPlan(pickTextOrArray(resp, "degradationPlan"));
        c.setSuggestions(pickTextOrArray(resp, "suggestions"));
        return c;
    }

    private static List<CategoryConclusion.BreakingChange> mapBreaking(List<String> bodies) {
        List<CategoryConclusion.BreakingChange> out = new ArrayList<>();
        for (String obj : bodies) {
            CategoryConclusion.BreakingChange b = new CategoryConclusion.BreakingChange();
            b.setFile(pickObjectField(obj, "file", "fileName"));
            b.setChangeType(pickObjectField(obj, "changeType", "type"));
            b.setChange(pickObjectField(obj, "change", "detail"));
            b.setCompatImpact(pickObjectField(obj, "compatImpact", IMPACT));
            b.setSeverity(pickObjectField(obj, "severity", "risk"));
            b.setMigrationSuggestion(pickObjectField(obj, "migrationSuggestion", "suggestion"));
            out.add(b);
        }
        return out;
    }

    private static List<CategoryConclusion.TestPoint> mapTestPoints(List<String> bodies) {
        List<CategoryConclusion.TestPoint> out = new ArrayList<>();
        for (String obj : bodies) {
            CategoryConclusion.TestPoint t = new CategoryConclusion.TestPoint();
            t.setItem(pickObjectField(obj, "item", "name"));
            t.setFile(pickObjectField(obj, "file", "files"));
            t.setScenario(pickObjectField(obj, "scenario"));
            t.setCaseIdea(pickObjectField(obj, "caseIdea", "case_idea"));
            t.setVerifyFocus(pickObjectField(obj, "verifyFocus", "verify_focus"));
            out.add(t);
        }
        return out;
    }

    /** 提取 JSON 对象数组内部为对象体字符串列表（候选字段名依次尝试）；未找到返回空列表。 */
    private static List<String> extractObjectBodies(String resp, String... fieldNames) {
        String inner = findJsonArray(resp, fieldNames);
        if (inner == null) {
            return new ArrayList<>();
        }
        return parseRawObjectBodies(inner);
    }

    /** 解析一个 JSON 对象数组内部文本（不含外层方括号）为各对象体（不含外层花括号）的列表。
     *  与 parseFileRiskObjects 同构但返回原始对象体，供 mapBreaking/mapTestPoints 复用 pickObjectField。 */
    private static List<String> parseRawObjectBodies(String inner) {
        List<String> out = new ArrayList<>();
        int[] depth = {0};
        int[] objStart = {-1};
        boolean inStr = false;
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inStr = !inStr;
            } else if (!inStr) {
                scanBrace(c, depth, objStart, out, inner, i);
            }
        }
        return out;
    }

    /** 更新花括号层与对象体起始/结束（仅在非字符串内调用，规避括号被字符串内容干扰）。 */
    private static void scanBrace(char c, int[] depth, int[] objStart, List<String> out, String inner, int i) {
        if (c == '{') {
            if (depth[0] == 0) {
                objStart[0] = i;
            }
            depth[0]++;
        } else if (c == '}') {
            depth[0]--;
            if (depth[0] == 0 && objStart[0] >= 0) {
                out.add(inner.substring(objStart[0] + 1, i));
                objStart[0] = -1;
            }
        }
    }

    /** 分类结论自由文本字段的宽容提取：兼容「标量 / 字符串数组 / 对象 / 对象数组」四种形态。
     *  真实 LLM 常把受影响模块、优化建议、contextInfluence、impact 等输出为跨行数组或对象，
     *  单行标量 {@code pick} 会在值起始的 '[' 或 '{' 处截断，渲染成孤立的符号。
     *  此方法先按完整 JSON 值提取并按形态转多行文本，未命中再回退标量/纯文本，两者兼顾。
     *  同时也是 {@code pickText} 的实现内核。 */
    private static String pickTextOrArray(String resp, String field) {
        String raw = extractFieldJsonValue(resp, field);
        if (raw != null) {
            return jsonValueToText(raw);
        }
        return pick(resp, field, "");
    }

    private static String pickTextOrArray(String resp, String... fieldAliases) {
        for (String f : fieldAliases) {
            String v = pickTextOrArray(resp, f);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    /** 从响应中定位某字段名（完整 JSON 键 `"field":`）的起始下标；未找到返回 -1。
     *  前导必须是对象分隔符（`{`/`,`/空白），避免把 `subField` 这类复合键内部的
     *  同名子串误当作顶层字段（如 IMPACT 会误命中 "compatImpact"）。 */
    private static int indexOfFieldKey(String resp, String field) {
        String key = "\"" + field + "\"";
        int idx = resp.indexOf(key);
        while (idx >= 0) {
            // 前导字符合法 ⇒ 独立键；命中复合键内部子串则继续向后搜索
            char prev = idx > 0 ? resp.charAt(idx - 1) : '{';
            if (prev == '{' || prev == ',' || Character.isWhitespace(prev)) {
                return idx;
            }
            idx = resp.indexOf(key, idx + 1);
        }
        return -1;
    }

    /** 从响应中定位某字段名并返回其 JSON 值整体子串（含外层引号/数组/对象括号）；字段不存在或无值返回 null。 */
    private static String extractFieldJsonValue(String resp, String field) {
        int idx = indexOfFieldKey(resp, field);
        if (idx < 0) return null;
        int colon = resp.indexOf(':', idx + field.length() + 2);
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < resp.length() && (resp.charAt(p) == ' ' || resp.charAt(p) == '\t'
                || resp.charAt(p) == '\r' || resp.charAt(p) == '\n')) p++;
        if (p >= resp.length()) return null;
        return extractJsonValueSubstring(resp, p);
    }

    /** 按首字符类型提取一个完整 JSON 值子串（字符串/数组/对象/标量）；未闭合的数组/对象返回 null。 */
    private static String extractJsonValueSubstring(String resp, int p) {
        char ch = resp.charAt(p);
        if (ch == '"') {
            int end = scanStringEnd(resp, p + 1);
            return resp.substring(p, Math.min(end + 1, resp.length()));
        }
        if (ch == '[') {
            int end = findArrayEnd(resp, p);
            return end > p ? resp.substring(p, end + 1) : null;
        }
        if (ch == '{') {
            int end = findObjectEnd(resp, p);
            return end > p ? resp.substring(p, end + 1) : null;
        }
        // 标量（数字/布尔/null）
        int e = p;
        while (e < resp.length() && resp.charAt(e) != ',' && resp.charAt(e) != '}'
                && resp.charAt(e) != ']' && resp.charAt(e) != '\n' && resp.charAt(e) != '\r') e++;
        return resp.substring(p, e);
    }

    /** 找到从开括号开始匹配的对象结束位置（处理嵌套与字符串内括号），未匹配返回 -1。 */
    private static int findObjectEnd(String resp, int brace) {
        int depth = 0;
        boolean inStr = false;
        boolean escaped = false;
        for (int i = brace; i < resp.length(); i++) {
            char c = resp.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inStr = !inStr;
            } else if (!inStr) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** 将一个 JSON 值子串（字符串数组 / 对象数组 / 单对象 / 标量 / 字符串）转成可读多行文本。 */
    private static String jsonValueToText(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("[")) return jsonArrayToText(t);
        if (t.startsWith("{")) {
            String inner = t.length() >= 2 ? t.substring(1, t.length() - 1) : "";
            return objectToText(inner);
        }
        if (t.startsWith("\"") && t.length() >= 2 && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** 将 JSON 数组值子串转多行文本：对象数组逐对象转文本，字符串数组以换行拼接。 */
    private static String jsonArrayToText(String t) {
        String inner = t.length() >= 2 ? t.substring(1, t.length() - 1) : "";
        int first = 0;
        while (first < inner.length() && Character.isWhitespace(inner.charAt(first))) first++;
        if (first < inner.length() && inner.charAt(first) == '{') {
            // 对象数组 → 逐对象转文本
            List<String> bodies = parseRawObjectBodies(inner);
            StringBuilder sb = new StringBuilder();
            for (String b : bodies) {
                String ot = objectToText(b);
                if (!ot.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(ot);
                }
            }
            return sb.toString();
        }
        return String.join("\n", parseStringArray(inner));
    }

    /** 将单个 JSON 对象体（不含外层花括号）拍平为「- 字段：值」多行文本；嵌套对象/数组递归转文本。 */
    private static String objectToText(String inner) {
        String s = inner == null ? "" : inner;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int next = appendField(sb, s, i);
            if (next < 0) return collapseText(sb); // 值未闭合（截断输出）：提前终止，避免游标回退死循环
            i = next;
        }
        return collapseText(sb);
    }

    /**
     * 格式化单个「字段：值」并追加到 sb，返回下一字段起始下标；未找到字段或值未闭合返回 -1 由调用方终止。
     * 提取自 objectToText 以降低其认知复杂度（原内层 while 全量逻辑移入）。
     */
    private static int appendField(StringBuilder sb, String s, int i) {
        int q = s.indexOf('"', i);
        if (q < 0) return -1;
        int qEnd = scanStringEnd(s, q + 1);
        String key = s.substring(q + 1, qEnd);
        int colon = s.indexOf(':', qEnd);
        if (colon < 0) return -1;
        int vs = colon + 1;
        while (vs < s.length() && Character.isWhitespace(s.charAt(vs))) vs++;
        int[] next = {i};
        String val = parseObjectValue(s, vs, next);
        if (val == null) return -1; // 值体（对象/数组）未闭合（LLM 输出被截断）：交由调用方终止避免死循环
        int i2 = next[0];
        String vtxt = jsonValueToText(val).replaceAll("\n\\s*", "\n").trim();
        boolean multi = vtxt.contains("\n");
        if (sb.length() > 0) sb.append("\n");
        appendFieldText(sb, key, vtxt, multi);
        while (i2 < s.length() && s.charAt(i2) != ',') {
            if (s.charAt(i2) == '}') break;
            i2++;
        }
        if (i2 < s.length() && s.charAt(i2) == ',') i2++;
        return i2;
    }

    /** 以"键：值"单行或多行形式追加字段文本；多行时逐行缩进两格。 */
    private static void appendFieldText(StringBuilder sb, String key, String vtxt, boolean multi) {
        if (multi) {
            sb.append("- ").append(key).append("：\n");
            for (String ln : vtxt.split("\n")) sb.append("  ").append(ln).append("\n");
        } else {
            sb.append("- ").append(key).append("：").append(vtxt);
        }
    }

    /** 解析对象体内某字段的 JSON 值（vs 为冒号后首个非空白下标），返回值子串并把游标推进到其结尾。
     *  值体（对象/数组）未闭合（截断输出）时返回 null，交由调用方终止以规避死循环。 */
    private static String parseObjectValue(String s, int vs, int[] next) {
        if (vs >= s.length()) {
            next[0] = vs;
            return "";
        }
        char ch = s.charAt(vs);
        if (ch == '{') {
            int end = findObjectEnd(s, vs);
            if (end > vs) {
                next[0] = end + 1;
                return s.substring(vs, end + 1);
            }
            return null; // 对象括号未闭合（截断输出）
        }
        if (ch == '[') {
            int end = findArrayEnd(s, vs);
            if (end > vs) {
                next[0] = end + 1;
                return s.substring(vs, end + 1);
            }
            return null; // 数组未闭合（截断输出）
        }
        if (ch == '"') {
            int end = scanStringEnd(s, vs + 1); // 右引号可能缺失（截断输出）：夹取到末尾即止，确保游标不越过边界
            next[0] = Math.min(end, s.length());
            return s.substring(vs, Math.min(end + 1, s.length()));
        }
        int e = vs;
        while (e < s.length() && s.charAt(e) != ',' && s.charAt(e) != '}') e++;
        next[0] = e;
        return s.substring(vs, e);
    }

    /** 将临时对象文本累加器归一化：合并连续空行并去首尾空白，供 normal/未闭合提前返回两条路径复用。 */
    private static String collapseText(StringBuilder sb) {
        return sb.toString().replaceAll("\n{2,}", "\n").trim();
    }

    /** 从字符串字面量起始（from 为开引号后首字符）扫到结束引号，返回其下标（不含）；未闭合时返回 s.length()。 */
    private static int scanStringEnd(String s, int from) {
        int end = from;
        boolean esc = false;
        while (end < s.length()) {
            char c = s.charAt(end);
            if (esc) esc = false;
            else if (c == '\\') esc = true;
            else if (c == '"') return end;
            end++;
        }
        return end;
    }

    public static FileAnalysis parseFileAnalysisStatic(String key, String resp) {
        return parseFileAnalysisStatic(key, resp, false);
    }

    public static FileAnalysis parseFileAnalysisStatic(String key, String resp, boolean withContext) {
        String influence = withContext ? pickTextOrArray(resp, "contextInfluence", "上下文影响") : "";
        // 测试要点：从 JSON 数组（testPoints 等）提取
        List<String> testPoints = extractStringArray(resp, "testPoints", "test_points", "testingPoints", "测试要点");
        return new FileAnalysis(key, pickTextOrArray(resp, "intent", "意图"),
                pick(resp, "risk", DEFAULT_RISK),
                pickTextOrArray(resp, IMPACT, "影响"), testPoints, influence);
    }

    private static StageASummary parseStageA(String resp, boolean withContext) {
        return parseStageAStatic(resp, withContext);
    }

    private static FileAnalysis parseFileAnalysis(String key, String resp, boolean withContext) {
        return parseFileAnalysisStatic(key, resp, withContext);
    }

    private static String pick(String resp, String field, String def) {
        for (String line : resp.split("\n")) {
            // JSON 形态优先: "field": "value"（仅取本字段标量，避免越界吞掉后续字段）
            int j = indexOfFieldKey(line, field);
            if (j >= 0) {
                int c = line.indexOf(':', j);
                if (c >= 0) {
                    String val = extractJsonScalar(line, c + 1);
                    if (val != null && !val.isEmpty()) return val;
                }
            }
            // 纯文本形态: field: value（兜底，兼容非 JSON 输出）
            int i = line.indexOf(field + ":");
            if (i >= 0) return line.substring(i + field.length() + 1).trim().replaceAll("[\"',]", "");
        }
        return def;
    }

    /** 提取 JSON 字符串数组（候选字段名依次尝试）。未找到返回 null；找到空数组返回空列表。 */
    private static List<String> extractStringArray(String resp, String... fieldNames) {
        String inner = findJsonArray(resp, fieldNames);
        // 返回可变空列表：调用方在 markdown 回退路径上会继续 add；返回不可变集合会抛 UnsupportedOperationException
        if (inner == null) return new ArrayList<>();
        return parseStringArray(inner);
    }

    private static List<String> parseStringArray(String inner) {
        List<String> out = new ArrayList<>();
        boolean inStr = false;
        boolean escaped = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                cur.append(unescape(c));
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                if (inStr) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    inStr = false;
                } else {
                    inStr = true;
                }
            } else if (inStr) {
                cur.append(c);
            }
        }
        return out;
    }

    /** 提取 JSON 对象数组为 FileRisk 列表（候选字段名依次尝试）。未找到返回空列表。 */
    private static List<FileRisk> extractFileRiskArray(String resp, String... fieldNames) {
        String inner = findJsonArray(resp, fieldNames);
        // 返回可变空列表，避免调用方后续 add 时抛 UnsupportedOperationException
        if (inner == null) return new ArrayList<>();
        return parseFileRiskObjects(inner);
    }

    /** 解析一个 JSON 对象数组内部文本（不含外层方括号）为 FileRisk 列表。
     *  对象体的字符级切分复用 parseRawObjectBodies 状态机，本方法只做对象体到 FileRisk 的映射。 */
    private static List<FileRisk> parseFileRiskObjects(String inner) {
        List<FileRisk> out = new ArrayList<>();
        for (String obj : parseRawObjectBodies(inner)) {
            out.add(parseFileRiskObject(obj));
        }
        return out;
    }

    /** 解析单个 fileRisks 对象（key/risk/oneLineReason，兼容别名）。 */
    private static FileRisk parseFileRiskObject(String obj) {
        String key = pickObjectField(obj, "key", "fileName", "file");
        String risk = pickObjectField(obj, "risk", "riskLevel");
        String reason = pickObjectField(obj, "oneLineReason", "reason", "description");
        return new FileRisk(key.isEmpty() ? "?" : key, risk.isEmpty() ? DEFAULT_RISK : risk, reason);
    }

    /** 从子对象串中取字段（JSON 形态）。逐个候选名尝试，命中非空即返回。 */
    private static String pickObjectField(String obj, String... names) {
        for (String n : names) {
            String v = pick(obj, n, null);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    /** 在响应中定位首个候选字段名对应的 JSON 数组内部文本（不含外层方括号）。 */
    private static String findJsonArray(String resp, String... fieldNames) {
        for (String name : fieldNames) {
            String body = extractArrayBody(resp, name);
            if (body != null) {
                return body;
            }
        }
        return null;
    }

    /** 提取某个字段名对应 JSON 数组的内部文本；该字段不存在或无数组则返回 null。 */
    private static String extractArrayBody(String resp, String name) {
        int idx = indexOfFieldKey(resp, name);
        if (idx < 0) return null;
        int bracket = resp.indexOf('[', idx);
        if (bracket < 0) return null;
        int end = findArrayEnd(resp, bracket);
        if (end <= bracket) return null;
        return resp.substring(bracket + 1, end);
    }

    /** 找到从开括号开始匹配的数组结束位置（处理嵌套与字符串内括号），未匹配返回 -1。 */
    private static int findArrayEnd(String resp, int bracket) {
        int depth = 0;
        boolean inStr = false;
        boolean escaped = false;
        for (int i = bracket; i < resp.length(); i++) {
            char c = resp.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inStr = !inStr;
            } else if (!inStr) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    /** 还原 JSON 转义字符（用于数组字符串值内）。 */
    private static char unescape(char c) {
        switch (c) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            case 'b': return '\b';
            case 'f': return '\f';
            case '/': return '/';
            case '\\': return '\\';
            case '"': return '"';
            default: return c;
        }
    }

    /** 从 JSON 冒号后提取标量值（字符串或数字/布尔/null）。字符串取到匹配的右引号；其余取到逗号/右花括号。 */
    private static String extractJsonScalar(String line, int from) {
        int p = from;
        while (p < line.length() && (line.charAt(p) == ' ' || line.charAt(p) == '\t')) p++;
        if (p >= line.length()) return null;
        char ch = line.charAt(p);
        if (ch == '"') {
            int end = line.indexOf('"', p + 1);
            if (end > p) return line.substring(p + 1, end);
            return null;
        }
        int comma = line.indexOf(',', p);
        int rb = line.indexOf('}', p);
        int end;
        if (comma < 0) {
            end = (rb < 0) ? line.length() : rb;
        } else {
            end = (rb < 0) ? comma : Math.min(comma, rb);
        }
        if (end <= p) return null;
        return line.substring(p, end).trim();
    }

    private void writePrompt(String name, String content) {
        try {
            if (replayDir != null) {
                Files.createDirectories(replayDir);
                Files.write(replayDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // 非关键操作：prompt 文件写入失败不影响主流程
        }
    }

    private String readResponse(String name) {
        try {
            if (replayDir == null) return null;
            Path p = replayDir.resolve(name);
            if (!Files.exists(p)) return null;
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
