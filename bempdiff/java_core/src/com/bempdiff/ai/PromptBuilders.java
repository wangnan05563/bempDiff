package com.bempdiff.ai;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.config.AiConfig;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 两阶段 prompt 构造工具（离线可验证，不触网）。对应详细设计 §5.4 数据契约。
 *  - 阶段A：差异清单 + 每个改动文件的"摘要 diff"（截断策略：反编译源码取类签名/关键方法/前 N 行，文本取变更片段）
 *  - 阶段B：仅发完整 diffText（已脱敏）；按文件类型（Java 类 / 前端 JS/HTML/CSS）切换领域措辞
 *  - 脱敏：公网模型模式正则擦除疑似证件号/密钥字面量（详见 sanitize）
 */
public final class PromptBuilders {

    private PromptBuilders() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 阶段标题前缀（buildStageB 多处重复，提取为常量规避 S1192）。 */
    private static final String FILE_HEADER = "## 文件：";

    /** 阶段A prompt：让模型产出 JSON（整体风险/影响/测试主题 + 每文件初评）。 */
    public static String buildStageA(DiffResult diff, Map<String, DecompiledUnit> decompiled, AiConfig cfg) {
        return buildStageA(diff, decompiled, cfg, null);
    }

    /** 阶段A prompt（项目级上下文增强）：ctx 非空时注入「项目级上下文」章节并要求产出 contextInfluence。 */
    public static String buildStageA(DiffResult diff, Map<String, DecompiledUnit> decompiled,
                                     AiConfig cfg, ProjectContext ctx) {
        StringBuilder p = new StringBuilder();
        appendDiffHeader(p, diff, cfg);
        appendContextSection(p, ctx);
        appendFileSummaries(p, diff, decompiled, cfg);
        return sanitize(p.toString(), cfg);
    }

    /** 注入项目级上下文章节（ctx 为空则不注入，行为与旧版一致）。 */
    private static void appendContextSection(StringBuilder p, ProjectContext ctx) {
        if (ctx == null || ctx.isEmpty()) return;
        p.append(ctx.toPromptSection()).append("\n");
        p.append("请在上述【项目级上下文】约束下评估：架构分层 / 模块依赖 / 配置约定如何影响本次差异的")
         .append("风险与影响。输出 JSON 需额外包含字段 contextInfluence（字符串）：");
        p.append("说明项目级上下文如何修正、支撑或限定了你的结论（若无影响填\"无显著影响\"）。\n\n");
    }

    private static void appendDiffHeader(StringBuilder p, DiffResult diff, AiConfig cfg) {
        p.append("你是软件构建包（含 Java 后端与前端 JS/HTML/CSS 资源）升级的差异分析助手。下面是新/老两个构建包的差异清单，");
        p.append("请据此以 JSON 返回，字段固定为：\n");
        p.append("- `overallRisk`：整体风险等级(LOW/MEDIUM/HIGH)\n");
        p.append("- `impactScope`：影响范围描述（受影响的模块/对外接口）\n");
        p.append("- `testThemes`：全局测试要点字符串数组\n");
        p.append("- `fileRisks`：每个改动文件的初评数组，元素含 `key`(文件路径)、`risk`(LOW/MEDIUM/HIGH)、`oneLineReason`(一句话理由)\n\n");
        p.append("## 差异统计\n");
        p.append("新增=").append(diff.get(DiffStatus.ADDED).size())
                .append(" 删除=").append(diff.get(DiffStatus.DELETED).size())
                .append(" 修改=").append(diff.get(DiffStatus.MODIFIED).size())
                .append(" 未变=").append(diff.get(DiffStatus.UNCHANGED).size()).append("\n\n");
        p.append("## 改动文件与 diff 摘要（截断，最多 ").append(cfg.getStageATopK()).append(" 个）\n");
        p.append("> 文件类型：`.class`=Java 后端类；`.js`=前端 JavaScript；`.html`=前端模板；`.css`=前端样式。\n\n");
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
        p.append("\n### ").append(k).append(" (").append(statusName(stOf(diff, k)));
        FileClass fc = fileClassOf(k);
        if (fc != null) p.append("，").append(frontendLabel(fc));
        p.append(")\n");
        DecompiledUnit u = decompiled.get(k);
        if (u != null && u.isOk() && u.getDiffText() != null) {
            String[] lines = u.getDiffText().split("\n");
            int lim = Math.min(lines.length, cfg.getStageAFileSampleLines());
            for (int i = 0; i < lim; i++) p.append(lines[i]).append("\n");
            if (lines.length > lim) p.append("... (截断，共 ").append(lines.length).append(" 行)\n");
        } else {
            p.append("(无源码/未纳入 Top-K)\n");
        }
    }

