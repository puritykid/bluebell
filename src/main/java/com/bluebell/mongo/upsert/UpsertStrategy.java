package com.bluebell.mongo.upsert;

/**
 * 实体级默认 upsert 策略。
 */
public enum UpsertStrategy {

    /** 按业务键：不存在插入，存在则忽略（字段默认仅 setOnInsert） */
    INSERT_ONLY,

    /** 按业务键：不存在插入，存在则按实体字段全量 set 更新 */
    FULL_BY_KEY,

    /** 按业务键 + 时间字段：仅当新记录时间更大（或相等）才更新，防旧盖新 */
    UPSERT_IF_NEWER,

    /**
     * 不存在：插入（可雪花 _id），字段默认仅插入；
     * 存在：仅更新标注了 {@link UpsertOnUpdate} 或 {@link UpsertField#mode()}{@code = ALWAYS} 的字段。
     * 若配置了 {@link UpsertEntity#timeField()}，更新时同样防旧盖新。
     */
    UPSERT_SELECTIVE
}
