package com.bluebell.mongo.upsert;

/**
 * 插入时生成主键（如雪花 ID）。
 */
public interface UpsertIdGenerator {

    Object nextId(Class<?> entityClass);
}
