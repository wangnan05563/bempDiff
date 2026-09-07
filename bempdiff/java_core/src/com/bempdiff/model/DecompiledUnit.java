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
    // 7 位短哈希（类比 git rev-parse --short=7），缺失侧为 "0000000"，供 filebar 渲染 oldHash↔newHash 徽标
    private final String oldHash;
    private final String newHash;

    /** 缺失侧哈希占位串（7 个零），供 filebar 渲染 oldHash↔newHash 徽标占位。 */
    private static final String MISSING_HASH = "0000000";

    /** 9 参主构造器：含短哈希字段。 */
    public DecompiledUnit(String key, String oldSource, String newSource, String diffText, // NOSONAR(S107) - 9 参构造器为固定数据载体，涉及多个字段的单一赋值点，拆参对象化反而增加样板
                          String engine, String error, boolean ok,
                          String oldHash, String newHash) {
        this.key = key;
        this.oldSource = oldSource;
        this.newSource = newSource;
        this.diffText = diffText;
        this.engine = engine;
        this.error = error;
        this.ok = ok;
        // null/空串统一回退 0000000，避免前端拿到 undefined/空字符串时徽标不渲染
        this.oldHash = (oldHash == null || oldHash.isEmpty()) ? MISSING_HASH : oldHash;
        this.newHash = (newHash == null || newHash.isEmpty()) ? MISSING_HASH : newHash;
    }

    /** 兼容旧 7 参构造器：哈希字段默认为 "0000000"。新代码请直接传 9 参。 */
    public DecompiledUnit(String key, String oldSource, String newSource, String diffText,
                          String engine, String error, boolean ok) {
        this(key, oldSource, newSource, diffText, engine, error, ok, MISSING_HASH, MISSING_HASH);
    }

    public String getKey() { return key; }
    public String getOldSource() { return oldSource; }
    public String getNewSource() { return newSource; }
    public String getDiffText() { return diffText; }
    public String getEngine() { return engine; }
    public String getError() { return error; }
    public boolean isOk() { return ok; }
    public String getOldHash() { return oldHash; }
    public String getNewHash() { return newHash; }

    public static DecompiledUnit fail(String key, String error) {
        // 失败时也保留 0000000 占位，便于前端 filebar 仍能渲染哈希徽标（虽然 0/0 视觉无变化但布局稳定）
        return new DecompiledUnit(key, null, null, "", "none", error, false, MISSING_HASH, MISSING_HASH);
    }
}
