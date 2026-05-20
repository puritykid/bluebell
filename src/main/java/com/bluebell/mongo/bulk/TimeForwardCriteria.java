package com.bluebell.mongo.bulk;

import org.springframework.data.mongodb.core.query.Criteria;

import java.time.temporal.Temporal;

/**
 * 构造「仅当库中时间不存在或更旧时才匹配」的查询条件，防止旧数据覆盖新数据。
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

    public static Criteria timeForwardStrict(String timeField, Temporal newTime) {
        return new Criteria().orOperator(
                Criteria.where(timeField).exists(false),
                Criteria.where(timeField).lt(newTime)
        );
    }
}
