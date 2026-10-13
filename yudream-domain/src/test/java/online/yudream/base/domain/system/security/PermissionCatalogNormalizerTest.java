package online.yudream.base.domain.system.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCatalogNormalizerTest {

    @Test
    void mergesEndpointDescriptionsByCodeWithoutLosingDistinctDeclaredOperations() {
        var merged = PermissionCatalogNormalizer.normalize(List.of(
                new PermissionMeta("system:role:edit", "编辑角色", "系统管理", "编辑角色"),
                new PermissionMeta("system:role:edit", "分配角色权限", "系统管理", "增量授予或撤销角色权限"),
                new PermissionMeta("system:role:edit", "启用角色", "系统管理", "启用角色"),
                new PermissionMeta("system:role:edit", "启用角色", "系统管理", "启用角色"))).getFirst();
        assertEquals("编辑角色", merged.name());
        assertEquals("系统管理", merged.module());
        assertTrue(merged.desc().contains("分配角色权限：增量授予或撤销角色权限"));
        assertEquals(1, merged.desc().split("启用角色", -1).length - 1);
    }

    @Test
    void selectionIsIndependentOfDiscoveryOrderAndKeepsARealBusinessModule() {
        List<PermissionMeta> metadata = List.of(
                new PermissionMeta("platform:plugin:manage", "安装市场插件", "平台插件市场", "安装"),
                new PermissionMeta("platform:plugin:manage", "管理插件", "平台插件", "管理"),
                new PermissionMeta("platform:plugin:manage", "加载插件", "平台插件", "加载"),
                new PermissionMeta("platform:plugin:manage", "platform:plugin:manage", "default", ""));
        var first = PermissionCatalogNormalizer.normalize(metadata).getFirst();
        List<PermissionMeta> reversed = new ArrayList<>(metadata);
        Collections.reverse(reversed);
        var second = PermissionCatalogNormalizer.normalize(reversed).getFirst();
        assertEquals("管理插件", first.name());
        assertEquals("平台插件", first.module());
        assertEquals(first.toString(), second.toString());
        assertFalse(first.module().contains("/"));
    }

    @Test
    void keepsSingleRealNamesAndDoesNotInventLabelsForHistoricalCodes() {
        var result = PermissionCatalogNormalizer.normalize(List.of(
                new PermissionMeta("旧权限码", "真实业务操作", "中文业务", "实际说明"),
                new PermissionMeta("z:thing:view", "查看其他项", "中文业务", "")));
        assertEquals(2, result.size());
        assertEquals("旧权限码", result.getLast().code());
        assertEquals("真实业务操作", result.getLast().name());
        assertNull(PermissionCatalogNormalizer.actionName("旧权限码"));
        assertNull(PermissionCatalogNormalizer.actionName("system:user:assign-"));
        assertEquals("分配", PermissionCatalogNormalizer.actionName("system:user:assign-user-role"));
    }
}
