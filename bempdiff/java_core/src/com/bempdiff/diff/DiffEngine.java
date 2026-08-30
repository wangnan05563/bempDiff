package com.bempdiff.diff;

import com.bempdiff.model.FileClass;
import com.bempdiff.model.Layer;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 差异计算（T05/T06，FR3/FR4）。对应 prototype: compute_diff / compute_stats。
 *  - 全量差异：key 集合对称差 + sha256 比对（MODIFIED 以 sha 不等为准，非 size）
 *  - 删除类：仅老包有 -> DELETED（评审点#5，后续单列为破坏性变更）
 *  - 非文本边界：STATIC/OTHER 仍按 sha 判定 MODIFIED，但内容 diff 交由上层跳过（FR4.8）
 */
public final class DiffEngine {

    public DiffResult compute(PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        Map<String, LogicalEntry> old = oldSnap.getEntries();
        Map<String, LogicalEntry> now = newSnap.getEntries();
        DiffResult r = new DiffResult(old, now);
        Set<String> keys = new TreeSet<>();
        keys.addAll(old.keySet());
        keys.addAll(now.keySet());
        for (String k : keys) {
            LogicalEntry o = old.get(k);
            LogicalEntry n = now.get(k);
            if (o == null && n == null) {
                r.put(DiffStatus.UNCHANGED, k);
            } else if (o == null) {
                r.put(DiffStatus.ADDED, k);
            } else if (n == null) {
                r.put(DiffStatus.DELETED, k);
            } else if (!o.getSha256().equals(n.getSha256())) {
                // 文本类文件仅「行尾/行尾空白/BOM」等格式噪声差异（原始字节 sha 不同）→ 视为无差异，
                // 避免这类 .sh/.properties 等被误标 MODIFIED 而进入导出/破坏性/统计（与 diff 视图行尾中性口径一致）。
                // 仅在 sha 不同时才读内容规范化比较，缩小 IO；超限/读取失败回退 sha 判定。
                boolean sameText = textuallySameEntries(oldSnap, newSnap, o, n);
                r.put(sameText ? DiffStatus.UNCHANGED : DiffStatus.MODIFIED, k);
            } else {
                r.put(DiffStatus.UNCHANGED, k);
            }
        }
        // 同名不同版本/整包版本化（根目录改名→全量键不相等）：归档做版本配对，其余按内容级跨版本对齐。
        // 使统计/AI/导出/报告与树上口径一致，消除「版本化改名导致大量无差异内容被当作新增/删除」的假阳。
        alignVersionRenamedArchives(r, old, now);
        alignIdenticalContentAcrossRename(r, oldSnap, newSnap);
        return r;
    }

    /** 文本规范化比较的大小上限：超过则不做内容读入（回退 sha），避免大文本在 diff 阶段高 IO。 */
    private static final long NORM_MAX_BYTES = 2L * 1024 * 1024;

    /**
     * 判断两个同键文本条目的「规范化内容」是否一致（忽略 BOM / 行尾 CRLF·CR / 每行行尾空白）。
     * 仅当文件属文本可 diff（CONFIG 等以字符串比较）且大小可控时读取规范化比较；
     * 非文本 / 超限 / 读取失败回退按 sha 判定（返回 false → MODIFIED）。
     */
    private static boolean textuallySameEntries(PackageSnapshot os, PackageSnapshot ns, LogicalEntry o, LogicalEntry n) {
        if (o == null || n == null) return false;
        FileClass fc = o.getFileClass();
        if (fc == null || !fc.isTextDiffable()) return false;
        if (o.getSize() > NORM_MAX_BYTES || n.getSize() > NORM_MAX_BYTES) return false;
        byte[] a = readEntryBytesSafe(os, o);
        byte[] b = readEntryBytesSafe(ns, n);
        if (a == null || b == null) return false;
        return normalizeText(a).equals(normalizeText(b));
    }

