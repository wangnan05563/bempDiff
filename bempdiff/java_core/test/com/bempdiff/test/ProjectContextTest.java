package com.bempdiff.test;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectContextAnalyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 项目级上下文扫描器测试：构建系统/模块/依赖/入口/配置/约定识别，以及空目录与扫描上限。 */
public final class ProjectContextTest {

    private static Path write(Path dir, String rel, String content) throws IOException {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    public void testAnalyze_mavenMultiModule() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        // 多模块 Maven
        write(root, "pom.xml",
                "<project><modules><module>billing-core</module><module>billing-web</module></modules>"
                + "<dependencies><dependency><artifactId>spring-boot-starter</artifactId></dependency>"
                + "<dependency><artifactId>mybatis</artifactId></dependency></dependencies></project>");
        write(root, "billing-core/src/main/java/com/x/BillingCoreApp.java",
                "package com.x; public class BillingCoreApp { public static void main(String[] a){} }");
        write(root, "billing-core/src/main/resources/application.yml", "server:\n  port: 8080");
        write(root, "billing-web/src/main/java/com/x/web/OrderController.java",
                "package com.x.web; public class OrderController {}");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("构建系统应为 Maven", "Maven", ctx.getBuildSystem());
        Asserts.assertTrue("应识别模块 billing-core", ctx.getModules().contains("billing-core"));
        Asserts.assertTrue("应识别模块 billing-web", ctx.getModules().contains("billing-web"));
        Asserts.assertTrue("应识别依赖 spring-boot-starter",
                ctx.getDependencies().contains("spring-boot-starter"));
        Asserts.assertTrue("应识别依赖 mybatis", ctx.getDependencies().contains("mybatis"));
        Asserts.assertTrue("应识别入口 BillingCoreApp",
                ctx.getEntryPoints().stream().anyMatch(s -> s.contains("BillingCoreApp")));
        Asserts.assertTrue("应识别配置 application.yml",
                ctx.getConfigFiles().stream().anyMatch(s -> s.endsWith("application.yml")));
        Asserts.assertFalse("不应为空上下文", ctx.isEmpty());
        Asserts.assertContains("prompt 章节应含项目级上下文标题",
                ctx.toPromptSection(), "项目级上下文");
    }

