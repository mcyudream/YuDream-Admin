package online.yudream.base.application.system.menu;

import online.yudream.base.application.system.menu.cmd.MenuImportCmd;
import online.yudream.base.application.system.menu.dto.MenuImportDTO;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuAppServiceImportTest {

    private static final long SOURCE_ORDER_ID = 100L;

    private static final long UNKNOWN_SOURCE_ID = 200L;

    private static final long PLUGIN_MENU_ID = 555L;

    @Mock
    private MenuDomainService menuDomainService;
    @Mock
    private MenuRepo menuRepo;
    @Mock
    private CapabilityModuleRepo capabilityModuleRepo;

    private MenuAppService service;

    @BeforeEach
    void setUp() {
        service = new MenuAppService(menuDomainService, menuRepo, capabilityModuleRepo);
    }

    @Test
    void importBySourceIdMatchesSameCodeAndUpdatesInPlace() {
        Menu parent = systemMenu(1L, "system:tools", null, MenuNodeType.CATEGORY);
        Menu target = systemMenu(SOURCE_ORDER_ID, "system:report", "system:tools", MenuNodeType.MENU);
        when(menuRepo.findById(SOURCE_ORDER_ID)).thenReturn(Optional.of(target));
        when(menuRepo.findByCode("system:tools")).thenReturn(Optional.of(parent));
        when(menuRepo.findAll()).thenReturn(List.of(parent, target));

        MenuImportCmd cmd = importCmd("system:report", SOURCE_ORDER_ID);
        MenuImportDTO result = service.importMenu(cmd);

        assertThat(result.getCode()).isEqualTo("system:report");
        assertThat(result.getCreated()).isFalse();
        assertThat(result.getMatched()).isTrue();
        // 稳定内部 id 与归属保持：code、id、SYSTEM 来源不因模板导入改变
        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuDomainService).syncMenu(captor.capture());
        assertThat(captor.getValue()).isSameAs(target);
        assertThat(target.getId()).isEqualTo(SOURCE_ORDER_ID);
        assertThat(target.getCode()).isEqualTo("system:report");
        assertThat(target.getSource()).isEqualTo(MenuSource.SYSTEM);
        assertThat(target.getPluginCode()).isNull();
        assertThat(target.getPluginRegistrationKey()).isNull();
        // 受控字段按命令写入
        assertThat(target.getName()).isEqualTo("导入报表");
        assertThat(target.getType()).isEqualTo(MenuNodeType.MENU);
        assertThat(target.getParentCode()).isEqualTo("system:tools");
        assertThat(target.getModule()).isEqualTo("system");
        assertThat(target.getIcon()).isEqualTo("import-icon");
        assertThat(target.getPath()).isEqualTo("/reports");
        assertThat(target.getComponent()).isEqualTo("Report");
        assertThat(target.getLink()).isEqualTo("https://example.test/report");
        assertThat(target.getSort()).isEqualTo(9);
        assertThat(target.getVisible()).isFalse();
        assertThat(target.getPermission()).isEqualTo("system:report:custom");
        // status/visible 经受控命令回写
        assertThat(target.getStatus()).isEqualTo(MenuStatus.DISABLED);
    }

    @Test
    void importFallsBackToCodeWhenSourceIdUnknown() {
        Menu target = systemMenu(SOURCE_ORDER_ID + 5, "system:report", null, MenuNodeType.MENU);
        when(menuRepo.findById(UNKNOWN_SOURCE_ID)).thenReturn(Optional.empty());
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.of(target));

        MenuImportCmd cmd = importCmd("system:report", UNKNOWN_SOURCE_ID);
        cmd.setParentCode(null);
        cmd.setStatus(null);

        MenuImportDTO result = service.importMenu(cmd);

        assertThat(result.getMatched()).isTrue();
        assertThat(result.getCreated()).isFalse();
        assertThat(target.getId()).isEqualTo(SOURCE_ORDER_ID + 5);
        assertThat(target.getName()).isEqualTo("导入报表");
        assertThat(target.getParentCode()).isNull();
        // status 未指定时保持既有启用状态
        assertThat(target.getStatus()).isEqualTo(MenuStatus.ACTIVE);
    }

    @Test
    void importFallsBackToCodeTargetWhenSourceIdResolvesDifferentCode() {
        Menu other = systemMenu(300L, "system:other", null, MenuNodeType.MENU);
        Menu codeTarget = systemMenu(400L, "system:report", null, MenuNodeType.MENU);
        when(menuRepo.findById(300L)).thenReturn(Optional.of(other));
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.of(codeTarget));

        MenuImportCmd cmd = importCmd("system:report", 300L);
        cmd.setParentCode(null);
        cmd.setStatus(null);

        MenuImportDTO result = service.importMenu(cmd);

        // ID 撞到无关记录时忽略该命中，按业务编码回退更新 code 目标
        assertThat(result.getCode()).isEqualTo("system:report");
        assertThat(result.getMatched()).isTrue();
        assertThat(result.getCreated()).isFalse();
        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuDomainService).syncMenu(captor.capture());
        assertThat(captor.getValue()).isSameAs(codeTarget);
        assertThat(codeTarget.getId()).isEqualTo(400L);
        assertThat(codeTarget.getName()).isEqualTo("导入报表");
        // sourceId 指向的无关记录保持原样
        assertThat(other.getId()).isEqualTo(300L);
        assertThat(other.getName()).isEqualTo("system:other");
    }

    @Test
    void importRejectsWhenSourceIdResolvesDifferentCodeAndNoCodeTarget() {
        Menu other = systemMenu(300L, "system:other", null, MenuNodeType.MENU);
        when(menuRepo.findById(300L)).thenReturn(Optional.of(other));
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.empty());

        // sourceId 命中其他菜单且 code 也无目标：无法确定写入目标，安全失败，
        // 绝不改写 sourceId 指向的无关记录
        assertThatThrownBy(() -> service.importMenu(importCmd("system:report", 300L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法匹配");

        verify(menuDomainService, never()).syncMenu(any(Menu.class));
        assertThat(other.getName()).isEqualTo("system:other");
    }

    @Test
    void importRejectsPluginCodeTargetFoundAfterSourceIdMismatch() {
        Menu other = systemMenu(300L, "system:other", null, MenuNodeType.MENU);
        Menu pluginCodeTarget = pluginMenu(PLUGIN_MENU_ID, "system:report");
        when(menuRepo.findById(300L)).thenReturn(Optional.of(other));
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.of(pluginCodeTarget));

        assertThatThrownBy(() -> service.importMenu(importCmd("system:report", 300L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件菜单");

        verify(menuDomainService, never()).syncMenu(any(Menu.class));
    }

    @Test
    void importRejectsPluginMenuMatchedByCode() {
        Menu pluginMenu = pluginMenu(PLUGIN_MENU_ID, "plugin:wallet:home");
        when(menuRepo.findByCode("plugin:wallet:home")).thenReturn(Optional.of(pluginMenu));

        assertThatThrownBy(() -> service.importMenu(importCmd("plugin:wallet:home", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件菜单");

        verifyNoInteractions(menuDomainService);
    }

    @Test
    void importRejectsPluginMenuMatchedBySourceId() {
        Menu pluginMenu = pluginMenu(PLUGIN_MENU_ID, "plugin:wallet:home");
        when(menuRepo.findById(PLUGIN_MENU_ID)).thenReturn(Optional.of(pluginMenu));

        assertThatThrownBy(() -> service.importMenu(importCmd("plugin:wallet:home", PLUGIN_MENU_ID)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件菜单");

        verifyNoInteractions(menuDomainService);
    }

    @Test
    void importCreatesNewSystemMenuWithControlledDefaults() {
        Menu parent = systemMenu(1L, "system:tools", null, MenuNodeType.CATEGORY);
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.empty());
        when(menuRepo.findByCode("system:tools")).thenReturn(Optional.of(parent));

        MenuImportCmd cmd = importCmd("system:report", null);
        cmd.setSort(null);
        cmd.setVisible(null);
        cmd.setStatus(null);

        MenuImportDTO result = service.importMenu(cmd);

        assertThat(result.getCode()).isEqualTo("system:report");
        assertThat(result.getCreated()).isTrue();
        assertThat(result.getMatched()).isFalse();
        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuDomainService).syncMenu(captor.capture());
        Menu created = captor.getValue();
        assertThat(created.getId()).isNull();
        assertThat(created.getCode()).isEqualTo("system:report");
        assertThat(created.getSource()).isEqualTo(MenuSource.SYSTEM);
        assertThat(created.getPluginCode()).isNull();
        assertThat(created.getSort()).isZero();
        assertThat(created.getVisible()).isTrue();
        assertThat(created.getStatus()).isEqualTo(MenuStatus.ACTIVE);
        assertThat(created.getPath()).isEqualTo("/reports");
    }

    @Test
    void importCreatesNewMenuWithRequestedDisabledStatus() {
        when(menuRepo.findByCode("system:orphan-report")).thenReturn(Optional.empty());

        MenuImportCmd cmd = importCmd("system:orphan-report", null);
        cmd.setParentCode(null);
        cmd.setStatus(MenuStatus.DISABLED);

        service.importMenu(cmd);

        ArgumentCaptor<Menu> captor = ArgumentCaptor.forClass(Menu.class);
        verify(menuDomainService).syncMenu(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MenuStatus.DISABLED);
    }

    @Test
    void importCannotDisableMenuWithActiveChildren() {
        Menu target = systemMenu(SOURCE_ORDER_ID, "system:parent", null, MenuNodeType.CATEGORY);
        Menu activeChild = systemMenu(2L, "system:parent:child", "system:parent", MenuNodeType.MENU);
        when(menuRepo.findById(SOURCE_ORDER_ID)).thenReturn(Optional.of(target));
        when(menuRepo.findAll()).thenReturn(List.of(target, activeChild));

        MenuImportCmd cmd = importCmd("system:parent", SOURCE_ORDER_ID);
        cmd.setParentCode(null);

        assertThatThrownBy(() -> service.importMenu(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能停用");

        verify(menuDomainService, never()).syncMenu(any(Menu.class));
        assertThat(target.getStatus()).isEqualTo(MenuStatus.ACTIVE);
    }

    @Test
    void importRequiresCodeAndTypeEvenForBatchBackedCallers() {
        assertThatThrownBy(() -> service.importMenu(importCmd(" ", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("编码");

        MenuImportCmd noType = importCmd("system:report", null);
        noType.setType(null);
        assertThatThrownBy(() -> service.importMenu(noType))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类型");

        verifyNoInteractions(menuRepo);
        verifyNoInteractions(menuDomainService);
    }

    @Test
    void syncPermissionCatalogRebuildsFromAllMenus() {
        Menu first = systemMenu(1L, "system:report", null, MenuNodeType.MENU);
        Menu second = systemMenu(2L, "system:orphan", null, MenuNodeType.MENU);
        List<Menu> menus = List.of(first, second);
        when(menuRepo.findAll()).thenReturn(menus);
        when(menuDomainService.syncPermissionCatalog(menus)).thenReturn(2);

        int synced = service.syncPermissionCatalog();

        assertThat(synced).isEqualTo(2);
        verify(menuDomainService).syncPermissionCatalog(menus);
    }

    @Test
    void importRejectsUnsafeLink() {
        when(menuRepo.findByCode("system:report")).thenReturn(Optional.empty());

        MenuImportCmd cmd = importCmd("system:report", null);
        cmd.setLink("javascript:alert(1)");
        cmd.setParentCode(null);

        assertThatThrownBy(() -> service.importMenu(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("http");

        verify(menuDomainService, never()).syncMenu(any(Menu.class));
    }

    private Menu systemMenu(long id, String code, String parentCode, MenuNodeType type) {
        return Menu.builder()
                .id(id)
                .code(code)
                .name(code)
                .type(type)
                .parentCode(parentCode)
                .path("/" + code.replace(':', '/'))
                .component(type == MenuNodeType.MENU ? "SystemPage" : null)
                .sort(1)
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .source(MenuSource.SYSTEM)
                .build();
    }

    private Menu pluginMenu(long id, String code) {
        return Menu.builder()
                .id(id)
                .code(code)
                .name("Wallet")
                .type(MenuNodeType.MENU)
                .path("/wallet")
                .component("WalletHome")
                .sort(2)
                .visible(true)
                .status(MenuStatus.ACTIVE)
                .source(MenuSource.PLUGIN)
                .pluginCode("wallet")
                .pluginModuleName("wallet-admin")
                .pluginRegistrationKey("route:wallet-admin:home")
                .runtimeAvailable(true)
                .build();
    }

    private MenuImportCmd importCmd(String code, Long sourceId) {
        MenuImportCmd cmd = new MenuImportCmd();
        cmd.setSourceId(sourceId);
        cmd.setCode(code);
        cmd.setName("导入报表");
        cmd.setType(MenuNodeType.MENU);
        cmd.setParentCode("system:tools");
        cmd.setModule("system");
        cmd.setIcon("import-icon");
        cmd.setPath("/reports");
        cmd.setComponent("Report");
        cmd.setLink("https://example.test/report");
        cmd.setSort(9);
        cmd.setVisible(false);
        cmd.setPermission("system:report:custom");
        cmd.setStatus(MenuStatus.DISABLED);
        return cmd;
    }
}
