package com.bempdiff.test;

import com.bempdiff.parse.PackageVersion;

/**
 * 包文件名智能版本识别/排序测试：同名不同版本自动配对（旧→新）。
 * 覆盖用户场景：BEMP5.0-adapterV202301-02-036M061(20260707-1135).zip vs
 * BEMP5.0-adapterV202301-02-036M059(20260703-1104).zip。
 */
public final class PackageVersionTest {

    private static final String F1 = "BEMP5.0-adapterV202301-02-036M061(20260707-1135).zip";
    private static final String F2 = "BEMP5.0-adapterV202301-02-036M059(20260703-1104).zip";

    public void testExtract_buildStyleTimestamp() {
        Asserts.assertEquals("构建号式应提取出完整版本段",
                "036M061(20260707-1135)", PackageVersion.extractFromFileName(F1));
        Asserts.assertEquals("另一个版本段", "036M059(20260703-1104)", PackageVersion.extractFromFileName(F2));
    }

    public void testExtract_dottedAndVariant() {
        Asserts.assertEquals("点分式", "1.6.1", PackageVersion.extractFromFileName("app-1.6.1.war"));
        Asserts.assertEquals("v 前缀+后缀", "2.0.1-SNAPSHOT", PackageVersion.extractFromFileName("app-v2.0.1-SNAPSHOT.jar"));
        Asserts.assertNull("无版本返回 null", PackageVersion.extractFromFileName("app.zip"));
    }

    public void testSameBaseDifferentVersion_true() {
        Asserts.assertTrue("BEMP 同基名不同版本应识别", PackageVersion.sameBaseDifferentVersion(F1, F2));
        Asserts.assertTrue("点分式不同版本应识别",
                PackageVersion.sameBaseDifferentVersion("app-1.6.1.war", "app-1.6.2.war"));
    }

    public void testSameBaseDifferentVersion_false() {
        Asserts.assertFalse("同名同文件不算", PackageVersion.sameBaseDifferentVersion(F1, F1));
        Asserts.assertFalse("完全无关不算", PackageVersion.sameBaseDifferentVersion(F1, "other-package.zip"));
        Asserts.assertFalse("目录 vs 文件不算", PackageVersion.sameBaseDifferentVersion(F1, "BEMP5.0-adapterV202301-02"));
        Asserts.assertFalse("公共前缀过短不算",
                PackageVersion.sameBaseDifferentVersion("a1.zip", "a2.zip"));
    }

    public void testCompare_buildStyleOrder() {
        // M061 > M059：F1 更新
        Asserts.assertTrue("F1 应新于 F2", PackageVersion.compare(F1, F2) > 0);
        Asserts.assertTrue("F2 应旧于 F1", PackageVersion.compare(F2, F1) < 0);
    }

    public void testCompare_dottedAndTimestamp() {
        Asserts.assertTrue("1.6.2 新于 1.6.1", PackageVersion.compare("1.6.2", "1.6.1") > 0);
        Asserts.assertTrue("同版本相等", PackageVersion.compare("036M061(20260707-1135)", "036M061(20260707-1135)") == 0);
        // 时间戳决胜：编号相同但时间更新
        Asserts.assertTrue("时间戳更新者新",
                PackageVersion.compare("036M061(20260708-0900)", "036M061(20260707-1135)") > 0);
    }

    public void testOrderOldNew_autoSort() {
        // 用户场景：先传新版本（F1）也能自动排成 旧 F2 → 新 F1
        String[] ordered = PackageVersion.orderOldNew(F1, F2);
        Asserts.assertEquals("旧侧应为较低版本", F2, ordered[0]);
        Asserts.assertEquals("新侧应为较高版本", F1, ordered[1]);
        // 已是 旧→新 顺序则保持
        String[] kept = PackageVersion.orderOldNew(F2, F1);
        Asserts.assertEquals("保持旧→新", F2, kept[0]);
        Asserts.assertEquals("保持旧→新", F1, kept[1]);
        // 无关文件保持原顺序
        String[] other = PackageVersion.orderOldNew(F1, "unrelated.zip");
        Asserts.assertEquals("无关保持原顺序", F1, other[0]);
        Asserts.assertEquals("无关保持原顺序", "unrelated.zip", other[1]);
    }
}
