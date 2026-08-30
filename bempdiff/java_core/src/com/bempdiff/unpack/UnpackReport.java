package com.bempdiff.unpack;

import com.bempdiff.server.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次解包（单个输入侧）的状态报告：逐条目状态 + 错误聚合 + 线程/耗时统计。
 * 多线程复写需并发安全：状态用 ConcurrentHashMap、错误用 synchronizedList。
 * toJson() 复用服务端 Json 写入器产出结构化 JSON，供落盘与 /api/job/{id}/unpack-report 读取。
 */
public final class UnpackReport {
    private final String kind;                                  // folder/war/zip
    private final Map<String, String> status = new ConcurrentHashMap<>(); // key -> success|fail
    private final List<UnpackError> errors = Collections.synchronizedList(new ArrayList<>());
    private volatile int threadCount;
    private volatile int fileCount;
    private volatile long startedAt = System.currentTimeMillis();
    private volatile long elapsedMs;
    private volatile boolean incomplete;

    public UnpackReport(String kind) {
        this.kind = kind;
    }

    public void setThreadCount(int n) { threadCount = n; }
    public void addSuccess(String key) { status.put(key, "success"); fileCount++; }
    public void addError(UnpackError e) { status.put(e.key, "fail"); fileCount++; errors.add(e); }
    /** 解包收尾：记录总耗时；超时/部分完成时调用方置 incomplete=true。 */
    public void finish() { elapsedMs = System.currentTimeMillis() - startedAt; }
    public void markIncomplete() { incomplete = true; }

    public String getKind() { return kind; }
    public int getThreadCount() { return threadCount; }
    public int getFileCount() { return fileCount; }
    public long getElapsedMs() { return elapsedMs; }
    public boolean isIncomplete() { return incomplete; }
    public List<UnpackError> getErrors() { return errors; }
    public Map<String, String> getStatus() { return status; }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("status", status);
        m.put("threadCount", threadCount);
        m.put("fileCount", fileCount);
        m.put("elapsedMs", elapsedMs);
        m.put("incomplete", incomplete);
        List<Map<String, Object>> es = new ArrayList<>();
        synchronized (errors) {
            for (UnpackError e : errors) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("key", e.key);
                em.put("stage", e.stage);
                em.put("message", e.message);
                es.add(em);
            }
        }
        m.put("errors", es);
        return Json.write(m);
    }
}