package com.bluebell.mongo.permission;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 合并「用户数据权限」与「业务机构 + 子机构」，取交集得到最终可访问机构列表。
 */
public final class DataPermissionResolver {

    private DataPermissionResolver() {
    }

    /**
     * @param userOrgIds           用户数据权限机构列表
     * @param bizOrgId             业务传入机构 ID（可空）
     * @param hierarchyService     机构子树查询
     * @param includeBizChildren   是否展开子机构
     * @param allowAllWhenEmpty    用户权限为空时是否视为「全部组织」（超管）
     * @return 解析结果
     */
    public static ResolveResult resolve(
            List<String> userOrgIds,
            String bizOrgId,
            OrganizationHierarchyService hierarchyService,
            boolean includeBizChildren,
            boolean allowAllWhenEmpty
    ) {
        List<String> safeUser = userOrgIds == null ? List.of() : userOrgIds;
        List<String> bizScope = resolveBizScope(bizOrgId, hierarchyService, includeBizChildren);

        boolean userIsSuperAdmin = safeUser.isEmpty() && allowAllWhenEmpty;

        // 超管 + 未传业务机构 → 不过滤
        if (userIsSuperAdmin && bizScope == null) {
            return ResolveResult.noFilter();
        }
        // 超管 + 传了业务机构 → 仅按业务子树过滤
        if (userIsSuperAdmin) {
            return ResolveResult.filterBy(bizScope);
        }
        // 普通用户 + 未传业务机构 → 仅按用户权限
        if (bizScope == null) {
            return ResolveResult.filterBy(safeUser);
        }
        // 普通用户 + 传了业务机构 → 交集
        Set<String> userSet = new HashSet<>(safeUser);
        List<String> intersection = bizScope.stream().filter(userSet::contains).toList();
        return ResolveResult.filterBy(intersection);
    }

    private static List<String> resolveBizScope(
            String bizOrgId,
            OrganizationHierarchyService hierarchyService,
            boolean includeChildren
    ) {
        if (bizOrgId == null || bizOrgId.isBlank()) {
            return null;
        }
        if (hierarchyService == null) {
            return List.of(bizOrgId);
        }
        if (includeChildren) {
            List<String> tree = hierarchyService.resolveSelfAndChildren(bizOrgId);
            return tree == null || tree.isEmpty() ? List.of(bizOrgId) : tree;
        }
        return List.of(bizOrgId);
    }

    /**
     * 解析结果。
     *
     * @param skipFilter           true = 不拼接 Mongo 条件（超管且无业务机构）
     * @param effectiveOrgIds      最终用于 in 查询的机构 ID
     */
    public record ResolveResult(boolean skipFilter, List<String> effectiveOrgIds) {

        public static ResolveResult noFilter() {
            return new ResolveResult(true, List.of());
        }

        public static ResolveResult filterBy(List<String> orgIds) {
            return new ResolveResult(false, orgIds == null ? List.of() : List.copyOf(orgIds));
        }

        /** 是否应拒绝所有数据（交集为空） */
        public boolean denyAll() {
            return !skipFilter && effectiveOrgIds.isEmpty();
        }
    }
}
