package com.bluebell.mongo.permission;

import java.util.Collections;
import java.util.List;

/**
 * 当前线程数据权限上下文（由 AOP 写入）。
 */
public final class DataPermissionContext {

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private DataPermissionContext() {
    }

    public static void set(String field, List<String> organizationIds, boolean allowAllWhenEmpty) {
        HOLDER.set(new Holder(field, organizationIds, allowAllWhenEmpty));
    }

    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    public static String field() {
        Holder h = HOLDER.get();
        return h == null ? "organizationId" : h.field();
    }

    public static List<String> organizationIds() {
        Holder h = HOLDER.get();
        return h == null ? Collections.emptyList() : h.organizationIds();
    }

    public static boolean allowAllWhenEmpty() {
        Holder h = HOLDER.get();
        return h != null && h.allowAllWhenEmpty();
    }

    /** 是否需要拼接权限条件 */
    public static boolean shouldFilter() {
        Holder h = HOLDER.get();
        if (h == null) {
            return false;
        }
        if (h.organizationIds().isEmpty() && h.allowAllWhenEmpty()) {
            return false;
        }
        return true;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private record Holder(String field, List<String> organizationIds, boolean allowAllWhenEmpty) {
    }
}
