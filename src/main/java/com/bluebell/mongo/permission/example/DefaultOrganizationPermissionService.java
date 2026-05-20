package com.bluebell.mongo.permission.example;

import com.bluebell.mongo.permission.OrganizationPermissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 默认权限解析（示例）。请在业务项目中实现自己的 {@link OrganizationPermissionService}。
 */
@Service
@ConditionalOnMissingBean(OrganizationPermissionService.class)
public class DefaultOrganizationPermissionService implements OrganizationPermissionService {

    @Override
    public List<String> resolveOrganizationIds() {
        // TODO: 从登录用户 / JWT / 租户上下文解析，例如：
        // return loginUser.getOrganizationIds();
        // 返回 emptyList() 且 @DataPermission(allowAllWhenEmpty=true) 表示超级管理员不过滤
        return Collections.emptyList();
    }
}
