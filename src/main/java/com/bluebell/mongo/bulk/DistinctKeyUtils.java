package com.bluebell.mongo.bulk;

import java.util.Collection;
import java.util.Map;

/**
 * 去重键校验：支持 String、Number、组合 record、List 等作为 distinct key。
 */
public final class DistinctKeyUtils {

    private DistinctKeyUtils() {
    }

    public static boolean isPresent(Object key) {
        if (key == null) {
            return false;
        }
        if (key instanceof String s) {
            return !s.isBlank();
        }
        if (key instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (key instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (key instanceof Object[] arr) {
            return arr.length > 0;
        }
        return true;
    }
}
