package com.bempdiff.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 比对任务的内存注册表（单进程内，按 jobId 检索）。 */
public final class JobStore {
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public Job put(Job job) {
        jobs.put(job.id, job);
        return job;
    }

    public Job get(String id) {
        return jobs.get(id);
    }

    public Map<String, Job> all() {
        return jobs;
    }
}