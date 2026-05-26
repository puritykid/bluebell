package com.bluebell.mongo.permission;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import com.bluebell.mongo.config.MongoBulkConfig;

/**
 * 数据权限模块（<b>可选</b>）。与 {@link MongoBulkConfig} 分开引入。
 */
@Configuration
@EnableAspectJAutoProxy
@Import(MongoBulkConfig.class)
public class DataPermissionConfig {

    @Bean
    public DataPermissionMongoBulkHelper dataPermissionMongoBulkHelper(MongoBulkHelper mongoBulkHelper) {
        return new DataPermissionMongoBulkHelper(mongoBulkHelper);
    }

    @Bean
    public DataPermissionWxMsgBatchWriter dataPermissionWxMsgBatchWriter(
            DataPermissionMongoBulkHelper dataPermissionMongoBulkHelper,
            SnowflakeIdGenerator snowflake) {
        return new DataPermissionWxMsgBatchWriter(dataPermissionMongoBulkHelper, snowflake);
    }

    /**
     * 用带权限的 MongoTemplate 替换默认 Bean（可选，@Primary）。
     */
    @Bean
    @ConditionalOnMissingBean(name = "dataPermissionMongoTemplate")
    public DataPermissionMongoTemplate dataPermissionMongoTemplate(
            MongoDatabaseFactory factory, MongoConverter converter) {
        return new DataPermissionMongoTemplate(factory, converter);
    }
}
