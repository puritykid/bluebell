package com.bluebell.mongo.permission;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 原生 Document 聚合管道 + 数据权限（适用于未走 Spring Aggregation DSL 的代码）。
 */
public class DataPermissionAggregationExecutor {

    private final MongoTemplate mongoTemplate;

    public DataPermissionAggregationExecutor(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public <T> List<T> aggregate(
            String collectionName,
            List<Document> pipeline,
            Class<T> resultClass
    ) {
        List<Document> wrapped = DataPermissionAggregation.prependOrgMatchDocuments(pipeline);
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        List<T> result = new ArrayList<>();
        for (Document doc : collection.aggregate(wrapped)) {
            result.add(mongoTemplate.getConverter().read(resultClass, doc));
        }
        return result;
    }

    public List<Document> aggregateDocuments(String collectionName, List<Document> pipeline) {
        List<Document> wrapped = DataPermissionAggregation.prependOrgMatchDocuments(pipeline);
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);
        List<Document> result = new ArrayList<>();
        collection.aggregate(wrapped).into(result);
        return result;
    }
}
