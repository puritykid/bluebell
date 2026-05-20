package com.bluebell.mongo.upsert;

import java.lang.annotation.*;

/** 覆盖实体默认策略的字段级行为 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpsertField {

    FieldUpsertMode mode() default FieldUpsertMode.DEFAULT;

    /** Mongo 字段名，默认与 Java 字段同名 */
    String name() default "";
}
