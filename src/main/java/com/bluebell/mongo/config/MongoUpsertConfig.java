package com.bluebell.mongo.config;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import com.bluebell.mongo.upsert.ReflectiveMongoBulkHelper;
import com.bluebell.mongo.upsert.ReflectiveWxMsgBatchWriter;
import com.bluebell.mongo.upsert.SnowflakeUpsertIdGenerator;
import com.bluebell.mongo.upsert.UpsertIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 纯 MongoDB 批量 upsert 配置（不含数据权限）。
 * <p>
 * 需要数据权限时，额外引入 {@link com.bluebell.mongo.permission.DataPermissionConfig}。
 */
@Configuration
@EnableTransactionManagement
public class MongoUpsertConfig {

    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    public TransactionTemplate transactionTemplate(MongoTransactionManager manager) {
        return new TransactionTemplate(manager);
    }

    @Bean
    public MongoBulkHelper mongoBulkHelper(MongoTemplate mongoTemplate, TransactionTemplate transactionTemplate) {
        return new MongoBulkHelper(mongoTemplate, transactionTemplate);
    }

    @Bean
    public UpsertIdGenerator upsertIdGenerator(SnowflakeIdGenerator snowflake) {
        return new SnowflakeUpsertIdGenerator(snowflake);
    }

    @Bean
    public ReflectiveMongoBulkHelper reflectiveMongoBulkHelper(
            MongoBulkHelper mongoBulkHelper,
            UpsertIdGenerator upsertIdGenerator) {
        return new ReflectiveMongoBulkHelper(mongoBulkHelper, upsertIdGenerator);
    }

    @Bean
    public ReflectiveWxMsgBatchWriter reflectiveWxMsgBatchWriter(ReflectiveMongoBulkHelper reflectiveMongoBulkHelper) {
        return new ReflectiveWxMsgBatchWriter(reflectiveMongoBulkHelper);
    }
}
