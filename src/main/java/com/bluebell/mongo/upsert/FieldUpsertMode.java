package com.bluebell.mongo.upsert;

/**
 * 字段级 upsert 行为，可覆盖实体默认策略。
 */
public enum FieldUpsertMode {

    /** 默认：跟随实体 {@link UpsertStrategy} */
    DEFAULT,

    /** 业务查询键，不参与 Update */
    KEY,

    /** 仅插入时写入 */
    INSERT_ONLY,

    /** 插入/更新都 set */
    ALWAYS,

    /** 不参与映射 */
    IGNORE
}
