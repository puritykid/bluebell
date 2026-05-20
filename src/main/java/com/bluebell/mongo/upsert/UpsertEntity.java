package com.bluebell.mongo.upsert;

import java.lang.annotation.*;

/**
 * 标注在 Mongo 实体上，声明默认 upsert 策略与业务主键、防旧盖新时间字段。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpsertEntity {

    /** 默认策略：不存在插入 / 存在全量更新 / 按时间只向前更新 */
    UpsertStrategy strategy() default UpsertStrategy.FULL_BY_KEY;

    /**
     * 业务主键字段名（与 {@link UpsertKey} 二选一或并用）。
     * 未标注 {@link UpsertKey} 时使用此数组。
     */
    String[] keys() default {};

    /**
     * {@link UpsertStrategy#UPSERT_IF_NEWER} 时必填，用于比较新旧。
     */
    String timeField() default "";

    /** 主键字段名，默认 _id；仅插入且值为空时可自动生成 */
    String idField() default "_id";

    /** 是否在 insert 时自动生成主键（需配合 {@link UpsertIdGenerator}） */
    boolean generateIdOnInsert() default false;
}
