package com.bempdiff.util;

import java.nio.charset.StandardCharsets;

/**
 * 文本字节容错解码（与 Decompiler.decode 同源策略）。
 * 票据系统前端资源可能以 UTF-8 / GBK 落地，按候选编码依次尝试，
 * 最后一个兜底为 UTF-8（不抛异常，避免单文件解码失败中断整体比对）。
 */
public final class TextCodec {

    private TextCodec() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static String decode(byte[] bs) {
        if (bs == null) return "";
        // GBK 容错：CFR/javap 中文常量按系统编码输出，前端资源同理，utf-8 -> gbk -> latin-1 -> utf-8 兜底
        for (String enc : new String[]{StandardCharsets.UTF_8.name(), "gbk", "latin-1"}) {
            try {
                return new String(bs, enc);
            } catch (Exception ignored) {
                // 尝试下一种编码
            }
        }
        try {
            return new String(bs, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return new String(bs);
        }
    }
}
