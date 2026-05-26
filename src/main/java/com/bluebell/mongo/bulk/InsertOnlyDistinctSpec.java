package com.bluebell.mongo.bulk;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.function.Function;

/**
 * 去重后批量新增：Query / Update 均基于实体 {@code E} 构建。
 */
public record InsertOnlyDistinctSpec<E, K>(
        Function<E, K> distinctKeyFn,
        Function<E, Query> queryFn,
        Function<E, Update> updateFn
) {
    public static <E, K> InsertOnlyDistinctSpec<E, K> of(
            Function<E, K> distinctKeyFn,
            Function<E, Query> queryFn,
            Function<E, Update> updateFn
    ) {
        return new InsertOnlyDistinctSpec<>(distinctKeyFn, queryFn, updateFn);
    }
}
