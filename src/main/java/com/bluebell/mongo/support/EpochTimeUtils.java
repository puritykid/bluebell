package com.bluebell.mongo.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 时间字段统一为 MongoDB 中的 long（一般为 epoch 毫秒）。
 */
public final class EpochTimeUtils {

    private EpochTimeUtils() {
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /** 是否晚于当前时刻（毫秒）；null 视为否 */
    public static boolean isFuture(Long epochMillis) {
        return epochMillis != null && epochMillis > currentTimeMillis();
    }

    /**
     * 是否允许写入：时间为 null 或 ≤ 当前时刻；未来时间不写库。
     */
    public static boolean isWritable(Long epochMillis) {
        return !isFuture(epochMillis);
    }

    /**
     * 入库前规范化：已是 Long 则原样返回；兼容接入层仍传 LocalDateTime/Date 的情况。
     */
    public static Long normalizeToMillis(Object time) {
        if (time == null) {
            return null;
        }
        if (time instanceof Long l) {
            return l;
        }
        if (time instanceof Integer i) {
            return i.longValue();
        }
        if (time instanceof LocalDateTime ldt) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (time instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (time instanceof Date date) {
            return date.getTime();
        }
        throw new IllegalArgumentException("不支持的时间类型: " + time.getClass().getName());
    }
}
