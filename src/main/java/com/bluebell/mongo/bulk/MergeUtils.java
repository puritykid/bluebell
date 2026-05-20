package com.bluebell.mongo.bulk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * 批内按键合并，避免同批旧数据覆盖新数据。
 */
public final class MergeUtils {

    private MergeUtils() {
    }

    public static <T> List<T> mergeByKey(List<T> source, Function<T, String> keyFn, BinaryOperator<T> mergeFn) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : source) {
            String key = keyFn.apply(item);
            map.merge(key, item, mergeFn);
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 保留 msgTime 更大的一条（需传入 Comparable 提取函数）。
     */
    public static <T, C extends Comparable<C>> BinaryOperator<T> keepNewer(Function<T, C> timeFn) {
        return (a, b) -> {
            C ta = timeFn.apply(a);
            C tb = timeFn.apply(b);
            if (ta == null) {
                return b;
            }
            if (tb == null) {
                return a;
            }
            return ta.compareTo(tb) >= 0 ? a : b;
        };
    }
}
