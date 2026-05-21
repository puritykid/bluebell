package com.bluebell.mongo.support;

/**
 * 消息 type 字段工具：业务侧可能是 {@link Integer} 或 {@link String}。
 */
public final class MsgTypeUtils {

    private MsgTypeUtils() {
    }

    /** 是否可作为有效 type 写入（非 null；String 非空白） */
    public static boolean isPresent(Object type) {
        if (type == null) {
            return false;
        }
        if (type instanceof String s) {
            return !s.isBlank();
        }
        return true;
    }
}
