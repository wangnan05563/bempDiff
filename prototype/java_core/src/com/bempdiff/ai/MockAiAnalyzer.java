package com.bempdiff.ai;

import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;

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
    public String buildStageBPrompt(String key, DecompiledUnit unit, AiConfig cfg) {
        return PromptBuilders.buildStageB(key, unit, cfg);
    }

    @Override
    public StageASummary stageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        String prompt = buildStageAPrompt(diff, decompiled, cfg);
        writePrompt("stageA.prompt.txt", prompt);
        String resp = readResponse("stageA.response.txt");
        if (resp != null) return parseStageA(resp);
        // 兜底摘要（离线无回放文件时）
        StageASummary s = new StageASummary();
        s.setOverallRisk(DEFAULT_RISK);
        s.setImpactScope("离线回放模式：未提供 stageA.response.txt，使用内置兜底摘要。差异文件数="
                + (diff.get(DiffStatus.ADDED).size() + diff.get(DiffStatus.DELETED).size() + diff.get(DiffStatus.MODIFIED).size()));
        s.setTestThemes(java.util.Arrays.asList("回归核心业务流程", "校验对外接口兼容性", "验证删除类无外部依赖"));
        s.setFileRisks(new ArrayList<>());
        return s;
    }

    @Override
    public List<FileAnalysis> stageB(List<DecompileReq> candidates, AiConfig cfg) {
        List<FileAnalysis> out = new ArrayList<>();
        int idx = 0;
        for (DecompileReq req : candidates) {
            String prompt = buildStageBPrompt(req.key, req.unit, cfg);
            writePrompt("stageB." + idx + ".prompt.txt", prompt);
            String resp = readResponse("stageB." + idx + ".response.txt");
            if (resp != null) {
                out.add(parseFileAnalysis(req.key, resp));
            } else {
                out.add(new FileAnalysis(req.key, "离线回放模式：未提供回放响应", DEFAULT_RISK,
                        "需人工核对", java.util.Arrays.asList("回归该类的调用方")));
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
        StageASummary s = new StageASummary();
        s.setOverallRisk(pick(resp, "overallRisk", DEFAULT_RISK));
        s.setImpactScope(pick(resp, "impactScope", ""));
        List<String> themes = new ArrayList<>();
        for (String t : resp.split("\n")) {
            String t2 = t.trim();
            if (t2.startsWith("-") && (t2.contains("测试") || t2.contains("回归") || t2.contains("验证"))) {
                themes.add(t2.replaceFirst("^-", "").trim());
            }
        }
        s.setTestThemes(themes);
        s.setFileRisks(new ArrayList<>());
        return s;
    }

    public static FileAnalysis parseFileAnalysisStatic(String key, String resp) {
        return new FileAnalysis(key, pick(resp, "intent", ""), pick(resp, "risk", DEFAULT_RISK),
                pick(resp, "impact", ""), new ArrayList<>());
    }

    private static StageASummary parseStageA(String resp) {
        return parseStageAStatic(resp);
    }

    private static FileAnalysis parseFileAnalysis(String key, String resp) {
        return parseFileAnalysisStatic(key, resp);
    }

    private static String pick(String resp, String field, String def) {
        for (String line : resp.split("\n")) {
            // 纯文本形态: field: value
            int i = line.indexOf(field + ":");
            if (i >= 0) return line.substring(i + field.length() + 1).trim().replaceAll("[\"',]", "");
            // JSON 形态: "field": "value"（仅取本字段标量，避免越界吞掉后续字段）
            int j = line.indexOf("\"" + field + "\"");  // JSON 形态
            if (j >= 0) {
                int c = line.indexOf(':', j);
                if (c >= 0) {
                    String val = extractJsonScalar(line, c + 1);
                    if (val != null) return val;
                }
            }
        }
        return def;
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
