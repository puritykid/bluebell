package com.bluebell.mongo.permission;

import java.util.List;

/**
 * 解析机构树：给定机构 ID，返回「自身 + 所有子机构」ID 列表。
 * <p>
 * 由业务实现（查库、缓存、Feign 等）。
 */
public interface OrganizationHierarchyService {

    /**
     * @param organizationId 业务传入的机构 ID，null/空 表示不限制业务机构范围
     * @return 包含自身在内的子树机构 ID；传入根节点则返回整棵子树
     */
    List<String> resolveSelfAndChildren(String organizationId);
}
