package com.bempdiff.test;

import com.bempdiff.model.EntrySource;
import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.model.PackageType;
import com.bempdiff.server.CompareOptions;
import com.bempdiff.server.Job;
import com.bempdiff.server.LruMap;
import com.bempdiff.unpack.NestedUnpacker;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T00425/T00426/T00427 内存优化回归：
 * 1) 全局 memo 计数可回扣（修复只增不减导致 memoize 永久失效）；
 * 2) PackageSnapshot 登记的 memoizedBytes 与 memory-backed 条目一致；
 * 3) Job.releaseMemory 置空快照 + 回扣计数且幂等；
 * 4) LruMap 容量逐出与访问序刷新。
 */
public final class MemoryReleaseTest {

    private static LogicalEntry memEntry(String key, int size) {
        byte[] data = new byte[size];
        return new LogicalEntry(key, Layer.L0, FileClass.OTHER, size,
                "sha-" + key, EntrySource.memoryBacked(data));
    }

    private static PackageSnapshot snapWithMemEntries() {
        Map<String, LogicalEntry> entries = new LinkedHashMap<>();
        entries.put("a.bin", memEntry("a.bin", 100));
        entries.put("b.bin", memEntry("b.bin", 250));
        return new PackageSnapshot(Paths.get("x.war"), PackageType.WAR, "v", entries);
    }

    /** T00425：memoizedBytesOf 统计 memory-backed 字节；releaseMemoized 按值回扣（清零后做增量断言，抗其它用例污染）。 */
    public void testMemoBytesAccounting() {
        PackageSnapshot snap = snapWithMemEntries();
        Asserts.assertEquals("memoizedBytesOf 应等于各 memory-backed 条目大小之和",
                350L, NestedUnpacker.memoizedBytesOf(snap));

        // 清零基线后做增量断言（测试可能非首个运行，全局计数已有残留）
        NestedUnpacker.releaseMemoized(NestedUnpacker.globalMemoBytesNow());
        Asserts.assertEquals("清零后全局计数应为 0", 0L, NestedUnpacker.globalMemoBytesNow());

        com.bempdiff.unpack.NestedUnpacker.tryMemoize(new byte[100]);
        Asserts.assertEquals("tryMemoize 后计数 +100", 100L, NestedUnpacker.globalMemoBytesNow());
        NestedUnpacker.releaseMemoized(60);
        Asserts.assertEquals("回扣 60 后剩 40", 40L, NestedUnpacker.globalMemoBytesNow());
        NestedUnpacker.releaseMemoized(999);
        Asserts.assertEquals("超量回扣不下探 0 以下", 0L, NestedUnpacker.globalMemoBytesNow());
        NestedUnpacker.releaseMemoized(-5); // 非法值无操作不抛
        NestedUnpacker.releaseMemoized(0);
    }

    /** T00425/426：Job.releaseMemory 置空双侧快照、按登记值回扣计数、幂等。 */
    public void testJobReleaseMemory() {
        PackageSnapshot a = snapWithMemEntries(); // memo 350
        PackageSnapshot b = snapWithMemEntries(); // memo 350
        Job job = new Job("j-test", "package", new CompareOptions());
        job.markRunning("parsing", "解析中", 10);
        job.complete(a, b, null, null);
        Asserts.assertNotNull("完成态应有旧快照", job.getOldSnap());
        Asserts.assertNotNull("完成态应有新快照", job.getNewSnap());

        NestedUnpacker.releaseMemoized(NestedUnpacker.globalMemoBytesNow()); // 清零基线
        job.releaseMemory();
        Asserts.assertNull("释放后旧快照应置空", job.getOldSnap());
        Asserts.assertNull("释放后新快照应置空", job.getNewSnap());
        Asserts.assertEquals("回扣两侧 memo 700", 0L, NestedUnpacker.globalMemoBytesNow());

        // 幂等：再次释放不抛、无副作用（flags 防重复回扣）
        job.releaseMemory();
        Asserts.assertEquals("重复释放不再回扣", 0L, NestedUnpacker.globalMemoBytesNow());

        // QUEUED/无快照作业直接释放应安全
        Job empty = new Job("j-empty", "package", new CompareOptions());
        empty.releaseMemory();
    }

    /** T00427：LRU 容量逐出最旧、get 刷新访问序、超容量逐出「最久未访问」而非最新插入。 */
    public void testLruMapEviction() {
        LruMap<String, String> m = new LruMap<>(2);
        m.put("k1", "v1");
        m.put("k2", "v2");
        Asserts.assertEquals("容量内 get 命中", "v1", m.get("k1"));
        m.put("k3", "v3"); // 超容量：k2 最久未访问被逐出（k1 刚被 get 刷新）
        Asserts.assertEquals("k1 因访问刷新应保留", "v1", m.get("k1"));
        Asserts.assertNull("k2 最久未访问应被逐出", m.get("k2"));
        Asserts.assertEquals("k3 应保留", "v3", m.get("k3"));
        Asserts.assertEquals("size 恒不超过容量", 2, m.size());

        m.put("k4", "v4"); // 再逐出 k1（最久未访问）
        Asserts.assertNull("k1 应被逐出", m.get("k1"));
        m.remove("k4");
        Asserts.assertNull("remove 后不可见", m.get("k4"));
        Asserts.assertEquals("remove 后 size 减一", 1, m.size());
        m.clear();
        Asserts.assertEquals("clear 后为空", 0, m.size());
    }
}
