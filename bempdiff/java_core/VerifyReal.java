import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.ArchiveTree;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.FolderParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 临时验证：用用户真实 M059/M061 文件夹做端到端对比，核对内部条目状态。用完即删。 */
public class VerifyReal {
    static Map<String, Long> crcMap(Path zip) throws Exception {
        Map<String, Long> m = new TreeMap<>();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                m.put(e.getName(), e.getCrc());
            }
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        Path left = Paths.get("C:\\Users\\hspcadmin\\Desktop\\testC\\BEMP5.0V202301-02-036M059(20260703-1104)");
        Path right = Paths.get("C:\\Users\\hspcadmin\\Desktop\\testC\\BEMP5.0V202301-02-036M061(20260707-1135)");
        String aName = "BEMP5.0-adapterV202301-02-036M059(20260703-1104).zip";
        String bName = "BEMP5.0-adapterV202301-02-036M061(20260707-1135).zip";

        FolderParser fp = new FolderParser();
        ParseConfig cfg = new ParseConfig();
        PackageSnapshot oldSnap = fp.parse(left, cfg);
        PackageSnapshot newSnap = fp.parse(right, cfg);

        System.out.println("[old] entries=" + oldSnap.getEntries().keySet());
        System.out.println("[new] entries=" + newSnap.getEntries().keySet());

        // 以 M059 为 key 展开（对侧应自动配对 M061）
        List<Map<String, Object>> kids = ArchiveTree.computeChildren(oldSnap, newSnap, aName);
        Map<String, String> statuses = new TreeMap<>();
        for (Map<String, Object> c : kids) {
            statuses.put((String) c.get("name"), (String) c.get("status"));
        }
        Map<String, Integer> count = new LinkedHashMap<>();
        for (String s : statuses.values()) count.merge(s, 1, Integer::sum);
        System.out.println("M059 展开 -> status counts = " + count);

        // 直接比较两侧 zip 内每个条目的 CRC，作为内容一致性的基准
        Map<String, Long> crcA = crcMap(Paths.get(left.toString(), aName));
        Map<String, Long> crcB = crcMap(Paths.get(right.toString(), bName));

        int ok = 0, bad = 0;
        for (Map.Entry<String, String> e : statuses.entrySet()) {
            String n = e.getKey();
            Long ca = crcA.get(n), cb = crcB.get(n);
            boolean same = ca != null && cb != null && ca.equals(cb);
            boolean expectSame = e.getValue().equals("UNCHANGED");
            boolean mismatch = expectSame != same || (ca == null) != (cb == null) == false;
            // 判定：UNCHANGED 必须对应两侧 CRC 相同；MODIFIED 必须对应两侧 CRC 不同且都存在
            boolean correct;
            if (e.getValue().equals("UNCHANGED")) correct = same;
            else if (e.getValue().equals("MODIFIED")) correct = (ca != null && cb != null && !same);
            else correct = (ca == null || cb == null);
            if (correct) ok++; else { bad++; mismatch = true; }
            if (mismatch) System.out.println("  !! " + n + " 实际=" + e.getValue() + " crcA=" + ca + " crcB=" + cb);
        }
        System.out.println("交叉核对: 正确=" + ok + " 错误=" + bad);
    }
}
