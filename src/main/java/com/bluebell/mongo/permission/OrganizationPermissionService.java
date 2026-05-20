package com.bluebell.mongo.permission;

import java.util.List;

/**
 * 解析当前登录用户可访问的 organizationId 列表，由业务实现。
 */
public interface OrganizationPermissionService {

    /**
     * @return 可访问组织 ID；空列表表示「全部组织」（超级管理员）
     */
    List<String> resolveOrganizationIds();
}
