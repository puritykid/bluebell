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

    public static void set(String field, DataPermissionResolver.ResolveResult result) {
        HOLDER.set(new Holder(field, result));
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
        if (h == null) {
            return Collections.emptyList();
        }
        return h.result().effectiveOrgIds();
    }

    /** 是否需要拼接权限条件 */
    public static boolean shouldFilter() {
        Holder h = HOLDER.get();
        if (h == null) {
            return false;
        }
        return !h.result().skipFilter();
    }

    /** 交集为空等情况，明确无数据权限 */
    public static boolean denyAll() {
        Holder h = HOLDER.get();
        return h != null && h.result().denyAll();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private record Holder(String field, DataPermissionResolver.ResolveResult result) {
    }
}
