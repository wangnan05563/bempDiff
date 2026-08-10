package com.bempdiff.model;

/**
 * 反编译产物（prototype: decompile.DecompiledUnit）。
 * 新增类仅 newSource 有值、删除类仅 oldSource 有值，二者皆有即为修改类。
 */
public final class DecompiledUnit {
    private final String key;
    private final String oldSource;   // 老包侧源码（删除类为 null）
    private final String newSource;   // 新包侧源码（新增类为 null）
    private final String diffText;    // 统一格式源码级 diff
    private final String engine;      // "cfr" / "javap" / "none"
    private final String error;       // 失败原因
    private final boolean ok;

    public DecompiledUnit(String key, String oldSource, String newSource, String diffText,
                          String engine, String error, boolean ok) {
        this.key = key;
        this.oldSource = oldSource;
        this.newSource = newSource;
        this.diffText = diffText;
        this.engine = engine;
        this.error = error;
        this.ok = ok;
    }

    public String getKey() { return key; }
    public String getOldSource() { return oldSource; }
    public String getNewSource() { return newSource; }
    public String getDiffText() { return diffText; }
    public String getEngine() { return engine; }
    public String getError() { return error; }
    public boolean isOk() { return ok; }

    public static DecompiledUnit fail(String key, String error) {
        return new DecompiledUnit(key, null, null, "", "none", error, false);
    }
}
