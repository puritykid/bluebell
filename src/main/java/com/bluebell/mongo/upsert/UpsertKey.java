package com.bluebell.mongo.upsert;

import java.lang.annotation.*;

/** 业务主键字段（可多个，组成复合键） */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpsertKey {
}
