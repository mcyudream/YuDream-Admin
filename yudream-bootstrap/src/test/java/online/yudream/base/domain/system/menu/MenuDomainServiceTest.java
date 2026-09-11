package online.yudream.base.domain.system.menu;

import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuDomainServiceTest {

    @Mock
    private MenuRepo menuRepo;

    @Mock
    private PermissionRepo permissionRepo;

    @Test
    void missingOnlyDoesNotWriteMenuOrPermissionWhenMenuCodeAlreadyExists() {
        Menu declaration = Menu.builder()
                .code("plugin:yudream-wallet:module:yudreamWallet")
                .name("声明名称")
                .type(MenuNodeType.LAYOUT)
                .component("Layout")
                .permission("plugin:yudream-wallet:manage")
                .build();
        when(menuRepo.existsByCode(declaration.getCode())).thenReturn(true);
        MenuDomainService service = new MenuDomainService(menuRepo, permissionRepo);

        List<Menu> synced = service.syncMenus(List.of(declaration), SeedSyncMode.MISSING_ONLY);

        assertThat(synced).isEmpty();
        verify(menuRepo, never()).save(declaration);
        verifyNoInteractions(permissionRepo);
    }

    @Test
    void deleteMenuRemovesMenuAndItsPermission() {
        Menu menu = Menu.builder()
                .code("system:report")
                .name("报表")
                .permission("system:report:view")
                .build();
        when(menuRepo.findByCode(menu.getCode())).thenReturn(Optional.of(menu));
        MenuDomainService service = new MenuDomainService(menuRepo, permissionRepo);

        service.deleteMenu(menu.getCode());

        verify(menuRepo).deleteByCode(menu.getCode());
        verify(permissionRepo).deleteByCode("system:report:view");
    }
}
