package com.bempdiff.ai;

/** 阶段A 每文件初评（§5.4）。 */
public final class FileRisk {
    private String key;
    private String risk;           // LOW / MEDIUM / HIGH
    private String oneLineReason;

    public FileRisk(String key, String risk, String oneLineReason) {
        this.key = key;
        this.risk = risk;
        this.oneLineReason = oneLineReason;
    }

    public String getKey() { return key; }
    public String getRisk() { return risk; }
    public String getOneLineReason() { return oneLineReason; }
}
