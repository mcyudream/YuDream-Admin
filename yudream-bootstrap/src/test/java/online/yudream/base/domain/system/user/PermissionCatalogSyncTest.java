package online.yudream.base.domain.system.user;

import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.enumerate.PermissionSource;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PermissionCatalogSyncTest {

    @Test
    void mergesSameCodeOnceAndRestoresAnnotationOwnershipWithoutChangingDeprecationRules() {
        PermissionRepo repo = mock(PermissionRepo.class);
        Permission old = Permission.create("system:role:edit", "角色管理", "system", "菜单权限", PermissionSource.MENU);
        when(repo.findAll()).thenReturn(List.of(old));
        new PermissionDomainService(repo).syncPermissions(List.of(
                new PermissionMeta("system:role:edit", "启用角色", "系统管理", "启用角色"),
                new PermissionMeta("system:role:edit", "编辑角色", "系统管理", "编辑角色")));
        verify(repo, times(1)).save(old);
        assertEquals("编辑角色", old.getName());
        assertEquals(PermissionSource.ANNOTATION, old.getSource());
        assertTrue(old.getDescription().contains("启用角色"));
        verify(repo).deprecateAnnotationByCodesNotIn(Set.of("system:role:edit"));
    }

    @Test
    void manualRegistrationCannotOverwriteActiveRealAnnotationMetadata() {
        PermissionRepo repo = mock(PermissionRepo.class);
        Permission annotation = Permission.create("platform:ai:generate", "AI 生成页面", "平台能力", "真实端点说明", PermissionSource.ANNOTATION);
        when(repo.findByCode("platform:ai:generate")).thenReturn(Optional.of(annotation));
        new PermissionDomainService(repo).upsertManualPermissions(List.of(
                new PermissionMeta("platform:ai:generate", "某工具名称", "工具分类", "工具说明")));
        assertEquals("AI 生成页面", annotation.getName());
        assertEquals(PermissionSource.ANNOTATION, annotation.getSource());
        verify(repo, never()).save(any());
    }

    @Test
    void realManualPluginMetadataStillRegistersAndReactivatesWhenNoAnnotationOwnsIt() {
        PermissionRepo repo = mock(PermissionRepo.class);
        Permission manual = Permission.create("plugin:test:view", "旧名称", "插件", "旧说明", PermissionSource.MANUAL);
        manual.deprecate();
        when(repo.findByCode("plugin:test:view")).thenReturn(Optional.of(manual));
        new PermissionDomainService(repo).upsertManualPermissions(List.of(
                new PermissionMeta("plugin:test:view", "查看插件信息", "插件", "实际说明")));
        assertEquals("查看插件信息", manual.getName());
        assertEquals(PermissionSource.MANUAL, manual.getSource());
        verify(repo).save(manual);
    }
}
