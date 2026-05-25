package com.bluebell.mongo.config;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.bulk.WxMsgBatchWriter;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MongoDB 批量写入配置（不含数据权限）。
 * <p>
 * 需要数据权限时，额外引入 {@link com.bluebell.mongo.permission.DataPermissionConfig}。
 */
@Configuration
@EnableTransactionManagement
public class MongoBulkConfig {

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
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator();
    }

    @Bean
    public WxMsgBatchWriter wxMsgBatchWriter(MongoBulkHelper mongoBulkHelper, SnowflakeIdGenerator snowflake) {
        return new WxMsgBatchWriter(mongoBulkHelper, snowflake);
    }
}
