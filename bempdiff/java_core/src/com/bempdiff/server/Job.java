package com.bempdiff.server;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.model.PackageSnapshot;

/** 一次比对的内存态产物（供后续反编译 / 报告 / 导出复用，避免重复解析包）。 */
public final class Job {
    public final String id;
    public final String mode; // "package" | "folder"
    public final PackageSnapshot oldSnap;
    public final PackageSnapshot newSnap;
    public final DiffResult result;
    public final DiffStats stats;
    public final CompareOptions opts;
    public volatile String status = "DONE";
    public volatile String error;

    public Job(String id, String mode, PackageSnapshot oldSnap, PackageSnapshot newSnap,
               DiffResult result, DiffStats stats, CompareOptions opts) {
        this.id = id;
        this.mode = mode;
        this.oldSnap = oldSnap;
        this.newSnap = newSnap;
        this.result = result;
        this.stats = stats;
        this.opts = opts;
    }
}
