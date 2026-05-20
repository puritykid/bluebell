package com.bluebell.mongo.permission;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * 对 Query / Update 拼接组织数据权限。
 */
public final class DataPermissionCriteria {

    private DataPermissionCriteria() {
    }

    public static Query apply(Query query) {
        if (!DataPermissionContext.shouldFilter()) {
            return query;
        }
        return query.addCriteria(orgCriteria());
    }

    public static Query apply(Query query, String field) {
        if (!DataPermissionContext.shouldFilter()) {
            return query;
        }
        return query.addCriteria(orgCriteria(field));
    }

    public static Criteria orgCriteria() {
        return orgCriteria(DataPermissionContext.field());
    }

    public static Criteria orgCriteria(String field) {
        if (DataPermissionContext.denyAll()) {
            return Criteria.where(field).is("__NO_PERMISSION__");
        }
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (orgIds.isEmpty()) {
            return Criteria.where(field).is("__NO_PERMISSION__");
        }
        return Criteria.where(field).in(orgIds);
    }

    public static Update applyInsertOrg(Update update) {
        return applyInsertOrg(update, DataPermissionContext.field());
    }

    public static Update applyInsertOrg(Update update, String field) {
        if (!DataPermissionContext.isActive() || DataPermissionContext.denyAll()) {
            return update;
        }
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (orgIds.size() == 1) {
            update.setOnInsert(field, orgIds.get(0));
        }
        return update;
    }

    public static void assertWritable(String organizationId) {
        if (!DataPermissionContext.shouldFilter()) {
            return;
        }
        if (DataPermissionContext.denyAll()) {
            throw new DataPermissionDeniedException("无组织数据权限（业务机构与用户权限无交集）");
        }
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (organizationId == null || !orgIds.contains(organizationId)) {
            throw new DataPermissionDeniedException("无组织数据权限: organizationId=" + organizationId);
        }
    }
}
