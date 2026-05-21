package com.bluebell.mongo.config;

import com.bluebell.mongo.bulk.WxMsgBatchWriter;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.bluebell.mongo.bulk.MongoBulkHelper;

/**
 * @deprecated 请使用 {@link MongoUpsertConfig}（纯批量）；
 * 数据权限见 {@link com.bluebell.mongo.permission.DataPermissionConfig}。
 */
@Deprecated
@Configuration
@Import(MongoUpsertConfig.class)
public class MongoBulkConfig {

    @Bean
    public WxMsgBatchWriter wxMsgBatchWriter(MongoBulkHelper mongoBulkHelper, SnowflakeIdGenerator snowflake) {
        return new WxMsgBatchWriter(mongoBulkHelper, snowflake);
    }
}