    private static byte[] readEntryBytesSafe(PackageSnapshot snap, LogicalEntry e) {
        try {
            return new PackageParser().readEntryBytes(snap, e);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 规范化文本内容：去 UTF-8 BOM、统一换行（\r\n/\r → \n）、去每行行尾空白。 */
    static String normalizeText(byte[] data) {
        String s = new String(data == null ? new byte[0] : data, StandardCharsets.UTF_8);
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        String[] lines = s.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            int e = ln.length();
            while (e > 0 && (ln.charAt(e - 1) == ' ' || ln.charAt(e - 1) == '\t')) e--;
            if (i > 0) sb.append('\n');
            sb.append(ln, 0, e);
        }
        return sb.toString();
    }

    /**
     * 对「同名不同版本」归档容器做配对对齐：
     *   - 旧侧 DELETED 归档在 ADDED 侧有「唯一」的同名不同版本候选才配对（不要求父目录相等，
     *     因为整包交付件的根目录也可能随版本改名，如 …M059/…M061）；
     *   - 配对后容器按 sha 判 MODIFIED/UNCHANGED；
     *   - 其扁平化内部子文件（outer!/sub）按同版本键对齐逐一比对（内容一致 → UNCHANGED）。
     *
     * <p>性能：整包版本改名后扁平化条目可达几十万，若对每个 DELETED 归档全量扫描 AADD 列表并把结果
     * 逐个从 List 上 remove（O(N)），整体退化为 O(N²)。故这里用 Set 跟踪剩余、归档候选先预过滤，
     * 内部子文件按归档前缀一次收集，最终一次性重建 DELETED/ADDED 列表。</p>
     */
    private static void alignVersionRenamedArchives(DiffResult r, Map<String, LogicalEntry> old, Map<String, LogicalEntry> now) {
        LinkedHashSet<String> deleted = new LinkedHashSet<>(r.get(DiffStatus.DELETED));
        LinkedHashSet<String> added = new LinkedHashSet<>(r.get(DiffStatus.ADDED));
        if (deleted.isEmpty() || added.isEmpty()) return;
        // 归档候选预过滤：候选配对只需扫描归档条目，避免对几十万扁平化 class 全遍历
        List<String> archiveAdded = new ArrayList<>();
        for (String a : added) {
            LogicalEntry ae = now.get(a);
            if (ae != null && isArchive(ae)) archiveAdded.add(a);
        }
        for (String dKey : new ArrayList<>(deleted)) {
            if (!deleted.contains(dKey)) continue;
            LogicalEntry de = old.get(dKey);
            if (de == null || !isArchive(de)) continue;
            String aKey = versionCounterpart(dKey, archiveAdded, old, now, added);
            if (aKey == null) continue;
            added.remove(aKey);
            deleted.remove(dKey);
            LogicalEntry ae = now.get(aKey);
            boolean sameContainer = ae != null && de.getSha256().equals(ae.getSha256());
            r.put(sameContainer ? DiffStatus.UNCHANGED : DiffStatus.MODIFIED, dKey);
            // 扁平化内部子文件对齐（同版本键对齐后逐一比对 sha）。子文件跨层多，先按归档前缀一次收集，
            // 避免为每个 DELETED 归档重复全量遍历整个 deleted 集合。
            String preO = dKey + "!/";
            String preN = aKey + "!/";
            List<String> subUnderO = new ArrayList<>();
            for (String dSub : deleted) if (dSub.startsWith(preO)) subUnderO.add(dSub);
            for (String dSub : subUnderO) {
                if (!deleted.contains(dSub)) continue;
                String nKey = preN + dSub.substring(preO.length());
                if (!added.contains(nKey)) continue;
                LogicalEntry oe2 = old.get(dSub);
                LogicalEntry ne2 = now.get(nKey);
                boolean same = oe2 != null && ne2 != null && oe2.getSha256().equals(ne2.getSha256());
                deleted.remove(dSub);
                added.remove(nKey);
                r.put(same ? DiffStatus.UNCHANGED : DiffStatus.MODIFIED, dSub);
            }
        }
        r.replaceList(DiffStatus.DELETED, deleted);
        r.replaceList(DiffStatus.ADDED, added);
    }

    private static boolean isArchive(LogicalEntry e) {
        FileClass fc = e.getFileClass();
        return fc == FileClass.ARCHIVE || fc == FileClass.JAR;
    }

    /**
     * 在 ADDED 归档中找旧侧归档的「同名不同版本」唯一候选。
     * 放宽父目录（版本化根目录会改名），仅以「叶子版本对 + 全局唯一性」配对避免跨目录误配：
     * 命中候选必须恰好一个，否则放弃（保守，宁可不配也不误配）。
     * {@code remainingAdded}：仍在集合中的 ADDED 归档（用于跳过已被配对/移除的候选）。
     */
    private static String versionCounterpart(String dKey, List<String> added, Map<String, LogicalEntry> old,
                                             Map<String, LogicalEntry> now, Set<String> remainingAdded) {
        String cand = null;
        int matches = 0;
        for (String aKey : added) {
            if (!remainingAdded.contains(aKey)) continue;
            LogicalEntry ae = now.get(aKey);
            if (ae == null || !isArchive(ae)) continue;
            if (com.bempdiff.parse.PackageVersion.sameBaseDifferentVersion(fileName(dKey), fileName(aKey))) {
                cand = aKey;
                matches++;
            }
        }
        return (matches == 1) ? cand : null;
    }

    /**
     * 内容级跨版本对齐：仅对「版本等价路径」+ 内容一致的 ADDED/DELETED 配对为 UNCHANGED。
     *
     * <p>整包交付件（根目录/内层归档随版本改名，如 …M059/…M061）会导致全量键不相等，
     * 大部分文件本无变化却被计为新增/删除。这里只折叠「各路径段逐段版本等价（或相等）、且内容一致」
     * 的同逻辑文件（如 …M059/acctrecord.xml 与 …M061/acctrecord.xml）；
     * 不折叠普通目录/文件改名（如 dirA/→dirB/，非版本形态），保持既有“改名=删除+新增”的口径。
     *
     * <p>内容一致性采用「构建噪声归一化」判定（而非仅 sha 相同）：版本化整包重新打包时，
     * 嵌套 jar/war 内部的构建元数据（Maven MANIFEST.MF 的 Timestamp、pom.properties 的日期注释、
     * MANIFEST.xml 部署清单的 md5/size/版本路径、deploy.xml/version.properties 的版本号行）仅随构建
     * 而变，业务内容相同——这些差异应折叠为未变，消除「二层嵌套 jar/war 内部文件被误识别为新增/删除」的假阳
     * （用户实测 BEMP5.0V…M059 vs …M061）。真实内容差异（如前端 chunk 的 watch/校验逻辑改动）不折叠。
     *
     * <p>保守约束：仅当配对是 1:1（同一内容键内恰好一个版本等价候选）才折叠，避免误配。
     *
     * <p>性能：扁平化后 key 可达几十万，若对每个 DELETED 全量遍历所有 ADDED，整体 O(N²) 而卡死。
     * 这里先按「内容键」（sha 或构建噪声归一化文本的 sha）对 ADDED 建索引——只有内容一致的条目
     * 才可能是版本改名的同一逻辑文件，只在同内容键的候选桶内做逐段版本等价判定，复杂度降为近 O(N)。</p>
     */
    private static void alignIdenticalContentAcrossRename(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        Map<String, LogicalEntry> old = oldSnap.getEntries();
        Map<String, LogicalEntry> now = newSnap.getEntries();
        LinkedHashSet<String> deleted = new LinkedHashSet<>(r.get(DiffStatus.DELETED));
        LinkedHashSet<String> added = new LinkedHashSet<>(r.get(DiffStatus.ADDED));
        if (deleted.isEmpty() || added.isEmpty()) return;
        Map<String, List<String>> addedByKey = new HashMap<>();
        for (String a : r.get(DiffStatus.ADDED)) {
            LogicalEntry ae = now.get(a);
            if (ae != null) addedByKey.computeIfAbsent(contentAlignKey(newSnap, ae), x -> new ArrayList<>()).add(a);
        }
        for (String d : new ArrayList<>(deleted)) {
            if (!deleted.contains(d)) continue;
            LogicalEntry de = old.get(d);
            if (de == null) continue;
            List<String> candidates = addedByKey.get(contentAlignKey(oldSnap, de));
            if (candidates == null) continue;
            String pick = null;
            int matches = 0;
            for (String a : candidates) {
                if (!added.contains(a)) continue;
                LogicalEntry ae = now.get(a);
                if (ae == null) continue;
                if (versionEquivalentPath(d, a)) { pick = a; matches++; }
            }
            if (matches != 1 || pick == null) continue;
            deleted.remove(d);
            added.remove(pick);
            r.put(DiffStatus.UNCHANGED, d);
        }
        r.replaceList(DiffStatus.DELETED, deleted);
        r.replaceList(DiffStatus.ADDED, added);
    }

    /**
     * 版本对齐用的「内容键」：同内容键的条目才可能互为版本改名候选（用于近 O(N) 剪枝）。
     *  - 非文本 / 超大文本 / 读取失败：回退原始 sha（无构建噪声概念，按字节一致折叠）；
     *  - 文本小文件：用「构建噪声归一化」后的文本 sha——时间戳/日期注释/版本号行差异被抹平后
     *    内容一致 → 键相同 → 进入候选桶（随后经 {@link #versionEquivalentPath} + 1:1 校验折叠）。
     *  - MANIFEST.xml 部署清单：额外抹平 md5/size 属性（随构建全变）。
     */
    private static String contentAlignKey(PackageSnapshot snap, LogicalEntry e) {
        FileClass fc = e.getFileClass();
        if (fc == null || !fc.isTextDiffable()) return e.getSha256();
        if (e.getSize() > NORM_MAX_BYTES) return e.getSha256();
        byte[] bytes = readEntryBytesSafe(snap, e);
        if (bytes == null) return e.getSha256();
        String norm = isDeployManifestKey(e.getKey())
                ? noiseNormalizeDeploy(bytes)
                : noiseNormalizeText(bytes);
        return sha256Hex(norm.getBytes(StandardCharsets.UTF_8));
    }

    /** 是否为部署清单（MANIFEST.xml）：其 md5/size/版本路径全部随构建变化，视为整体构建元数据。 */
    private static boolean isDeployManifestKey(String key) {
        return key != null && key.endsWith("/META-INF/MANIFEST.xml");
    }

    /** 通用构建噪声归一化：去 BOM/统一换行/去行尾空白 + 跳过构建时间戳行 + 抹平版本号 token。 */
    static String noiseNormalizeText(byte[] data) {
        String s = normalizeText(data);
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (String line : s.split("\n", -1)) {
            if (isBuildNoiseLine(line)) continue;
            sb.append(stripVersionTokens(line)).append('\n');
        }
        return sb.toString();
    }

    /** 部署清单归一化：在通用噪声归一化基础上，抹平 md5/size 属性（随构建全变）。 */
    static String noiseNormalizeDeploy(byte[] data) {
        String s = noiseNormalizeText(data);
        s = s.replaceAll("(?i)md5\\s*=\\s*\"[0-9a-fA-F]+\"", "md5=\"*\"");
        s = s.replaceAll("(?i)size\\s*=\\s*\"[0-9]+\"", "size=\"*\"");
        return s;
    }

    /** 构建时间戳/日期注释行：Maven MANIFEST 的 Timestamp 行、pom.properties 的日期注释行。 */
    private static boolean isBuildNoiseLine(String line) {
        if (line.startsWith("Timestamp:")) return true;          // Manifest-Version 的构建时间戳
        String t = line.trim();
        if (t.startsWith("#")) {
            // Maven properties 自动生成的日期注释：#Fri Jul 03 11:06:08 CST 2026
            String rest = t.substring(1).trim();
            return rest.matches("(?i)(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\s+[A-Za-z]+\\s+\\d+.*\\d{4}");
        }
        return false;
    }

    /**
     * 抹平行内的「构建号式版本 token」（BEMP 风格 M 补丁号[.点分补丁]，可带括号/点分时间戳），
     * 如 036M059(20260703-1104)、036M059.20260703.1104、20230102036M.15。仅抹平版本形态子串，
     * 纯数字配置（port=8080 等）不匹配，避免把真实配置差异误判为版本噪声。
     */
    static String stripVersionTokens(String line) {
        // 1) 构建号[.补丁]+括号时间戳：036M059(20260703.1104) / 036M059(20260703-1104)
        line = line.replaceAll("[0-9]+M[0-9]*(?:\\.\\d+)?\\([0-9][0-9.\\-]*\\)", "\u0000V\u0000");
        // 2) 构建号[.补丁]+点分时间戳：036M059.20260703.1104 / 20230102036M.15.20260703.1104
        line = line.replaceAll("[0-9]+M[0-9]*(?:\\.\\d+)?\\.[0-9]{8}\\.[0-9]+", "\u0000V\u0000");
        // 3) 独立构建号[.补丁]：036M059 / 20230102036M.15
        line = line.replaceAll("[0-9]+M[0-9]*(?:\\.\\d+)?", "\u0000V\u0000");
        return line;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 两条键逐段“版本等价”：切分同数段，且每一段相等或是版本等价（尾段含数字、公共前缀≥4）。 */
    private static boolean versionEquivalentPath(String a, String b) {
        List<String> sa = splitKey(a);
        List<String> sb = splitKey(b);
        if (sa.size() != sb.size()) return false;
        for (int i = 0; i < sa.size(); i++) {
            if (!versionEquivalentSegment(sa.get(i), sb.get(i))) return false;
        }
        return true;
    }

    /** 段级版本等价：完全相同，或「公共前缀≥4 且剩余尾段均形似版本（含数字/合法字符）且不同」。 */
    private static boolean versionEquivalentSegment(String a, String b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        String lcp = longestCommonPrefix(a, b);
        if (lcp.length() < 4) return false;
        String ra = a.substring(lcp.length());
        String rb = b.substring(lcp.length());
        if (ra.isEmpty() || rb.isEmpty()) return false;
        // 版本差异的判据：两段在 lcp 后应先进入数字（版本号核心），而不是先出现" ≥2 个字母的单词"。
        // 真实版本号在 lcp 后紧跟数字或短版本标记（如 …M0→59/61、-rc→1/2、1.6→2.0），其数字前的字母段
        // 要么为空、要么不足 2 个（被 lcp 吸收）。产品名/词缀段（web/adapter、logo-login 的 login）在
        // 数字前自带一个 ≥2 字母的单词，属于「不同名字而非版本增量」，应判非等价 → 按删除+新增处理。
        if (lettersBeforeFirstDigit(ra) >= 2 || lettersBeforeFirstDigit(rb) >= 2) return false;
        return looksVersionTail(ra) && looksVersionTail(rb);
    }

    /** 统计字符串在首个数字之前出现的字母个数（跳过标点但不清零，用于识别"数字前是否夹带单词"）。 */
    private static int lettersBeforeFirstDigit(String s) {
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) break;
            if (Character.isLetter(c)) letters++;
        }
        return letters;
    }

