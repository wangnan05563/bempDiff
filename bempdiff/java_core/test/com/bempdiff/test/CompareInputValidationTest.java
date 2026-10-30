package com.bempdiff.test;

import com.bempdiff.server.BempServer;
import com.bempdiff.server.ServerConfig;

import java.io.FileNotFoundException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static com.bempdiff.test.ServerTestHarness.json;
import static com.bempdiff.test.ServerTestHarness.launch;
import static com.bempdiff.test.ServerTestHarness.newConfig;

/**
 * T03669 比对输入校验单测：{@link BempServer#validateCompareInput} 的各拒绝分支与
 * {@link BempServer#describeCompareFailure} 的前缀剥离，并端到端验证「路径不存在」在
 * 建作业之前就被 400 挡下（不进入解包/比对，也不返回 jobId）。
 *
 * <p>此前缺失路径一路走到 PackageParser 抛异常，错误只以数秒的 toast 出现，界面随后回落成
 * 与「两个包完全一致」无法区分的「暂无差异」空态；且后端与前端各加一次前缀，用户看到
 * 「比对失败：比对失败: …」。本测试守住这两个回归点。
 */
public final class CompareInputValidationTest {

    private static final String NO_PREFIX = "比对失败";

    // ---------------- 校验分支 ----------------

