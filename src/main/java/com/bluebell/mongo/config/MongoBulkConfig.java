package com.bluebell.mongo.config;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.bulk.WxMsgBatchWriter;
import com.bluebell.mongo.permission.DataPermissionWxMsgBatchWriter;
import com.bluebell.mongo.permission.DataPermissionMongoBulkHelper;
import com.bluebell.mongo.support.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

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
    public WxMsgBatchWriter wxMsgBatchWriter(MongoBulkHelper mongoBulkHelper, SnowflakeIdGenerator snowflake) {
        return new WxMsgBatchWriter(mongoBulkHelper, snowflake);
    }

    @Bean
    public DataPermissionWxMsgBatchWriter dataPermissionWxMsgBatchWriter(
            DataPermissionMongoBulkHelper dataPermissionMongoBulkHelper,
            SnowflakeIdGenerator snowflake) {
        return new DataPermissionWxMsgBatchWriter(dataPermissionMongoBulkHelper, snowflake);
    }
}