    /** 把键按 '/' 与 '!/' 边界切成逻辑段（保留含 ! 的归档段，便于段级版本比较）。 */
    private static List<String> splitKey(String key) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String k = key.replace('\\', '/');
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == '/' || (c == '!' && i + 1 < k.length() && k.charAt(i + 1) == '/')) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                if (c == '!') i++; // 跳过 '!'
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String longestCommonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return a.substring(0, i);
    }

    /** 尾段形似版本：含数字且只由 数字/字母/括号/._- 组成（与 PackageVersion 同口径；其方法为包私有，此处本地实现）。 */
    private static boolean looksVersionTail(String t) {
        if (t == null || t.isEmpty()) return false;
        boolean hasDigit = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) { hasDigit = true; continue; }
            if (Character.isLetter(c)) continue;
            if (c == '(' || c == ')' || c == '.' || c == '_' || c == '-') continue;
            return false;
        }
        return hasDigit;
    }

    private static String fileName(String key) {
        int i = key.lastIndexOf('/');
        if (i >= 0) return key.substring(i + 1);
        int b = key.lastIndexOf('\\');
        return (b < 0) ? key : key.substring(b + 1);
    }

    public DiffStats stats(DiffResult r) {
        DiffStats s = new DiffStats();
        s.setAdded(r.get(DiffStatus.ADDED).size());
        s.setDeleted(r.get(DiffStatus.DELETED).size());
        s.setModified(r.get(DiffStatus.MODIFIED).size());
        s.setUnchanged(r.get(DiffStatus.UNCHANGED).size());
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                if (k.endsWith(".jar") && k.contains("/lib/")) {
                    s.setJarChanged(s.getJarChanged() + 1);
                } else {
                    s.setBizChanged(s.getBizChanged() + 1);
                }
            }
        }
        return s;
    }

    /**
     * 收集「L1 业务 class 候选」（MODIFIED/ADDED/DELETED 中、层级为 L1 的 class）。
     * 单一事实来源：消除 compare/decompile/report/export/ai 以及 UI 中重复 4+ 遍的同形逻辑。
     * 返回全量候选（不截断），调用方按需 subList / Math.min。
     */
    @SuppressWarnings("unused")
    public static List<String> collectL1ClassCandidates(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        List<String> cands = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                LogicalEntry oe = oldSnap.getEntries().get(k);
                LogicalEntry ne = newSnap.getEntries().get(k);
                boolean isClass = (oe != null && oe.getFileClass() == FileClass.CLASS && oe.getLayer() == Layer.L1)
                        || (ne != null && ne.getFileClass() == FileClass.CLASS && ne.getLayer() == Layer.L1);
                if (isClass) cands.add(k);
            }
        }
        return cands;
    }

    /**
     * 收集「前端源码文本候选」（MODIFIED/ADDED/DELETED 中、分类为 JS/HTML/CSS 的条目）。
     * 这些文件需做美化后内容 diff 与 AI 分析（FR4.4 增强）。返回全量候选，调用方按需截断。
     */
    public static List<String> collectFrontendTextCandidates(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        List<String> cands = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                LogicalEntry oe = oldSnap.getEntries().get(k);
                LogicalEntry ne = newSnap.getEntries().get(k);
                boolean isFrontend = (oe != null && oe.getFileClass().isFrontendText())
                        || (ne != null && ne.getFileClass().isFrontendText());
                if (isFrontend) cands.add(k);
            }
        }
        return cands;
    }

    /**
     * 收集「可内容 diff 的文本资源候选」（MODIFIED/ADDED/DELETED 中、分类为
     * CONFIG/JSP/JS/HTML/CSS 的条目）。本需求扩展：在原有前端文本基础上纳入
     * XML/Properties 等配置文件与 JSP 页面，统一做内容级逐行 diff。
     * 返回全量候选，调用方按需截断（Top-K）。
     */
    public static List<String> collectTextDiffCandidates(DiffResult r, PackageSnapshot oldSnap, PackageSnapshot newSnap) {
        List<String> cands = new ArrayList<>();
        for (DiffStatus st : new DiffStatus[]{DiffStatus.MODIFIED, DiffStatus.ADDED, DiffStatus.DELETED}) {
            for (String k : r.get(st)) {
                LogicalEntry oe = oldSnap.getEntries().get(k);
                LogicalEntry ne = newSnap.getEntries().get(k);
                boolean isText = (oe != null && oe.getFileClass().isTextDiffable())
                        || (ne != null && ne.getFileClass().isTextDiffable());
                if (isText) cands.add(k);
            }
        }
        return cands;
    }
}