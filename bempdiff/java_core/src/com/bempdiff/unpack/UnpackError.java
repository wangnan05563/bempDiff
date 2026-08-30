package com.bempdiff.unpack;

/**
 * 单个解包条目的失败记录（异常隔离，不中断整体任务）。
 * key/stage 定位失败位置，message 描述损坏/权限/超限等原因。
 */
public final class UnpackError {
    public final String key;
    public final String stage;
    public final String message;

    public UnpackError(String key, String stage, String message) {
        this.key = key;
        this.stage = stage;
        this.message = message;
    }
}