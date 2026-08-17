package com.bempdiff.diff;

/** 差异统计（prototype: compute_stats）。 */
public final class DiffStats {
    private int added;
    private int deleted;
    private int modified;
    private int unchanged;
    private int bizChanged;   // 非 jar 的业务/类级变更（internal_or_biz_changed）
    private int jarChanged;   // jar 级变更（jar_level_changed）

    public int getAdded() { return added; }
    public void setAdded(int added) { this.added = added; }
    public int getDeleted() { return deleted; }
    public void setDeleted(int deleted) { this.deleted = deleted; }
    public int getModified() { return modified; }
    public void setModified(int modified) { this.modified = modified; }
    public int getUnchanged() { return unchanged; }
    public void setUnchanged(int unchanged) { this.unchanged = unchanged; }
    public int getBizChanged() { return bizChanged; }
    public void setBizChanged(int bizChanged) { this.bizChanged = bizChanged; }
    public int getJarChanged() { return jarChanged; }
    public void setJarChanged(int jarChanged) { this.jarChanged = jarChanged; }

    @Override
    public String toString() {
        return String.format(
                "added=%d deleted=%d modified=%d unchanged=%d | bizChanged=%d jarChanged=%d",
                added, deleted, modified, unchanged, bizChanged, jarChanged);
    }
}
