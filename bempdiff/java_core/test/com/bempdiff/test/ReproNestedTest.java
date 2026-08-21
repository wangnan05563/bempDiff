package com.bempdiff.test;

import com.bempdiff.diff.ArchiveTree;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.config.ParseConfig;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复现：package 模式（直接比对两个 .zip）下，嵌套归档（zip/jar）无法正确展开并递归对比。
 * 关键现象：diff 树里顶层节点 key 是包内条目名（如 "lib/bundle.zip"），
 * 点击展开时 ArchiveTree.resolveTopArchive 把 outerEntry(="lib/bundle.zip") 当成磁盘路径返回，
 * 导致 ZipFile 打开失败 / 返回空，嵌套内容无法解包。
 */
public final class ReproNestedTest {

    /** 直接比对两个真实 .zip（package 模式），展开其中嵌套的 bundle.zip。 */
    public static void testPackageModeNestedUnpack() throws Exception {
        Map<String, byte[]> innerOld = new LinkedHashMap<>();
        innerOld.put("A.class", "a-v1".getBytes());
        innerOld.put("B.txt", "b-old".getBytes());
        Map<String, byte[]> innerNew = new LinkedHashMap<>();
        innerNew.put("A.class", "a-v2".getBytes()); // 修改
        innerNew.put("B.txt", "b-old".getBytes());   // 未变
        innerNew.put("C.txt", "c-new".getBytes());   // 新增

        // 外层 zip：包含嵌套的 lib/bundle.zip
        Map<String, byte[]> outerOld = new LinkedHashMap<>();
        outerOld.put("lib/bundle.zip", TestFixtures.makeZip(innerOld));
        outerOld.put("README.txt", "readme".getBytes());
        Map<String, byte[]> outerNew = new LinkedHashMap<>();
        outerNew.put("lib/bundle.zip", TestFixtures.makeZip(innerNew));
        outerNew.put("README.txt", "readme".getBytes());

        Path oldZip = TestFixtures.writePackage(TestFixtures.makeZip(outerOld), "outerOld");
        Path newZip = TestFixtures.writePackage(TestFixtures.makeZip(outerNew), "outerNew");

        PackageParser pp = new PackageParser();
        PackageSnapshot oldSnap = pp.parse(oldZip, new ParseConfig(), false);
        PackageSnapshot newSnap = pp.parse(newZip, new ParseConfig(), false);

        System.out.println("[复现] 旧侧快照条目: " + oldSnap.getEntries().keySet());

        // 注意：package 模式下顶层节点 key 就是包内条目名（无外层前缀）
        List<Map<String, Object>> kids = ArchiveTree.computeChildren(oldSnap, newSnap, "lib/bundle.zip");
        System.out.println("[复现] lib/bundle.zip 展开子节点数: " + kids.size());
        for (Map<String, Object> k : kids) {
            System.out.println("   - " + k.get("key") + "  [" + k.get("status") + "]  expandable=" + k.get("expandable"));
        }
        Asserts.assertTrue("嵌套 bundle.zip 应能展开出内部条目（当前实现很可能为空=bug）", kids.size() > 0);

        // 继续递归展开下一层（bundle.zip 内已是普通文件，这里验证 A.class 的 inner diff 可被读取）
        Map<String, Map<String, Object>> idx = new LinkedHashMap<>();
        for (Map<String, Object> k : kids) idx.put((String) k.get("key"), k);
        if (idx.containsKey("lib/bundle.zip!/A.class")) {
            System.out.println("[复现] 命中 lib/bundle.zip!/A.class，状态=" + idx.get("lib/bundle.zip!/A.class").get("status"));
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            testPackageModeNestedUnpack();
            System.out.println("复现用例通过 ✓");
        } catch (AssertionError | Exception e) {
            System.out.println("复现用例失败 ✗: " + e);
            e.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
