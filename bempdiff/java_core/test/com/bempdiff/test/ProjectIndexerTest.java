package com.bempdiff.test;

import com.bempdiff.ai.context.ProjectContext;
import com.bempdiff.ai.context.ProjectIndex;
import com.bempdiff.ai.context.ProjectIndexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 上下文目录递归识别器测试：Maven 聚合边界、孤立模块、前端工程、node_modules 剪枝、单项目兜底。 */
public final class ProjectIndexerTest {

    private static Path write(Path dir, String rel, String content) throws IOException {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    private static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    public void testScan_multiMavenAggregatorAndFrontend() throws IOException {
        Path root = Files.createTempDirectory("bdidx");
        Files.createDirectories(root);
        // 聚合工程 adapter：聚合 pom 引用 api/client-api 两个叶子
        write(root, "adapter/pom.xml",
                "<project><packaging>pom</packaging><modules><module>api</module><module>client-api</module></modules></project>");
        write(root, "adapter/api/pom.xml", "<project><artifactId>api</artifactId></project>");
        write(root, "adapter/api/src/main/java/com/hundsun/api/ApiApp.java",
                "package com.hundsun.api; public class ApiApp { public static void main(String[] a){} }");
        write(root, "adapter/client-api/pom.xml", "<project><artifactId>client-api</artifactId></project>");
        write(root, "adapter/client-api/src/main/resources/application.yml", "server:\n  port: 8080");
        // 嵌套聚合 banks/ext-fxbank：banks 聚合引用 ext-fxbank；ext-fxbank 聚合引用 fxbank-adapter-api
        write(root, "banks/pom.xml",
                "<project><packaging>pom</packaging><modules><module>ext-fxbank</module></modules></project>");
        write(root, "banks/ext-fxbank/pom.xml",
                "<project><packaging>pom</packaging><modules><module>fxbank-adapter-api</module></modules></project>");
        write(root, "banks/ext-fxbank/fxbank-adapter-api/pom.xml", "<project><artifactId>fxbank-adapter-api</artifactId></project>");
        write(root, "banks/ext-fxbank/fxbank-adapter-api/src/main/java/com/fxbank/OrderController.java",
                "package com.fxbank; public class OrderController {}");
        // 孤立叶子模块（无任何父聚合引用）→ 独立项目
        write(root, "standalone/pom.xml", "<project><artifactId>standalone</artifactId></project>");
        write(root, "standalone/src/main/java/com/x/StandaloneApp.java",
                "package com.x; public class StandaloneApp { public static void main(String[] a){} }");
        // 前端工程
        write(root, "frontend/package.json", "{\"name\":\"bemp-ui\",\"dependencies\":{\"vue\":\"^3\"}}");
        // node_modules 噪音：不应被识别为项目
        write(root, "frontend/node_modules/left-pad/package.json", "{\"name\":\"left-pad\"}");
        write(root, "frontend/node_modules/vue/package.json", "{\"name\":\"vue\"}");

        ProjectIndex idx = ProjectIndexer.scan(root);
        List<ProjectIndex.ProjectEntry> ps = idx.getProjects();
        List<String> relPaths = ps.stream().map(ProjectIndex.ProjectEntry::getRelPath).toList();

        Asserts.assertTrue("应识别聚合工程 adapter", relPaths.contains("adapter"));
        Asserts.assertTrue("应识别聚合工程 banks", relPaths.contains("banks"));
        Asserts.assertTrue("应识别嵌套聚合 banks/ext-fxbank（自身为项目）", relPaths.contains("banks/ext-fxbank"));
        Asserts.assertTrue("应识别孤立模块 standalone", relPaths.contains("standalone"));
        Asserts.assertTrue("应识别前端工程 frontend", relPaths.contains("frontend"));
        Asserts.assertFalse("node_modules 内的 package.json 不应产生项目",
                relPaths.stream().anyMatch(p -> p.contains("node_modules")));

        // adapter 聚合：modules 含 api/client-api，且 api 不独立成项目
        ProjectContext adapterCtx = ctxOf(ps, "adapter");
        Asserts.assertTrue("adapter 应含模块 api", adapterCtx.getModules().contains("api"));
        Asserts.assertTrue("adapter 应含模块 client-api", adapterCtx.getModules().contains("client-api"));
        Asserts.assertFalse("api 叶子不应独立成项目", relPaths.contains("adapter/api"));

        // locate：文件路径反查所属项目（最长前缀匹配）
        ProjectContext hit = idx.locate("banks/ext-fxbank/fxbank-adapter-api/src/main/java/com/fxbank/OrderController.java");
        Asserts.assertNotNull("应命中 banks/ext-fxbank 项目", hit);
        Asserts.assertTrue("命中的应是最深聚合 ext-fxbank",
                hit.getRootPath().endsWith("banks" + java.io.File.separator + "ext-fxbank"));
        ProjectContext hit2 = idx.locate("standalone/src/main/java/com/x/StandaloneApp.java");
        Asserts.assertNotNull("应命中 standalone", hit2);
        Asserts.assertTrue("standalone 命中应带构建系统 Maven", "Maven".equals(hit2.getBuildSystem()));
        ProjectContext hit3 = idx.locate("adapter/api/src/main/java/com/hundsun/api/ApiApp.java");
        Asserts.assertNotNull("应命中 adapter（api 从属于 adapter）", hit3);
        Asserts.assertTrue("adapter 命中应带 Maven", "Maven".equals(hit3.getBuildSystem()));

        // 渲染：多项目 prompt 章节含项目数与路径
        String prompt = idx.toPromptSection(8000);
        Asserts.assertContains("渲染应含项目数", prompt, "个项目");
        Asserts.assertContains("渲染应含 adapter", prompt, "adapter");
        Asserts.assertContains("渲染应含 ext-fxbank", prompt, "ext-fxbank");
    }

    public void testScan_singleProjectFallback() throws IOException {
        Path root = Files.createTempDirectory("bdidx");
        Files.createDirectories(root);
        write(root, "readme.txt", "no build files here");

        ProjectIndex idx = ProjectIndexer.scan(root);
        Asserts.assertEquals("无构建文件应兜底单项目", 1, idx.size());
        Asserts.assertEquals("兜底项目 relPath 应为 .", ".", idx.getProjects().get(0).getRelPath());
        Asserts.assertEquals("兜底构建系统应为 none", "none", idx.getProjects().get(0).getCtx().getBuildSystem());
    }

    public void testScan_deploymentFallback() throws IOException {
        Path root = Files.createTempDirectory("bdidx");
        Files.createDirectories(root);
        write(root, "WEB-INF/classes/com/x/A.class", "a");
        write(root, "WEB-INF/lib/spring.jar", "x");
        write(root, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");

        ProjectIndex idx = ProjectIndexer.scan(root);
        Asserts.assertEquals("部署产物应兜底单项目", 1, idx.size());
        ProjectContext c = idx.getProjects().get(0).getCtx();
        Asserts.assertEquals("应识别为 WAR 解包", "Java Web 部署包（WAR 解包）", c.getBuildSystem());
        Asserts.assertTrue("应提取 lib 依赖", c.getDependencies().contains("spring"));
    }

    public void testScan_invalidRoot() {
        ProjectIndex idx = ProjectIndexer.scan(java.nio.file.Paths.get("C:/__not_exist__/xyz"));
        Asserts.assertTrue("无效根应返回空索引", idx.isEmpty());
    }

    private static ProjectContext ctxOf(List<ProjectIndex.ProjectEntry> ps, String relPath) {
        for (ProjectIndex.ProjectEntry e : ps) {
            if (e.getRelPath().equals(relPath)) return e.getCtx();
        }
        return null;
    }
}
