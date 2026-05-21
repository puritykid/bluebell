package com.bluebell.mongo.permission;

import java.lang.annotation.*;

/**
 * 标注在 Service 方法上，开启组织数据权限。
 * <p>
 * 最终可访问机构 = <b>用户数据权限</b> ∩ <b>（业务传入机构 + 其子机构）</b>。
 * 业务未传机构时，仅按用户数据权限过滤。
 * 用户为超管且未传业务机构时，不过滤。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /** Mongo 文档中的组织字段名，默认 organizationId */
    String field() default "organizationId";

    /**
     * 业务机构 ID 的参数名（需编译参数名：{@code -parameters}）。
     * 与 {@link BizOrgId} 二选一；同时存在时优先 {@link BizOrgId}。
     */
    String bizOrgParam() default "";

    /** 是否将业务机构展开为「自身 + 所有子机构」再参与交集 */
    boolean includeBizChildren() default true;

    /**
     * 用户数据权限为空时是否视为超管（不过滤），
     * 但若同时传入业务机构，则仍按业务子树过滤。
     */
    boolean allowAllWhenEmpty() default true;
}
