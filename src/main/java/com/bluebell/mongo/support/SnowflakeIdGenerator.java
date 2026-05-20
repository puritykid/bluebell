package com.bluebell.mongo.support;

import org.springframework.stereotype.Component;

/**
 * 雪花 ID；生产环境请替换为公司统一发号器。
 */
@Component
public class SnowflakeIdGenerator {

    public long nextId() {
        try {
            return cn.hutool.core.util.IdUtil.getSnowflakeNextId();
        } catch (NoClassDefFoundError e) {
            return System.currentTimeMillis() << 10;
        }
    }
}
