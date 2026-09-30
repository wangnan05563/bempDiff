package com.bempdiff.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T00427：极简线程安全 LRU 缓存（访问序 + 容量上限，put 触发逐出最久未访问项）。
 * 取代「ConcurrentHashMap + size 超限即 clear() 全清」的粗粒度做法：
 * 全清会把全部热缓存一次性抖掉，随后请求集中重建（延迟尖峰 + 大对象集中分配加剧内存压力）；
 * LRU 只逐出最旧条目，保留热点，容量语义与原 MAX 常量一致。
 *
 * <p>适用场景：小容量、低竞争的进程内缓存（读多写少）；synchronized 粒度足够，
 * 不引入外部依赖。键值非空约束与 HashMap 相同。</p>
 */
public final class LruMap<K, V> {
    private final LinkedHashMap<K, V> map;
    private final int max;

    public LruMap(int max) {
        this.max = Math.max(1, max);
        // accessOrder=true：get/put 均视为访问，逐出链表头（最久未访问）。
        // 初始容量给足余量，避免频繁 rehash；loadFactor 沿用默认。
        this.map = new LinkedHashMap<K, V>(Math.min(1024, this.max * 2), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LruMap.this.max;
            }
        };
    }

    public synchronized V get(K key) { return map.get(key); }

    public synchronized V put(K key, V value) { return map.put(key, value); }

    public synchronized V remove(K key) { return map.remove(key); }

    public synchronized boolean containsKey(K key) { return map.containsKey(key); }

    public synchronized int size() { return map.size(); }

    public synchronized void clear() { map.clear(); }
}
