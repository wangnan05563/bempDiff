package com.bempdiff.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FR-011 一键导出脱敏诊断包：出错时导出脱敏日志 + 环境信息 zip，替代口头描述环境。
 *
 * <p>脱敏白名单机制（验收①：包内不含 API Key / 用户路径敏感段）——
 * 双保险设计而非黑名单枚举：
 * <ul>
 *   <li>包内容物白名单：只有 env.json（代码构造的固定字段，天然无敏感值）与 logs/ 下
 *       文本日志两类，配置文件/差异内容/反编译产物一律不入包；</li>
 *   <li>行级脱敏：日志逐行过 sanitizeLine，密钥字面量与用户目录路径替换为占位符。</li>
 * </ul>
 *
 * <p>体积护栏（验收②：&lt;5MB）：每个日志文件仅取尾部 256KB（出错现场通常在尾部），
 * 全包累计写满 5MB 即停止并写入 manifest 说明截断，绝不让诊断包本身拖垮磁盘。
 */
public final class DiagBundle { // NOSONAR - 单一职责：诊断包构建器

    /** 诊断包总体积上限（验收②）。 */
    public static final long MAX_BUNDLE_BYTES = 5L * 1024 * 1024;
    /** 单个日志文件最多入包的尾部字节数（出错现场通常在日志尾部）。 */
    public static final int LOG_TAIL_BYTES = 256 * 1024;

    /** 密钥/凭证字面量：Bearer 头、key=value 与 sk- 前缀三类高置信模式，宁可多脱不可漏脱。 */
    private static final Pattern P_BEARER = Pattern.compile(
            "(?i)(bearer\\s+)([A-Za-z0-9._~+/\\-]+=*)");
    private static final String BEARER_REPL = "$1<redacted>";
    private static final Pattern P_CRED_KV = Pattern.compile(
            "(?i)((?:api[_-]?key|apikey|authorization|token|bearer|secret|password)\\s*[:=]\\s*)(\\S+)");
    private static final String CRED_REPL = "$1<redacted>";
    private static final Pattern P_SK = Pattern.compile("sk-[A-Za-z0-9_\\-]{8,}");
    private static final String SK_REPL = "sk-<redacted>";

    private DiagBundle() {}

    /** 构建诊断包（默认日志目录 {user.home}/.bempdiff/logs），返回 zip 落盘路径。 */
    public static Path build(Path outDir) throws IOException {
        return build(outDir, Paths.get(System.getProperty("user.home", ""), ".bempdiff", "logs"));
    }

    /** 构建诊断包（日志目录可注入：生产用默认值，测试注入临时目录避免污染真实用户目录）。 */
    public static Path build(Path outDir, Path logsDir) throws IOException {
        Files.createDirectories(outDir);
        Path zip = Files.createTempFile(outDir, "bempdiff-diag-", ".zip");
        List<String> truncated = new ArrayList<>();
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putBytes(zos, "env.json", envJson());
            long budget = MAX_BUNDLE_BYTES - 64 * 1024; // 预留 manifest 与 env 自身体积
            for (Path log : listLogs(logsDir)) {
                if (budget <= 0) { truncated.add(log.getFileName() + " (预算耗尽未入包)"); break; }
                budget = putLogTail(zos, log, budget, truncated);
            }
            if (!truncated.isEmpty()) {
                StringBuilder sb = new StringBuilder("以下内容因体积护栏被截断或未入包：\n");
                for (String s : truncated) sb.append("- ").append(s).append("\n");
                putBytes(zos, "manifest.txt", sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return zip;
    }

    /** 环境信息白名单字段：只取系统级只读属性，不含用户名/路径/配置。 */
    static byte[] envJson() {
        Runtime rt = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"generatedAt\": \"").append(now()).append("\",\n");
        sb.append("  \"app\": \"bempdiff\",\n");
        sb.append("  \"javaVersion\": \"").append(sys("java.version")).append("\",\n");
        sb.append("  \"javaVendor\": \"").append(sys("java.vm.vendor")).append("\",\n");
        sb.append("  \"osName\": \"").append(sys("os.name")).append("\",\n");
        sb.append("  \"osArch\": \"").append(sys("os.arch")).append("\",\n");
        sb.append("  \"osVersion\": \"").append(sys("os.version")).append("\",\n");
        sb.append("  \"userLanguage\": \"").append(sys("user.language")).append("\",\n");
        sb.append("  \"fileEncoding\": \"").append(sys("file.encoding")).append("\",\n");
        sb.append("  \"processors\": ").append(rt.availableProcessors()).append(",\n");
        sb.append("  \"maxMemoryBytes\": ").append(rt.maxMemory()).append("\n");
        sb.append("}\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 行级脱敏：密钥字面量与用户目录路径替换为占位符（验收①）。 */
    static String sanitizeLine(String line) {
        if (line == null || line.isEmpty()) return line;
        // 先 Bearer 后 KV：KV 模式只吞「Authorization:」后的第一个 \S+（即 "Bearer" 一词），
        // token 本体在第二个词——先让 Bearer 模式把本体脱掉，KV 兜底裸 key=value 形态
        String out = P_BEARER.matcher(line).replaceAll(BEARER_REPL);
        out = P_CRED_KV.matcher(out).replaceAll(CRED_REPL);
        out = P_SK.matcher(out).replaceAll(SK_REPL);
        String home = System.getProperty("user.home", "");
        String dir = System.getProperty("user.dir", "");
        if (!home.isEmpty()) out = out.replace(home, "<home>");
        if (!dir.isEmpty()) out = out.replace(dir, "<workdir>");
        return out;
    }

    /** 单文件尾部入包：读尾部 256KB、逐行脱敏后写入 zip；返回剩余预算。 */
    private static long putLogTail(ZipOutputStream zos, Path log, long budget,
                                   List<String> truncated) throws IOException {
        byte[] raw = Files.readAllBytes(log);
        byte[] tail = raw.length > LOG_TAIL_BYTES
                ? java.util.Arrays.copyOfRange(raw, raw.length - LOG_TAIL_BYTES, raw.length) : raw;
        String text = new String(tail, StandardCharsets.UTF_8);
        // 首行可能因尾部截断而残缺，丢弃首行残片（若发生截断）
        if (tail.length != raw.length) {
            int nl = text.indexOf('\n');
            if (nl >= 0) text = text.substring(nl + 1);
        }
        StringBuilder clean = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) clean.append(sanitizeLine(line)).append('\n');
        byte[] body = clean.toString().getBytes(StandardCharsets.UTF_8);
        String entryName = "logs/" + log.getFileName();
        if (body.length > budget) {
            truncated.add(entryName + " (超剩余预算未入包)");
            return budget;
        }
        putBytes(zos, entryName, body);
        return budget - body.length;
    }

    /** 收集日志目录下的 .log/.json 报告（目录不存在返回空列表，不抛错）。 */
    private static List<Path> listLogs(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (Files.isRegularFile(p) && (n.endsWith(".log") || n.endsWith(".json"))) out.add(p);
            }
        } catch (IOException | UncheckedIOException e) {
            // 日志目录读不了不影响诊断包其它内容——环境信息已足以定位平台/版本
        }
        out.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return out;
    }

    private static void putBytes(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static String sys(String key) {
        String v = System.getProperty(key, "");
        return v.replace("\\", "\\\\").replace("\"", "'");
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
