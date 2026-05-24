package com.bluebell.mongo.bulk;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * 构造「仅当库中时间不存在或更旧时才匹配」的查询条件，防止旧数据覆盖新数据。
 * <p>
 * 时间字段类型为 {@link Long}（epoch 毫秒），与库内 BSON long 一致。
 */
public final class TimeForwardCriteria {

    private TimeForwardCriteria() {
    }

    public static Criteria timeForward(String timeField, Object newTime) {
        return new Criteria().orOperator(
                Criteria.where(timeField).exists(false),
                Criteria.where(timeField).lte(newTime)
        );
    }

    /** 库内时间严格小于新时间时才匹配（用于 lastMsgTime 等只向前推进） */
    public static Criteria timeForwardStrict(String timeField, Object newTime) {
        return new Criteria().orOperator(
                Criteria.where(timeField).exists(false),
                Criteria.where(timeField).lt(newTime)
        );
    }
}
