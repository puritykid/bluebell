package com.bluebell.mongo.bulk;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.function.Function;

/**
 * 单条 upsert 规格：查询条件 + 更新内容。
 *
 * @param <S> 源数据类型（如 Kafka DTO）
 */
public record UpsertSpec<S>(Function<S, Query> queryFn, Function<S, Update> updateFn) {

    public static <S> UpsertSpec<S> of(Function<S, Query> queryFn, Function<S, Update> updateFn) {
        return new UpsertSpec<>(queryFn, updateFn);
    }
}