    /** 阶段B 单文件 prompt：仅发完整 diffText；按文件类型切换领域措辞（Java / 前端）。 */
    public static String buildStageB(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg) {
        return buildStageB(key, unit, fc, cfg, null);
    }

    /** 阶段B 单文件 prompt（项目级上下文增强）：ctx 非空时附简要上下文并要求产出 contextInfluence。 */
    public static String buildStageB(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx) {
        StringBuilder p = new StringBuilder();
        if (ctx != null && !ctx.isEmpty()) {
            p.append("## 项目级上下文（简要）\n").append(ctx.getSummary()).append("\n");
            p.append("请在分析本文件改动时，结合上述上下文判断其所属模块、上下游依赖与配置约定。\n\n");
        }
        if (fc != null && fc.isFrontendText()) {
            p.append("请对以下前端").append(frontendLabel(fc)).append("代码改动做逐文件深读，返回 JSON：");
            p.append("改动意图(intent)、风险等级(risk: LOW/MEDIUM/HIGH)、影响范围(impact，含对页面/交互/接口的潜在影响)、测试要点(testPoints数组)、contextInfluence（项目上下文如何影响本文件结论）。\n\n");
            p.append(FILE_HEADER).append(key).append(" （").append(frontendLabel(fc)).append("）\n");
        } else if (fc == FileClass.JSP) {
            p.append("请对以下JSP页面/标签文件改动做逐文件深读，返回 JSON：");
            p.append("改动意图(intent)、风险等级(risk: LOW/MEDIUM/HIGH)、影响范围(impact，含对页面渲染/请求处理/标签逻辑的潜在影响)、测试要点(testPoints数组)、contextInfluence（项目上下文如何影响本文件结论）。\n\n");
            p.append(FILE_HEADER).append(key).append(" （JSP 页面/标签）\n");
        } else if (fc == FileClass.CONFIG) {
            p.append("请对以下配置文件（XML/Properties/YAML/JSON 等）改动做逐文件深读，返回 JSON：");
            p.append("改动意图(intent)、风险等级(risk: LOW/MEDIUM/HIGH)、影响范围(impact，含对配置项语义/模块行为/启动加载的潜在影响)、测试要点(testPoints数组)、contextInfluence（项目上下文如何影响本文件结论）。\n\n");
            p.append(FILE_HEADER).append(key).append(" （配置文件）\n");
        } else {
            p.append("请对以下Java类改动做逐文件深读，返回 JSON：");
            p.append("改动意图(intent)、风险等级(risk: LOW/MEDIUM/HIGH)、影响范围(impact)、测试要点(testPoints数组)、contextInfluence（项目上下文如何影响本文件结论）。\n\n");
            p.append(FILE_HEADER).append(key).append("\n");
        }
        p.append("```diff\n").append(unit.getDiffText() == null ? "" : unit.getDiffText()).append("\n```\n");
        return sanitize(p.toString(), cfg);
    }

    /** 文本文件类型中文标签（用于阶段A 摘要标注与阶段B 措辞）。 */
    private static String frontendLabel(FileClass fc) {
        switch (fc) {
            case JS: return "JavaScript";
            case HTML: return "HTML 模板";
            case CSS: return "CSS 样式";
            case JSP: return "JSP 页面";
            case CONFIG: return "配置文件";
            default: return "文本资源";
        }
    }

