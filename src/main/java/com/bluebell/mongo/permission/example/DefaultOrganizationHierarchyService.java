package com.bluebell.mongo.permission.example;

import com.bluebell.mongo.permission.OrganizationHierarchyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 示例子机构解析。生产请替换为：查组织表 / 缓存 / 权限中心。
 */
@Service
@ConditionalOnMissingBean(OrganizationHierarchyService.class)
public class DefaultOrganizationHierarchyService implements OrganizationHierarchyService {

    /**
     * 示例静态树：org_root → org_1001, org_1002；org_1001 → org_1001_a
     */
    private static final Map<String, List<String>> CHILDREN = Map.of(
            "org_root", List.of("org_1001", "org_1002"),
            "org_1001", List.of("org_1001_a"),
            "org_1002", List.of()
    );

    @Override
    public List<String> resolveSelfAndChildren(String organizationId) {
        List<String> result = new ArrayList<>();
        collect(organizationId, result);
        return result;
    }

    private void collect(String orgId, List<String> out) {
        if (orgId == null || orgId.isBlank()) {
            return;
        }
        out.add(orgId);
        List<String> children = CHILDREN.get(orgId);
        if (children != null) {
            for (String child : children) {
                collect(child, out);
            }
        }
        // TODO: 生产环境改为递归查库，例如：
        // List<Org> children = orgRepository.findByParentId(orgId);
    }
}
