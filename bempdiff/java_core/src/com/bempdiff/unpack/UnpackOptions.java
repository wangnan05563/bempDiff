package com.bempdiff.unpack;

/**
 * 解包配置（外部可配，不写死）：线程池大小、单文件超时、最大递归深度与累计解压字节上限。
 * 由 BempServer 从 CompareOptions 派生，传入 NestedUnpacker。
 */
public final class UnpackOptions {
    /** 并发解包线程数（LAN 磁盘 I/O 密集，默认 4，可按机器核数上调）。 */
    public int threadPoolSize = 4;
    /** 单个根容器解包的平摊预算（毫秒）：屏障按「预算×root 数」作整体 deadline，单个 root 可占用剩余预算，
     *  不被单独截断。设 60s 为深层 webapp WAR（展开上百子文件）留足余量；totalBytesCap/maxDepth 仍防病态放大。 */
    public long perItemTimeoutMs = 60_000;
    /** 嵌套归档最大递归深度，防止病态深层 zip 无限下钻。 */
    public int maxDepth = 6;
    /** 临时目录累计解压字节总上限（防御 zip bomb 集群放大）。 */
    public long totalBytesCap = 512L * 1024 * 1024;
    /** 比对级忽略扩展名（含点小写，如 .log/.mf）。扁平化展开时，命中该后缀的原子文件跳过不落入快照，
     *  与 PackageParser/FolderParser/ArchiveTree 的解析阶段过滤保持一致（防止 unpackNested 下嵌套内部漏出）。 */
    public java.util.List<String> ignoreExtensions = new java.util.ArrayList<>();

    public UnpackOptions() {
    }
}