package com.bluebell.mongo.upsert;

import com.bluebell.mongo.support.SnowflakeIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeUpsertIdGenerator implements UpsertIdGenerator {

    private final SnowflakeIdGenerator snowflake;

    public SnowflakeUpsertIdGenerator(SnowflakeIdGenerator snowflake) {
        this.snowflake = snowflake;
    }

    @Override
    public Object nextId(Class<?> entityClass) {
        return snowflake.nextId();
    }
}
