package com.bluebell.mongo.bulk;

/**
 * 批量写入模式（反射工具 {@link com.bluebell.mongo.upsert.ReflectiveMongoBulkHelper} 使用）。
 */
public enum BulkWriteMode {

    /** 批量新增：按业务键不存在才插入，已存在则忽略 */
    INSERT_ONLY,

    /** 批量修改：按业务键只 update，不存在则不插入 */
    UPDATE_ONLY,

    /**
     * 批量 upsert：不存在插入、存在修改。
     * 存在时的字段范围由实体 {@link com.bluebell.mongo.upsert.UpsertStrategy} 决定。
     */
    UPSERT
}
