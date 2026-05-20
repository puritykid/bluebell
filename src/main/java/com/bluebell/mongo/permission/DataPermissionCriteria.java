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
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (orgIds.isEmpty()) {
            // 非 allowAll 且无组织 → 查不到数据
            return Criteria.where(field).is("__NO_PERMISSION__");
        }
        return Criteria.where(field).in(orgIds);
    }

    /**
     * upsert/insert 写入组织字段（取权限范围内第一个，或由业务在 DTO 自带）。
     */
    public static Update applyInsertOrg(Update update) {
        return applyInsertOrg(update, DataPermissionContext.field());
    }

    public static Update applyInsertOrg(Update update, String field) {
        if (!DataPermissionContext.isActive()) {
            return update;
        }
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (orgIds.size() == 1) {
            update.setOnInsert(field, orgIds.get(0));
        }
        return update;
    }

    /**
     * 校验本条数据的 organizationId 是否在权限范围内。
     */
    public static void assertWritable(String organizationId) {
        if (!DataPermissionContext.shouldFilter()) {
            return;
        }
        List<String> orgIds = DataPermissionContext.organizationIds();
        if (organizationId == null || !orgIds.contains(organizationId)) {
            throw new DataPermissionDeniedException("无组织数据权限: organizationId=" + organizationId);
        }
    }
}
