package com.bluebell.mongo.permission;

import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * 扩展 MongoTemplate：find / aggregate 自动拼接 organizationId 权限。
 * <p>
 * 使用：注入本类替代 MongoTemplate，Service 方法加 {@link DataPermission}。
 */
public class DataPermissionMongoTemplate extends MongoTemplate {

    public DataPermissionMongoTemplate(MongoDatabaseFactory mongoDbFactory) {
        super(mongoDbFactory);
    }

    public DataPermissionMongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoConverter converter) {
        super(mongoDbFactory, converter);
    }

    @Override
    public <T> List<T> find(Query query, Class<T> entityClass) {
        return super.find(DataPermissionCriteria.apply(query), entityClass);
    }

    @Override
    public <T> T findOne(Query query, Class<T> entityClass) {
        return super.findOne(DataPermissionCriteria.apply(query), entityClass);
    }

    @Override
    public long count(Query query, Class<?> entityClass) {
        return super.count(DataPermissionCriteria.apply(query), entityClass);
    }

    @Override
    public long remove(Query query, Class<?> entityClass) {
        return super.remove(DataPermissionCriteria.apply(query), entityClass).getDeletedCount();
    }

    @Override
    public <O> AggregationResults<O> aggregate(Aggregation aggregation, Class<O> outputType) {
        return super.aggregate(DataPermissionAggregation.prependOrgMatch(aggregation), outputType);
    }

    @Override
    public <O> AggregationResults<O> aggregate(Aggregation aggregation, String collectionName, Class<O> outputType) {
        return super.aggregate(
                DataPermissionAggregation.prependOrgMatch(aggregation), collectionName, outputType);
    }

    @Override
    public <O> AggregationResults<O> aggregate(Aggregation aggregation, Class<?> inputType, Class<O> outputType) {
        return super.aggregate(
                DataPermissionAggregation.prependOrgMatch(aggregation), inputType, outputType);
    }
}
