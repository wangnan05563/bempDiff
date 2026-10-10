package com.bempdiff.test;

import com.bempdiff.server.DiagBundle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * FR-011 脱敏诊断包测试。
 * 验收①：包内不含 API Key / 用户路径敏感段（双保险：内容白名单 + 行级脱敏）；
 * 验收②：体积 <5MB（日志尾部截断护栏）。
 * 日志目录注入临时路径——绝不读写真实 {user.home}/.bempdiff/logs。
 */
public final class DiagBundleTest {

    /** 验收①：密钥字面量与用户目录路径全部被脱敏替换。 */
    public void testSanitizeRedactsCredentialsAndHome() throws Exception {
        Path logs = Files.createTempDirectory("bempdiff-diagtest-logs");
        Path out = Files.createTempDirectory("bempdiff-diagtest-out");
        String home = System.getProperty("user.home", "");
        String log = "2026-10-10 INFO 启动正常\n"
                + "2026-10-10 ERROR AI 调用失败 apiKey=sk-abcdef1234567890XYZ\n"
                + "2026-10-10 WARN request header Authorization: Bearer tok_9f8e7d6c5b4a\n"
                + "2026-10-10 INFO 缓存目录 " + home + "\\.bempdiff\\logs\\unpack-x.log\n"
                + "2026-10-10 INFO working dir " + System.getProperty("user.dir", "") + "\\out\n";
        Files.write(logs.resolve("shell.log"), log.getBytes(StandardCharsets.UTF_8));
        // 非 .log/.json 文件不入包（内容白名单）
        Files.write(logs.resolve("notes.txt"), "不应入包".getBytes(StandardCharsets.UTF_8));

        Path zip = DiagBundle.build(out, logs);
        Map<String, String> entries = readZip(zip);

        Asserts.assertTrue("包内应有 env.json", entries.containsKey("env.json"));
        Asserts.assertTrue("包内应有 shell.log", entries.containsKey("logs/shell.log"));
        Asserts.assertTrue("txt 白名单外文件不应入包", !entries.containsKey("logs/notes.txt"));

        String body = entries.get("logs/shell.log");
        Asserts.assertTrue("api key 值应被脱敏", !body.contains("sk-abcdef1234567890XYZ"));
        Asserts.assertContains("api key 应留键名+占位符", body, "apiKey=<redacted>");
        Asserts.assertTrue("Bearer token 应被脱敏", !body.contains("tok_9f8e7d6c5b4a"));
        if (!home.isEmpty()) {
            Asserts.assertTrue("用户主目录路径应被替换", !body.contains(home));
            Asserts.assertContains("主目录应替换为 <home> 占位", body, "<home>");
        }
        Asserts.assertTrue("工作目录路径应被替换",
                !body.contains(System.getProperty("user.dir", "") + "\\out"));

        // env.json 白名单字段：无任何真实路径/密钥
        String env = entries.get("env.json");
        Asserts.assertTrue("env.json 不应含 user.home", !env.contains(home));
        Asserts.assertTrue("env.json 不应含密钥字面量", !env.contains("sk-"));
        Asserts.assertContains("env.json 应含 osName", env, "osName");

        // 验收②：小场景下远小于 5MB
        Asserts.assertTrue("包体应 <5MB", Files.size(zip) < DiagBundle.MAX_BUNDLE_BYTES);
    }

    /** 验收②：超长日志只取尾部 256KB，且头部残行被丢弃。 */
    public void testLongLogTailTruncation() throws Exception {
        Path logs = Files.createTempDirectory("bempdiff-diagtest-logs2");
        Path out = Files.createTempDirectory("bempdiff-diagtest-out2");
        StringBuilder sb = new StringBuilder();
        String marker = "TAIL-MARKER-FINE";
        for (int i = 0; i < 40_000; i++) sb.append("line-").append(i).append(" padding-0123456789\n");
        sb.append(marker).append('\n');
        Files.write(logs.resolve("big.log"), sb.toString().getBytes(StandardCharsets.UTF_8));

        Path zip = DiagBundle.build(out, logs);
        Map<String, String> entries = readZip(zip);
        String body = entries.get("logs/big.log");
        Asserts.assertTrue("超长日志应入包", body != null);
        Asserts.assertContains("尾部出错现场应保留", body, marker);
        Asserts.assertTrue("入包体积不应超尾部护栏(256KB+行级变化余量)",
                body.getBytes(StandardCharsets.UTF_8).length <= DiagBundle.LOG_TAIL_BYTES + 4096);
        Asserts.assertTrue("日志头部(截断残片)不应入包", !body.contains("line-0 "));
    }

    /** 日志目录不存在 → 仅有 env.json 的合法空包（不抛错）。 */
    public void testMissingLogsDirYieldsEnvOnly() throws Exception {
        Path out = Files.createTempDirectory("bempdiff-diagtest-out3");
        Path zip = DiagBundle.build(out, out.resolve("no-such-logs"));
        Map<String, String> entries = readZip(zip);
        Asserts.assertTrue("空日志场景应有 env.json", entries.containsKey("env.json"));
        Asserts.assertTrue("不应有别的条目", entries.size() == 1);
    }

    private static Map<String, String> readZip(Path zip) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
                entries.put(e.getName(), new String(bos.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