    public void testAnalyze_gradleSingleModule() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "build.gradle",
                "dependencies { implementation 'org.springframework.boot:spring-boot-starter-web' }");
        write(root, "settings.gradle", "rootProject.name = 'demo'");
        write(root, "src/main/java/com/demo/DemoApp.java",
                "package com.demo; public class DemoApp { public static void main(String[] a){} }");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("构建系统应为 Gradle", "Gradle", ctx.getBuildSystem());
        Asserts.assertTrue("应识别 spring 依赖",
                ctx.getDependencies().stream().anyMatch(d -> d.contains("spring-boot-starter-web")));
        Asserts.assertTrue("应识别入口 DemoApp",
                ctx.getEntryPoints().stream().anyMatch(s -> s.contains("DemoApp")));
    }

    public void testAnalyze_noneBuildSystem() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "readme.txt", "nothing special");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("无构建系统应标 none", "none", ctx.getBuildSystem());
        Asserts.assertContains("摘要应提示未识别", ctx.getSummary(), "未识别");
    }

    /** 部署产物核心场景：WAR 解包目录（无标准构建文件）应识别为 Java Web 部署包，并从 WEB-INF/lib 提取依赖。 */
    public void testAnalyze_warDeployment() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "META-INF/MANIFEST.MF",
                "Manifest-Version: 1.0\nImplementation-Title: bemp-served\nImplementation-Version: 2.1.0\n");
        write(root, "WEB-INF/classes/com/internal/A.class", "a");
        write(root, "WEB-INF/classes/properties/version.properties", "version=2.1.0");
        write(root, "WEB-INF/lib/spring-core-6.1.0.jar", "x");
        write(root, "WEB-INF/lib/mybatis-3.5.14.jar", "x");
        write(root, "WEB-INF/web.xml", "<web-app/>");
        write(root, "deploy.xml", "<deploy/>");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("WAR 解包应识别为部署包", "Java Web 部署包（WAR 解包）", ctx.getBuildSystem());
        Asserts.assertTrue("应提取 WEB-INF/lib 依赖 spring-core-6.1.0",
                ctx.getDependencies().contains("spring-core-6.1.0"));
        Asserts.assertTrue("应提取 WEB-INF/lib 依赖 mybatis-3.5.14",
                ctx.getDependencies().contains("mybatis-3.5.14"));
        Asserts.assertTrue("应识别 MANIFEST 为产物元信息",
                ctx.getConfigFiles().stream().anyMatch(s -> s.endsWith("META-INF/MANIFEST.MF")));
        Asserts.assertTrue("应识别 WEB-INF/classes 下 properties 为配置",
                ctx.getConfigFiles().stream().anyMatch(s -> s.endsWith("version.properties")));
        Asserts.assertTrue("约定应含部署包标识版本",
                ctx.getConventions().stream().anyMatch(s -> s.contains("bemp-served") && s.contains("2.1.0")));
        Asserts.assertContains("摘要应说明部署包结构", ctx.getSummary(), "部署包");
    }

    /** JAR 解包目录（仅 META-INF/MANIFEST，无 WEB-INF）应识别为 Java 应用产物。 */
    public void testAnalyze_jarDeployment() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Title: demo.jar\n");
        write(root, "com/demo/App.class", "a");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("JAR 解包应识别为应用产物", "Java 应用产物（JAR 解包）", ctx.getBuildSystem());
        Asserts.assertTrue("应识别 MANIFEST 元信息",
                ctx.getConfigFiles().stream().anyMatch(s -> s.endsWith("META-INF/MANIFEST.MF")));
    }

    /** 无根级构建文件、但子目录含独立构建文件时，应兜底识别子目录为模块（修复 collectFallbackModules 死代码）。 */
    public void testAnalyze_submoduleFallback() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "readme.txt", "loose layout");
        write(root, "billing-core/pom.xml", "<project/>");
        write(root, "billing-web/pom.xml", "<project/>");
        write(root, "shared-lib/build.gradle", "dependencies {}");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("无根级构建文件应仍为 none", "none", ctx.getBuildSystem());
        Asserts.assertTrue("应兜底识别子模块 billing-core",
                ctx.getModules().contains("billing-core"));
        Asserts.assertTrue("应兜底识别子模块 billing-web",
                ctx.getModules().contains("billing-web"));
        Asserts.assertTrue("应兜底识别 gradle 子模块 shared-lib",
                ctx.getModules().contains("shared-lib"));
    }

    /** 非标准 Java 源码布局（无构建文件、无 src/main/java）至少应识别为 Java 源码工程。 */
    public void testAnalyze_nonStandardSource() throws IOException {
        Path root = Files.createTempDirectory("bdctx");
        Files.createDirectories(root);
        write(root, "com/hundsun/bank/InterestController.java", "package com.hundsun.bank; public class InterestController {}");
        write(root, "com/hundsun/bank/InterestService.java", "package com.hundsun.bank; public class InterestService {}");

        ProjectContext ctx = ProjectContextAnalyzer.analyze(root);
        Asserts.assertEquals("非标准源码布局应识别为 Java 源码工程",
                "Java 源码工程（非标准布局）", ctx.getBuildSystem());
    }

    public void testAnalyze_nullOrInvalid() {
        ProjectContext ctx1 = ProjectContextAnalyzer.analyze(null);
        Asserts.assertTrue("null 应返回空上下文", ctx1.isEmpty());
        ProjectContext ctx2 = ProjectContextAnalyzer.analyze(
                java.nio.file.Paths.get("C:/__definitely_not_exist__/xyz"));
        Asserts.assertTrue("不存在目录应返回空上下文", ctx2.isEmpty());
    }
}
