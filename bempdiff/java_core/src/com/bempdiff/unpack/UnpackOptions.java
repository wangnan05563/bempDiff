package com.bempdiff.unpack;

/**
 * 解包配置（外部可配，不写死）：线程池大小、单文件超时、最大递归深度与累计解压字节上限。
 * 由 BempServer 从 CompareOptions 派生，传入 NestedUnpacker。
 */
public final class UnpackOptions {
    /** 并发解包线程数（LAN 磁盘 I/O 密集，默认 4，可按机器核数上调）。 */
    public int threadPoolSize = 4; // NOSONAR S1104 - 配置 DTO 对外暴露的可变字段；测试(UnpackTest/FlakyProbe/DiagWar)与 NestedUnpacker 均直接读写，封装会导致调用点重大改动
    /** 单个根容器解包的平摊预算（毫秒）：屏障按「预算×root 数」作整体 deadline，单个 root 可占用剩余预算，
     *  不被单独截断。设 60s 为深层 webapp WAR（展开上百子文件）留足余量；totalBytesCap/maxDepth 仍防病态放大。 */
    public long perItemTimeoutMs = 60_000; // NOSONAR S1104 - 配置 DTO 对外暴露的可变字段；测试(UnpackTest/DiagWar/E2eVerify)与 NestedUnpacker 均直接读写，封装会导致调用点重大改动
    /** 嵌套归档最大递归深度，防止病态深层 zip 无限下钻。 */
    public int maxDepth = 6; // NOSONAR S1104 - 配置 DTO 对外暴露的可变字段；测试(UnpackTest/DiagWar)与 NestedUnpacker 均直接读写，封装会导致调用点重大改动
    /** 临时目录累计解压字节总上限（防御 zip bomb 集群放大）。
     *  默认 4GB：本产品交付（多层 webapp WAR）展开后可达数百 MB，512MB 处于临界值会偶发提前截断
     *  （静默少展开、统计 flaky），故按稳定完整量级提升；仍为有上限的 zip-bomb 防御。 */
    public long totalBytesCap = 4L * 1024 * 1024 * 1024; // NOSONAR S1104 - 配置 DTO 对外暴露的可变字段；测试(UnpackTest/DiagWar/E2eVerify)与 NestedUnpacker 均直接读写，封装会导致调用点重大改动
    /** 比对级忽略扩展名（含点小写，如 .log/.mf）。扁平化展开时，命中该后缀的原子文件跳过不落入快照，
     *  与 PackageParser/FolderParser/ArchiveTree 的解析阶段过滤保持一致（防止 unpackNested 下嵌套内部漏出）。 */
    public java.util.List<String> ignoreExtensions = new java.util.ArrayList<>(); // NOSONAR S1104 - 配置 DTO 对外暴露的可变字段；测试(UnpackTest)与 NestedUnpacker 均直接读写，封装会导致调用点重大改动

    public UnpackOptions() {
        // 构造无需初始化：全部字段均在本类声明处带默认值（线程数/超时/深度/字节上限/忽略扩展名）。
    }
}