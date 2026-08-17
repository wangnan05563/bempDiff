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

    public void testAnalyze_nullOrInvalid() {
        ProjectContext ctx1 = ProjectContextAnalyzer.analyze(null);
        Asserts.assertTrue("null 应返回空上下文", ctx1.isEmpty());
        ProjectContext ctx2 = ProjectContextAnalyzer.analyze(
                java.nio.file.Paths.get("C:/__definitely_not_exist__/xyz"));
        Asserts.assertTrue("不存在目录应返回空上下文", ctx2.isEmpty());
    }
}
