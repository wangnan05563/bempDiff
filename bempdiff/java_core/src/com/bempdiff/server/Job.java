package com.bempdiff.server;

import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStats;
import com.bempdiff.model.PackageSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;

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
    /** 两侧物理平铺解包报告（unpackNested 开启时非空；[0]=旧 侧，[1]=新 侧）。引用 volatile 保证可见性。 */
    private volatile com.bempdiff.unpack.UnpackReport[] unpackReports;

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

    /** 该作业的物理解包运行时目录（{user.home}/.bempdiff/runtime/<jobId>），作业被取代/淘汰时整体回收。 */
    private volatile Path runtimeDir;

    /** P1-E 终态时间戳（ms）：进入 DONE/ERROR/CANCELLED 时记录，供 JobStore 按「最旧已结束」淘汰。 */
    public volatile long finishedAtMillis;

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

    public com.bempdiff.unpack.UnpackReport[] getUnpackReports() { return unpackReports; }
    public void setUnpackReports(com.bempdiff.unpack.UnpackReport[] unpackReports) { this.unpackReports = unpackReports; }

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

    /** 追加非致命警告到 message（如"解包不完整"），不改变状态/进度，供前端轮询识别部分完成。 */
    public synchronized void appendMessage(String extra) {
        if (extra == null || extra.isEmpty()) return;
        this.message = (this.message == null || this.message.isEmpty()) ? extra : this.message + "；" + extra;
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
        this.finishedAtMillis = System.currentTimeMillis();
    }

    /** 比对失败：记录错误并置 ERROR。仅当仍 RUNNING 时生效。 */
    public void fail(String error) {
        if (!STATUS_RUNNING.equals(status)) return;
        this.status = "ERROR";
        this.phase = "error";
        this.message = "失败";
        this.error = error;
        this.finishedAtMillis = System.currentTimeMillis();
    }

    /** 被取消：将 QUEUED/RUNNING 态置为 CANCELLED（幂等，不覆盖已完成/已失败的终态）。
     *  原实现仅允许 RUNNING，导致「提交后立刻取消」的任务一直停在 QUEUED 永不淘汰；P1-E 放宽到
     *  已排队/进行中均可取消，并记录结束时间供 JobStore 淘汰。 */
    public void markCancelled() {
        if (isTerminal(status)) return;
        this.status = "CANCELLED";
        this.phase = "cancelled";
        this.message = "已取消";
        this.finishedAtMillis = System.currentTimeMillis();
    }

    /** 是否已进入终态（DONE/ERROR/CANCELLED），用于幂等保护与淘汰判断。 */
    private static boolean isTerminal(String st) {
        return "DONE".equals(st) || "ERROR".equals(st) || "CANCELLED".equals(st);
    }

    /** 请求取消（幂等）。 */
    public void requestCancel() {
        this.cancelRequested = true;
    }

    public Path getRuntimeDir() { return runtimeDir; }
    public void setRuntimeDir(Path p) { this.runtimeDir = p; }

    /**
     * 回收该作业的物理解包运行时目录（递归删除，容错）。作业被新比对取代或 JobStore 淘汰时调用，
     * 避免每次对比在磁盘累积大量解压垃圾（此前 %TEMP% 的 atom/目录只靠进程退出的 deleteOnExit 兜底）。
     */
    public void cleanupRuntime() {
        Path d = runtimeDir;
        runtimeDir = null;
        if (d == null) return;
        try {
            if (!Files.exists(d)) return;
            if (Files.isDirectory(d)) {
                try (java.util.stream.Stream<Path> s = Files.walk(d)) {
                    s.sorted(java.util.Comparator.reverseOrder())
                     .forEach(x -> { try { Files.deleteIfExists(x); } catch (Exception ignored) { } });
                }
            } else {
                Files.deleteIfExists(d);
            }
        } catch (Exception ignored) {
            // 删除失败（被占用/权限）静默，不干扰主流程；下次取代/退出时再兜底。
        }
    }
}