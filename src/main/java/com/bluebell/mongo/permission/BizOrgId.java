package com.bluebell.mongo.permission;

import java.lang.annotation.*;

/**
 * 标注在 Service 方法参数上，表示「业务传入的机构 ID」。
 * 与 {@link DataPermission} 配合，用于与用户数据权限、子机构取交集。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BizOrgId {
}