    public void testMissingNewPackageIsReportedAgainstNewSide() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            Path oldPkg = Files.write(tmp.resolve("app-1.0.0.war"), "x".getBytes("UTF-8"));
            String missing = tmp.resolve("app-2.0.0.war").toString();
            String err = BempServer.validateCompareInput(oldPkg.toString(), missing, "package");
            Asserts.assertNotNull("新包缺失应返回错误", err);
            Asserts.assertContains("应指明是新包", err, "新包文件不存在：");
            Asserts.assertContains("应带上绝对路径便于定位", err,
                    Paths.get(missing).toAbsolutePath().toString());
            Asserts.assertNotContains("原因句不得自带「比对失败」前缀（前缀由界面统一加）", err, NO_PREFIX);
        } finally {
            deleteTree(tmp);
        }
    }

    public void testOldPackageTakesPrecedenceOverNew() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            String missingOld = tmp.resolve("gone-1.war").toString();
            String missingNew = tmp.resolve("gone-2.war").toString();
            String err = BempServer.validateCompareInput(missingOld, missingNew, "package");
            Asserts.assertContains("两侧都缺失时应先报老包", err, "老包文件不存在：");
        } finally {
            deleteTree(tmp);
        }
    }

    public void testFolderModeMissingDirectory() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            Path left = Files.createDirectories(tmp.resolve("old"));
            String err = BempServer.validateCompareInput(
                    left.toString(), tmp.resolve("no-such-dir").toString(), "folder");
            Asserts.assertContains("目录模式缺失应说「目录不存在」而非「文件不存在」", err, "新包目录不存在：");
        } finally {
            deleteTree(tmp);
        }
    }

    public void testFolderModeRejectsPlainFile() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            Path left = Files.createDirectories(tmp.resolve("old"));
            Path rightIsFile = Files.write(tmp.resolve("new.txt"), "x".getBytes("UTF-8"));
            String err = BempServer.validateCompareInput(left.toString(), rightIsFile.toString(), "folder");
            Asserts.assertContains("目录模式选中文件应提示不是目录", err, "新包不是目录：");
        } finally {
            deleteTree(tmp);
        }
    }

    public void testPackageModeRejectsDirectory() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            Path oldDir = Files.createDirectories(tmp.resolve("old-dir"));
            Path newDir = Files.createDirectories(tmp.resolve("new-dir"));
            String err = BempServer.validateCompareInput(oldDir.toString(), newDir.toString(), "package");
            Asserts.assertContains("包模式选中目录应引导改用目录对比", err, "老包是目录，请改用目录对比：");
        } finally {
            deleteTree(tmp);
        }
    }

    public void testValidPairPasses() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ");
        try {
            Path a = Files.write(tmp.resolve("app-1.0.0.war"), "a".getBytes("UTF-8"));
            Path b = Files.write(tmp.resolve("app-1.0.1.war"), "b".getBytes("UTF-8"));
            Asserts.assertNull("两个存在的包不应被拦截", BempServer.validateCompareInput(
                    a.toString(), b.toString(), "package"));
            Path d1 = Files.createDirectories(tmp.resolve("f1"));
            Path d2 = Files.createDirectories(tmp.resolve("f2"));
            Asserts.assertNull("两个存在的目录不应被拦截", BempServer.validateCompareInput(
                    d1.toString(), d2.toString(), "folder"));
        } finally {
            deleteTree(tmp);
        }
    }

    public void testIllegalPathDoesNotThrow() {
        // NUL 字符会让 Paths.get 抛 InvalidPathException：校验器必须兜住并转成用户可读提示，
        // 否则异常会冒到 handleCompare 的 catch，退化成回不来的 500。
        String err = BempServer.validateCompareInput("bad\0path", "also\0bad", "package");
        Asserts.assertContains("非法路径应给出「路径不合法」", err, "老包路径不合法：");
    }

    // ---------------- 失败原因短句（去前缀） ----------------

    public void testDescribeFileNotFound() {
        String r = BempServer.describeCompareFailure(new FileNotFoundException("D:/pkgs/a.war"));
        Asserts.assertEquals("FileNotFoundException 归一为「文件不存在」", "文件不存在：D:/pkgs/a.war", r);
        Asserts.assertNotContains("不得自带「比对失败」前缀", r, NO_PREFIX);
    }

    public void testDescribeNoSuchFile() {
        String r = BempServer.describeCompareFailure(
                new java.nio.file.NoSuchFileException("D:/pkgs/b.war"));
        Asserts.assertEquals("NoSuchFileException 归一为「文件不存在」", "文件不存在：D:/pkgs/b.war", r);
    }

    public void testDescribeKeepsPlainMessageAndHandlesNull() {
        Asserts.assertEquals("普通异常原样透出原因", "解压失败：CRC 不匹配",
                BempServer.describeCompareFailure(new IllegalStateException("解压失败：CRC 不匹配")));
        Asserts.assertEquals("无 message 时退化为类名，不显示 null",
                "IllegalStateException",
                BempServer.describeCompareFailure(new IllegalStateException()));
        Asserts.assertEquals("空 message 同样退化为类名", "IllegalArgumentException",
                BempServer.describeCompareFailure(new IllegalArgumentException("")));
    }

    // ---------------- 端到端：建作业之前就被 400 挡下 ----------------

    public void testHttpRejectsMissingPathBeforeCreatingJob() throws Exception {
        Path tmp = Files.createTempDirectory("bdf-civ-http");
        try {
            Path oldPkg = Files.write(tmp.resolve("app-1.0.0.war"), "x".getBytes("UTF-8"));
            ServerConfig config = newConfig(tmp);
            ServerTestHarness.Server s = launch(config, Files.createDirectories(tmp.resolve("webroot")));

            String body = ServerTestHarness.compareBody(
                    "package", oldPkg.toString(), tmp.resolve("nope.war").toString());
            HttpResponse<String> resp = s.post("/api/session/compare", body);
            Asserts.assertEquals("缺失路径应在提交阶段直接 400", 400, resp.statusCode());
            Map<String, Object> j = json(resp);
            String msg = String.valueOf(j.get("error"));
            Asserts.assertContains("400 原因应说明新包文件不存在", msg, "新包文件不存在：");
            Asserts.assertNull("被拦截的请求不应产生作业", j.get("jobId"));

            // 同一请求体把路径换成真实存在的包，校验放行（这里只验证「合法路径不被误拦」，
            // 完整比对流水线由 ServerCompareFlowTest 覆盖）。
            Path newPkg = Files.write(tmp.resolve("app-1.0.1.war"), "y".getBytes("UTF-8"));
            HttpResponse<String> ok = s.post("/api/session/compare",
                    ServerTestHarness.compareBody("package", oldPkg.toString(), newPkg.toString()));
            Asserts.assertEquals("合法路径不应被输入校验拦截", 200, ok.statusCode());
            Asserts.assertNotNull("合法请求应返回 jobId", json(ok).get("jobId"));
        } finally {
            deleteTree(tmp);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 临时目录清理失败不影响测试结论
                }
            });
        } catch (Exception ignored) {
        }
    }
}
