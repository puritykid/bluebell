package com.bluebell.mongo.permission;

import java.lang.annotation.*;

/**
 * 标注在 Service 方法上，开启组织数据权限。
 * 在方法执行期间，Mongo 查询/更新/删除会自动拼接 organizationId 条件；
 * 插入/upsert 会自动写入当前组织（或权限范围内的组织）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /** Mongo 文档中的组织字段名，默认 organizationId */
    String field() default "organizationId";

    /**
     * 是否允许跨组织（超级管理员）。
     * true 且权限服务返回 empty 表示不过滤。
     */
    boolean allowAllWhenEmpty() default true;
}
