package com.bluebell.mongo.permission;

import org.bson.Document;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 Aggregation 管道拼接组织数据权限（管道最前面加 $match）。
 */
public final class DataPermissionAggregation {

    private DataPermissionAggregation() {
    }

    /** 组织权限 $match 阶段 */
    public static MatchOperation orgMatchStage() {
        return Aggregation.match(DataPermissionCriteria.orgCriteria());
    }

    public static MatchOperation orgMatchStage(String field) {
        return Aggregation.match(DataPermissionCriteria.orgCriteria(field));
    }

    /**
     * 在已有 Aggregation 最前面插入 $match(organizationId in ...)。
     */
    public static Aggregation prependOrgMatch(Aggregation aggregation) {
        if (!DataPermissionContext.shouldFilter()) {
            return aggregation;
        }
        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(orgMatchStage());
        operations.addAll(aggregation.getPipeline().getOperations());
        return Aggregation.newAggregation(operations);
    }

    /**
     * 构建管道：先 $match 权限，再接后续阶段。
     */
    public static Aggregation of(AggregationOperation... operations) {
        List<AggregationOperation> list = new ArrayList<>();
        if (DataPermissionContext.shouldFilter()) {
            list.add(orgMatchStage());
        }
        list.addAll(List.of(operations));
        return Aggregation.newAggregation(list);
    }

    /**
     * 原生 Document 管道（MongoCollection.aggregate(List&lt;Document&gt;)）最前面插入 $match。
     */
    public static List<Document> prependOrgMatchDocuments(List<Document> pipeline) {
        if (!DataPermissionContext.shouldFilter()) {
            return pipeline;
        }
        List<Document> result = new ArrayList<>();
        result.add(orgMatchDocument());
        result.addAll(pipeline);
        return result;
    }

    public static Document orgMatchDocument() {
        String field = DataPermissionContext.field();
        List<String> orgIds = DataPermissionContext.organizationIds();
        Document match;
        if (orgIds.isEmpty()) {
            match = new Document(field, "__NO_PERMISSION__");
        } else {
            match = new Document(field, new Document("$in", orgIds));
        }
        return new Document("$match", match);
    }

    /**
     * 对 Criteria 再叠一层组织权限（用于 Aggregation.match(criteria) 前）。
     */
    public static Criteria andOrg(Criteria criteria) {
        if (!DataPermissionContext.shouldFilter()) {
            return criteria;
        }
        return new Criteria().andOperator(criteria, DataPermissionCriteria.orgCriteria());
    }
}
