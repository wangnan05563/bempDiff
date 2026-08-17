import com.bempdiff.ai.PromptBuilders;
import com.bempdiff.config.AiConfig;
import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.model.DecompiledUnit;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * FR-CX-01 落地驱动：读取持久化 AI 配置（~/.bempdiff/ui-config.properties），
 * 复用 BempDiff 内部 PackageParser/DiffEngine/Decompiler + PromptBuilders，
 * 直接调用 OpenAI 兼容端点（SiliconFlow Qwen3-8B）做两阶段分析，
 * 并把「智能化差异说明 + 变更影响 + 测试点」章节注入差异报告。
 *
 * 与 Main.ai 子命令的差异：本驱动解析 StageA/StageB 的【完整】字段
 * （overallRisk/impactScope/testThemes/fileRisks 与 intent/risk/impact/testPoints），
 * 而工具内置 MockAiAnalyzer 解析器会丢弃 testPoints/fileRisks，因此这里自行做严格 JSON 解析。
 *
 * 用法：
 *   java -cp "app.jar;out" AiReportDriver <oldJar> <newJar> <cfrJar> <reportMd> [uiConfigPath]
 */
public final class AiReportDriver {

    private String aiApiKey;
    private String aiBaseUrl;
    private String aiModel;
    private String aiProvider;
    private String rawDir; // 原始响应落盘，便于审计/排查

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("usage: AiReportDriver <oldJar> <newJar> <cfrJar> <reportMd> [uiConfigPath]");
            System.exit(2);
        }
        Path oldJar = Paths.get(args[0]);
        Path newJar = Paths.get(args[1]);
        Path cfrJar = Paths.get(args[2]);
        Path reportMd = Paths.get(args[3]);
        Path uiCfg = args.length > 4 ? Paths.get(args[4])
                : Paths.get(System.getProperty("user.home"), ".bempdiff", "ui-config.properties");

        AiReportDriver d = new AiReportDriver();
        d.loadConfig(uiCfg);
        d.rawDir = System.getProperty("java.io.tmpdir") + "/bemp_ai_raw";
        Files.createDirectories(Paths.get(d.rawDir));

        // 1) 差异 + 反编译（复用工具内部管线）
        PackageParser parser = new PackageParser();
        ParseConfig pcfg = new ParseConfig();
        PackageSnapshot oldSnap = parser.parse(oldJar, pcfg, false);
        PackageSnapshot newSnap = parser.parse(newJar, pcfg, false);
        DiffEngine engine = new DiffEngine();
        DiffResult r = engine.compute(oldSnap, newSnap);

        String javaBin = findJava();
        Decompiler dec = new Decompiler(cfrJar, javaBin);
        Map<String, DecompiledUnit> decompiled = new LinkedHashMap<>();
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, oldSnap, newSnap);
        int topK = 15;
        if (cands.size() > topK) cands = cands.subList(0, topK);
        for (String k : cands) {
            decompiled.put(k, dec.decompile(oldSnap, newSnap,
                    oldSnap.getEntries().get(k), newSnap.getEntries().get(k), k));
        }

        // 2) 配置 AiConfig（供 PromptBuilders.sanitize 判断是否脱敏）
        AiConfig cfg = new AiConfig();
        cfg.setProvider(d.aiProvider);
        cfg.setBaseUrl(d.aiBaseUrl);
        cfg.setApiKey(d.aiApiKey);
        cfg.setModel(d.aiModel);
        cfg.setStageBTopK(15);
        cfg.setEnabled(true);

        // 3) StageA：整体概览
        String stageAPrompt = PromptBuilders.buildStageA(r, decompiled, cfg)
                + "\n\n请严格以如下 JSON 结构返回（不要包裹 markdown 代码块，直接返回 JSON）：\n"
                + "{\n"
                + "  \"overallRisk\": \"LOW 或 MEDIUM 或 HIGH\",\n"
                + "  \"impactScope\": \"一句话描述影响模块/对外接口\",\n"
                + "  \"testThemes\": [\"全局测试要点1\", \"全局测试要点2\"],\n"
                + "  \"fileRisks\": [{\"key\":\"完整类名(与上面一致)\",\"risk\":\"LOW|MEDIUM|HIGH\",\"reason\":\"一句话理由\"}]\n"
                + "}\n";
        String stageARaw = d.chat(stageAPrompt, 0.2, "stageA");
        Map<String, Object> stageA = parseJsonObject(stageARaw);

        // 4) StageB：逐文件深读
        List<Map<String, Object>> stageB = new ArrayList<>();
        for (String k : decompiled.keySet()) {
            DecompiledUnit u = decompiled.get(k);
            if (u == null || !u.isOk() || u.getDiffText() == null) continue;
            LogicalEntry e = newSnap.getEntries().get(k);
            if (e == null) e = oldSnap.getEntries().get(k);
            FileClass fc = (e != null) ? e.getFileClass() : FileClass.CLASS;
            String per = PromptBuilders.buildStageB(k, u, fc, cfg)
                    + "\n\n请严格以如下 JSON 结构返回（不要包裹 markdown 代码块）：\n"
                    + "{\n"
                    + "  \"intent\": \"改动意图\",\n"
                    + "  \"risk\": \"LOW|MEDIUM|HIGH\",\n"
                    + "  \"impact\": \"影响范围\",\n"
                    + "  \"testPoints\": [\"测试要点1\", \"测试要点2\"]\n"
                    + "}\n";
            String raw = d.chat(per, 0.1, "stageB_" + sanitizeKey(k));
            Map<String, Object> m = parseJsonObject(raw);
            m.put("__key", k);
            stageB.add(m);
        }

        // 5) 组装章节并注入报告
        String chapter = buildChapter(stageA, stageB, d.aiBaseUrl, d.aiModel);
        injectChapter(reportMd, chapter);
        System.out.println("== DONE ==");
        System.out.println("整体风险=" + gs(stageA, "overallRisk"));
        System.out.println("StageB 文件数=" + stageB.size());
        System.out.println("报告已更新: " + reportMd);
    }

    private static String sanitizeKey(String k) {
        return k.replace('/', '_').replace('.', '_');
    }

    // ---------- 章节组装 ----------
    private static String buildChapter(Map<String, Object> a, List<Map<String, Object>> b,
                                        String baseUrl, String model) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 七、智能化差异说明与变更影响（AI 两阶段分析）\n\n");
        sb.append("> 分析引擎：").append(model).append("（").append(baseUrl)
                .append("，OpenAI 兼容）· 两阶段：StageA 整体概览 + StageB 逐文件深读。\n");
        sb.append("> 合规：公网模型模式下 prompt 已按工具内置规则脱敏（源码不出机、敏感字面量擦除）。\n\n");

        sb.append("### 7.1 整体风险概览（StageA）\n");
        sb.append("- **整体风险等级**：`").append(gs(a, "overallRisk")).append("`\n");
        String impact = gs(a, "impactScope");
        sb.append("- **影响范围**：").append(impact.isEmpty() ? "（模型未返回）" : impact).append("\n\n");
        sb.append("**全局测试要点**：\n");
        for (String t : ga(a, "testThemes")) sb.append("- ").append(t).append("\n");
        if (ga(a, "testThemes").isEmpty()) sb.append("- （模型未返回）\n");

        sb.append("\n### 7.2 逐文件深读（StageB）\n");
        for (Map<String, Object> m : b) {
            String key = gs(m, "__key");
            sb.append("#### `").append(key).append("`\n");
            sb.append("- **改动意图**：").append(gs(m, "intent")).append("\n");
            sb.append("- **风险等级**：`").append(gs(m, "risk")).append("`\n");
            sb.append("- **影响范围**：").append(gs(m, "impact")).append("\n");
            sb.append("- **测试要点**：\n");
            for (String tp : ga(m, "testPoints")) sb.append("  - ").append(tp).append("\n");
            if (ga(m, "testPoints").isEmpty()) sb.append("  - （模型未返回）\n");
            sb.append("\n");
        }

        sb.append("### 7.3 测试点汇总（供测试用例设计）\n");
        List<String> all = new ArrayList<>();
        for (String t : ga(a, "testThemes")) all.add(t);
        for (Map<String, Object> m : b) for (String tp : ga(m, "testPoints")) all.add(tp);
        for (String s : dedupe(all)) sb.append("- ").append(s).append("\n");
        if (all.isEmpty()) sb.append("- （模型未返回）\n");
        return sb.toString();
    }

    private static List<String> dedupe(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (!s.trim().isEmpty() && !out.contains(s.trim())) out.add(s.trim());
        return out;
    }

    // ---------- 注入报告（幂等：若已存在七章节则整体替换，避免重复追加） ----------
    private static void injectChapter(Path reportMd, String chapter) throws IOException {
        String content = new String(Files.readAllBytes(reportMd), StandardCharsets.UTF_8);
        // 幂等：移除旧「七、」章节（该章节为报告末尾，截到 EOF 即可）
        int idx = content.indexOf("## 七、");
        if (idx >= 0) content = content.substring(0, idx);
        // 更新审计摘要中的 AI 接入状态行（首次生效；已接入则无操作）
        content = content.replaceAll("AI 分析：未接入.*",
                "AI 分析：已接入（SiliconFlow Qwen3-8B 两阶段分析，详见「七、智能化差异说明与变更影响」）");
        // 追加新章节
        content = content + "\n" + chapter;
        Files.write(reportMd, content.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 配置加载 ----------
    private void loadConfig(Path uiCfg) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(uiCfg)) {
            p.load(in);
        }
        aiApiKey = p.getProperty("aiApiKey", "");
        aiBaseUrl = p.getProperty("aiBaseUrl", "https://api.openai.com/v1");
        aiModel = p.getProperty("aiModel", "gpt-4o");
        aiProvider = p.getProperty("aiProvider", "openai");
        if (aiApiKey.isEmpty()) throw new IllegalStateException("ui-config.properties 中未找到 aiApiKey");
    }

    // ---------- HTTP 调用（OpenAI 兼容，复刻 HttpAiAnalyzer 行为） ----------
    private String chat(String prompt, double temperature, String tag) {
        int attempt = 0;
        while (true) {
            try {
                return chatOnce(prompt, temperature, tag);
            } catch (IOException e) {
                attempt++;
                if (attempt >= 3) {
                    System.err.println("[AI][" + tag + "] 调用失败(重试耗尽): " + e.getMessage());
                    return "{}";
                }
                System.err.println("[AI][" + tag + "] 第" + attempt + "次失败，3s 后重试: " + e.getMessage());
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private String chatOnce(String prompt, double temperature, String tag) throws IOException {
        String endpoint = aiBaseUrl.endsWith("/") ? aiBaseUrl + "chat/completions"
                : aiBaseUrl + "/chat/completions";
        URL u = URI.create(endpoint).toURL();
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(60000);
        c.setReadTimeout(90000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Authorization", "Bearer " + aiApiKey);
            String body = "{\"model\":" + esc(aiModel) + ",\"temperature\":" + temperature
                    + ",\"messages\":[{\"role\":\"user\",\"content\":" + esc(prompt) + "}]}";
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            String resp = readAll(in);
            if (code < 200 || code >= 300) {
                System.err.println("[AI][" + tag + "] HTTP " + code + ": " + resp);
                throw new IOException("HTTP " + code + ": " + resp);
            }
            String contentTxt = extractContent(resp);
            // 落盘原始响应（审计/排查）
            try { Files.write(Paths.get(rawDir, tag + ".raw.txt"), resp.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
            try { Files.write(Paths.get(rawDir, tag + ".content.txt"), contentTxt.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
            return contentTxt;
    }

    private static String extractContent(String resp) {
        int ci = resp.indexOf("\"choices\"");
        if (ci < 0) return resp;
        int mi = resp.indexOf("\"message\"", ci);
        int from = (mi >= 0) ? mi : ci;
        int ci2 = resp.indexOf("\"content\"", from);
        if (ci2 < 0) return resp;
        int colon = resp.indexOf(':', ci2);
        if (colon < 0) return resp;
        int q = resp.indexOf('"', colon);
        if (q < 0) return resp;
        StringBuilder sb = new StringBuilder();
        int p = q + 1;
        boolean done = false;
        while (p < resp.length() && !done) {
            char ch = resp.charAt(p);
            if (ch == '\\') {
                if (p + 1 < resp.length()) {
                    char nx = resp.charAt(p + 1);
                    switch (nx) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        default: sb.append(nx);
                    }
                    p += 2;
                } else { p++; }
            } else if (ch == '"') {
                done = true;
            } else {
                sb.append(ch);
                p++;
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return "\"" + sb + "\"";
    }

    // ---------- 极简 JSON 解析（仅覆盖本场景：对象/数组/字符串/数字/布尔/null） ----------
    private static Map<String, Object> parseJsonObject(String s) {
        try {
            JParser p = new JParser(stripFences(s));
            Object o = p.parse();
            if (o instanceof Map) return castMap(o);
        } catch (Exception e) {
            System.err.println("[JSON] StageA/StageB 解析失败，降级： " + e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) { return (Map<String, Object>) o; }

    private static String stripFences(String s) {
        int fb = s.indexOf("{");
        int lb = s.lastIndexOf("}");
        if (fb >= 0 && lb > fb) return s.substring(fb, lb + 1);
        return s;
    }

    private static String gs(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static List<String> ga(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof List) {
            List<String> r = new ArrayList<>();
            for (Object o : (List<?>) v) r.add(String.valueOf(o));
            return r;
        }
        return new ArrayList<>();
    }

    private static String findJava() {
        String jh = System.getenv("JAVA_HOME");
        if (jh != null) {
            Path cand = Paths.get(jh, "bin", "java.exe");
            if (cand.toFile().isFile()) return cand.toString();
        }
        return "java";
    }

    /** 递归下降 JSON 解析器。 */
    private static final class JParser {
        final String s; int p;
        JParser(String s) { this.s = s; skipWs(); }
        void skipWs() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
        char peek() { return p < s.length() ? s.charAt(p) : '\0'; }

        Object parse() {
            skipWs();
            char c = peek();
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (c == 't' || c == 'f') return bool();
            if (c == 'n') { p += 4; return null; }
            return num();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            p++; skipWs();
            if (peek() == '}') { p++; return m; }
            while (true) {
                skipWs();
                String k = str();
                skipWs();
                if (peek() == ':') p++;
                Object v = parse();
                m.put(k, v);
                skipWs();
                if (peek() == ',') { p++; continue; }
                if (peek() == '}') { p++; break; }
                break;
            }
            return m;
        }

        List<Object> arr() {
            List<Object> a = new ArrayList<>();
            p++; skipWs();
            if (peek() == ']') { p++; return a; }
            while (true) {
                skipWs();
                a.add(parse());
                skipWs();
                if (peek() == ',') { p++; continue; }
                if (peek() == ']') { p++; break; }
                break;
            }
            return a;
        }

        String str() {
            StringBuilder b = new StringBuilder();
            if (peek() == '"') p++;
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '\\') {
                    if (p + 1 < s.length()) {
                        char e = s.charAt(p + 1);
                        switch (e) {
                            case 'n': b.append('\n'); break;
                            case 't': b.append('\t'); break;
                            case 'r': b.append('\r'); break;
                            case '"': b.append('"'); break;
                            case '\\': b.append('\\'); break;
                            case '/': b.append('/'); break;
                            default: b.append(e);
                        }
                        p += 2;
                    } else { p++; }
                } else if (c == '"') { p++; break; }
                else { b.append(c); p++; }
            }
            return b.toString();
        }

        Object num() {
            int start = p;
            while (p < s.length() && "-+.eE0123456789".indexOf(s.charAt(p)) >= 0) p++;
            return s.substring(start, p);
        }

        boolean bool() {
            if (s.startsWith("true", p)) { p += 4; return true; }
            p += 5; return false;
        }
    }
}