    /** 由文件扩展名推断文本类型（用于阶段A 摘要标注；class 返回 null）。 */
    private static FileClass fileClassOf(String key) {
        if (key.endsWith(".js")) return FileClass.JS;
        if (key.endsWith(".html") || key.endsWith(".htm")) return FileClass.HTML;
        if (key.endsWith(".css")) return FileClass.CSS;
        if (key.endsWith(".jsp") || key.endsWith(".jspx")
                || key.endsWith(".tag") || key.endsWith(".tagx")) return FileClass.JSP;
        if (key.endsWith(".xml") || key.endsWith(".properties") || key.endsWith(".yml")
                || key.endsWith(".yaml") || key.endsWith(".json") || key.endsWith(".conf")
                || key.endsWith(".cfg") || key.endsWith(".tld") || key.endsWith(".xhtml")
                || key.endsWith(".wsdl") || key.endsWith(".xsl") || key.endsWith(".xslt")
                || key.endsWith(".dtd") || key.endsWith(".vm") || key.endsWith(".ftl")
                || key.endsWith(".ini") || key.endsWith(".toml") || key.endsWith(".txt")
                || key.endsWith(".csv")) return FileClass.CONFIG;
        return null;
    }

    /** 脱敏：公网模型(o/a/z/q/custom 非本地)时，擦除疑似敏感字面量。 */
    public static String sanitize(String text, AiConfig cfg) {
        boolean local = "ollama".equalsIgnoreCase(cfg.getProvider())
                || cfg.getBaseUrl().contains("localhost") || cfg.getBaseUrl().contains("127.0.0.1");
        if (local) return text;  // 本地/私有化模型：代码不出机，无需脱敏
        // 擦除疑似身份证/密钥/手机号等（演示正则；量产版按业务规则细化）
        // 注意：用显式数字边界 (?<!\d)/(?!\d) 替代 \b，因为 Java 的 \b 在中文等
        // 非 ASCII 字符与数字相邻时不产生词边界，会导致「身份证110...」之类场景漏脱敏。
        return text
                .replaceAll("(?<!\\d)\\d{17}[\\dXx](?!\\d)", "***ID***")          // 18位身份证
                .replaceAll("(?<!\\w)(?:AKIA|AK|SK|KEY|SECRET|TOKEN|PASSWORD|PWD)[-.\\w=:]*[A-Za-z0-9+/=]{6,}", "***SECRET***")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "***PHONE***");         // 手机号
    }

    /** 追加聚焦指令：focus 非空时引导模型在该维度深入分析与结论（类别化报告的核心）。 */
    private static void appendFocus(StringBuilder p, String focus) {
        if (focus == null || focus.trim().isEmpty()) return;
        p.append("\n## 本次分析聚焦\n").append(focus.trim())
         .append("\n请在本聚焦维度上给出更详尽的分析与可执行的结论。\n");
    }

    /** 阶段A prompt（聚焦类别增强）：在基础 prompt 后追加聚焦指令，引导模型在指定维度深入。 */
    public static String buildStageA(DiffResult diff, Map<String, DecompiledUnit> decompiled,
                                     AiConfig cfg, ProjectContext ctx, String focus) {
        StringBuilder p = new StringBuilder(buildStageA(diff, decompiled, cfg, ctx));
        appendFocus(p, focus);
        return sanitize(p.toString(), cfg);
    }

    /** 阶段B 单文件 prompt（聚焦类别增强）。 */
    public static String buildStageB(String key, DecompiledUnit unit, FileClass fc, AiConfig cfg, ProjectContext ctx, String focus) {
        StringBuilder p = new StringBuilder(buildStageB(key, unit, fc, cfg, ctx));
        appendFocus(p, focus);
        return sanitize(p.toString(), cfg);
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
