package com.bempdiff.server;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.model.PackageSnapshot;

/**
 * 一次比对的内存态产物（供后续反编译 / 报告 / 导出复用，避免重复解析包）。
 *
 * <p>比对本身是可能很慢的异步任务（大 war/jar 解析 + 反编译级 diff），因此 Job 在创建时只持有
 * 元信息，{@code oldSnap/newSnap/result/stats} 由后台任务完成时回填；{@code status} 经历
 * {@code QUEUED → RUNNING → DONE | ERROR | CANCELLED}，{@code progress/phase/message} 用于前端轮询展示进度。
 */
public final class Job {
    private static final String STATUS_RUNNING = "RUNNING";

    public final String id;
    public final String mode; // "package" | "folder"
    public final CompareOptions opts;

    // 由后台比对任务完成时回填（非 final，避免构造时同步阻塞）。
    // 仅存对象引用，volatile 已保证引用可见性（非数组/集合）。
    private volatile PackageSnapshot oldSnap; // NOSONAR(S3077): 引用类型快照，volatile 足够
    private volatile PackageSnapshot newSnap; // NOSONAR(S3077)
    private volatile DiffResult result;       // NOSONAR(S3077)
    private volatile DiffStats stats;         // NOSONAR(S3077)

    /** 任务状态：QUEUED(已入队) | RUNNING(进行中) | DONE(完成) | ERROR(失败) | CANCELLED(已取消)。 */
    private volatile String status = "QUEUED";
    private volatile String error;

    /** 进度百分比 0~100（仅 RUNNING 期间有意义）。 */
    private volatile int progress = 0;
    /** 当前阶段：queued | parsing | diffing | building | done | error | cancelled。 */
    private volatile String phase = "queued";
    /** 面向用户的阶段描述，如「解析包…」「计算差异…」。 */
    private volatile String message = "等待中";

    /** 取消请求标记；后台任务在阶段边界检查，置位后尽快终止并标记 CANCELLED。 */
    private volatile boolean cancelRequested = false;

    public Job(String id, String mode, CompareOptions opts) {
        this.id = id;
        this.mode = mode;
        this.opts = opts;
    }

    public PackageSnapshot getOldSnap() {
        return oldSnap;
    }

    public PackageSnapshot getNewSnap() {
        return newSnap;
    }

    public DiffResult getResult() {
        return result;
    }

    public DiffStats getStats() {
        return stats;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public int getProgress() {
        return progress;
    }

    public String getPhase() {
        return phase;
    }

    public String getMessage() {
        return message;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /** 进入某个运行阶段（含进度百分比；progress<0 表示不更新进度值）。 */
    public void markRunning(String phase, String message, int progress) {
        this.status = STATUS_RUNNING;
        this.phase = phase;
        this.message = message;
        if (progress >= 0) this.progress = progress;
    }

    /** 比对成功完成：回填产物并置 DONE/100%。仅当仍 RUNNING 时生效（已被取消/失败则忽略）。 */
    public void complete(PackageSnapshot oldSnap, PackageSnapshot newSnap, DiffResult result, DiffStats stats) {
        if (!STATUS_RUNNING.equals(status)) return;
        this.oldSnap = oldSnap;
        this.newSnap = newSnap;
        this.result = result;
        this.stats = stats;
        this.status = "DONE";
        this.phase = "done";
        this.message = "完成";
        this.progress = 100;
    }

    /** 比对失败：记录错误并置 ERROR。仅当仍 RUNNING 时生效。 */
    public void fail(String error) {
        if (!STATUS_RUNNING.equals(status)) return;
        this.status = "ERROR";
        this.phase = "error";
        this.message = "失败";
        this.error = error;
    }

    /** 被取消：仅在 RUNNING 阶段有效（幂等，避免覆盖已完成/已失败的结果）。 */
    public void markCancelled() {
        if (!STATUS_RUNNING.equals(status)) return;
        this.status = "CANCELLED";
        this.phase = "cancelled";
        this.message = "已取消";
    }

    /** 请求取消（幂等）。 */
    public void requestCancel() {
        this.cancelRequested = true;
    }
}