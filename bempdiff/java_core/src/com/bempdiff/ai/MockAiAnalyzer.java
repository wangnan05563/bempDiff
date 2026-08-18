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
        writePrompt("stageA.prompt.txt", prompt);
        String resp = readResponse("stageA.response.txt");
        if (resp != null) return parseStageA(resp, false);
        return buildFallbackSummary(diff, null);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx);
        writePrompt("stageA.prompt.txt", prompt);
        String resp = readResponse("stageA.response.txt");
        boolean withCtx = ctx != null && !ctx.isEmpty();
        if (resp != null) return parseStageA(resp, withCtx);
        return buildFallbackSummary(diff, ctx);
    }

    private static StageASummary buildFallbackSummary(DiffResult diff, ProjectContext ctx) {
        StageASummary s = new StageASummary();
        s.setOverallRisk(DEFAULT_RISK);
        int changed = diff.get(DiffStatus.ADDED).size() + diff.get(DiffStatus.DELETED).size()
                + diff.get(DiffStatus.MODIFIED).size();
        if (ctx != null && !ctx.isEmpty()) {
            s.setImpactScope("离线回放模式（含项目上下文[" + ctx.getBuildSystem() + "]）：未提供 stageA.response.txt，"
                    + "使用内置兜底摘要。差异文件数=" + changed);
            s.setContextInfluence("已结合项目上下文（构建系统=" + ctx.getBuildSystem()
                    + "）做基础判断；未接入 LLM，影响评估限于结构层面。");
        } else {
            s.setImpactScope("离线回放模式：未提供 stageA.response.txt，使用内置兜底摘要。差异文件数=" + changed);
        }
        s.setTestThemes(java.util.Arrays.asList("回归核心业务流程", "校验对外接口兼容性", "验证删除类无外部依赖"));
        s.setFileRisks(new ArrayList<>());
        return s;
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg) {
        return stageB(candidates, cfg, null);
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx) {
        List<FileAnalysis> out = new ArrayList<>();
        int idx = 0;
        boolean withCtx = ctx != null && !ctx.isEmpty();
        for (DecompileReq req : candidates) {
            String prompt = PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, ctx);
            writePrompt("stageB." + idx + ".prompt.txt", prompt);
            String resp = readResponse("stageB." + idx + ".response.txt");
            if (resp != null) {
                out.add(parseFileAnalysis(req.key, resp, withCtx));
            } else {
                String influence = withCtx
                        ? "已结合项目上下文（" + ctx.getBuildSystem() + "）做基础判断；未接入 LLM。"
                        : "";
                out.add(new FileAnalysis(req.key, "离线回放模式：未提供回放响应", DEFAULT_RISK,
                        "需人工核对", java.util.Arrays.asList("回归该类的调用方"), influence));
            }
            idx++;
        }
        return out;
    }

    @Override
    public String buildStageAPrompt(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
    }

    @Override
    public String buildStageBPrompt(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx, String focus) {
        return PromptBuilders.buildStageB(key, unit, fc, cfg, ctx, focus);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg, ProjectContext ctx, String focus) {
        String prompt = PromptBuilders.buildStageA(diff, decompiled, cfg, ctx, focus);
        writePrompt("stageA.prompt.txt", prompt);
        String resp = readResponse("stageA.response.txt");
        boolean withCtx = ctx != null && !ctx.isEmpty();
        if (resp != null) return parseStageA(resp, withCtx);
        return buildFallbackSummary(diff, ctx);
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg, ProjectContext ctx, String focus) {
        List<FileAnalysis> out = new ArrayList<>();
        int idx = 0;
        boolean withCtx = ctx != null && !ctx.isEmpty();
        for (DecompileReq req : candidates) {
            String prompt = PromptBuilders.buildStageB(req.key, req.unit, req.fileClass, cfg, ctx, focus);
            writePrompt("stageB." + idx + ".prompt.txt", prompt);
            String resp = readResponse("stageB." + idx + ".response.txt");
            if (resp != null) {
                out.add(parseFileAnalysis(req.key, resp, withCtx));
            } else {
                String influence = withCtx
                        ? "已结合项目上下文（" + ctx.getBuildSystem() + "）做基础判断；未接入 LLM。"
                        : "";
                out.add(new FileAnalysis(req.key, "离线回放模式：未提供回放响应", DEFAULT_RISK,
                        "需人工核对", java.util.Arrays.asList("回归该类的调用方"), influence));
            }
            idx++;
        }
        return out;
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
        s.setImpactScope(pick(resp, "impactScope", ""));
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
            s.setContextInfluence(pick(resp, "contextInfluence", pick(resp, "上下文影响", "")));
        }
        return s;
    }

    public static FileAnalysis parseFileAnalysisStatic(String key, String resp) {
        return parseFileAnalysisStatic(key, resp, false);
    }

    public static FileAnalysis parseFileAnalysisStatic(String key, String resp, boolean withContext) {
        String influence = withContext ? pick(resp, "contextInfluence", pick(resp, "上下文影响", "")) : "";
        // 测试要点：从 JSON 数组（testPoints 等）提取
        List<String> testPoints = extractStringArray(resp, "testPoints", "test_points", "testingPoints", "测试要点");
        return new FileAnalysis(key, pick(resp, "intent", ""), pick(resp, "risk", DEFAULT_RISK),
                pick(resp, "impact", ""), testPoints, influence);
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
            int j = line.indexOf("\"" + field + "\"");
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

    /** 解析一个 JSON 对象数组内部文本（不含外层方括号）为 FileRisk 列表。 */
    private static List<FileRisk> parseFileRiskObjects(String inner) { // NOSONAR(S3776) - 字符级状态机解析，复杂度源于必要分支
        List<FileRisk> out = new ArrayList<>();
        int depth = 0;
        int objStart = -1;
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
                if (c == '{') {
                    if (depth == 0) {
                        objStart = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart >= 0) {
                        out.add(parseFileRiskObject(inner.substring(objStart + 1, i)));
                        objStart = -1;
                    }
                }
            }
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
        int idx = resp.indexOf("\"" + name + "\"");
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
