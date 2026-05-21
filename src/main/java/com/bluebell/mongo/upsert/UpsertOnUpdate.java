package com.bluebell.mongo.upsert;

import java.lang.annotation.*;

/**
 * 标注「存在时也要更新」的字段（配合 {@link UpsertStrategy#UPSERT_SELECTIVE}）。
 * 未标注的字段仅在不存在插入时写入。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpsertOnUpdate {
}
