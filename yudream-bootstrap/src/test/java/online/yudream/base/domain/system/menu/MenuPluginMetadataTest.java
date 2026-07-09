package online.yudream.base.domain.system.menu;

import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import online.yudream.base.infra.system.menu.mapper.MenuInfraMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPluginMetadataTest {

    @Test
    void pluginOwnershipDoesNotDependOnParent() {
        Menu menu = Menu.builder()
                .code("plugin:wallet:home")
                .parentCode("system:dashboard")
                .source(MenuSource.PLUGIN)
                .pluginCode("yudream-wallet")
                .pluginRegistrationKey("route:yudreamWallet:platform-plugin-yudream-wallet")
                .runtimeAvailable(false)
                .build();

        assertThat(menu.isPluginMenu()).isTrue();
        assertThat(menu.isAvailableForRuntime()).isFalse();
        assertThat(menu.getRuntimeAvailable()).isFalse();
    }

    @Test
    void pluginMetadataRoundTripsThroughInfrastructureMapper() {
        Menu menu = Menu.builder()
                .code("plugin:wallet:home")
                .source(MenuSource.PLUGIN)
                .pluginCode("yudream-wallet")
                .pluginModuleName("yudreamWallet")
                .pluginRegistrationKey("route:yudreamWallet:platform-plugin-yudream-wallet")
                .runtimeAvailable(true)
                .build();

        MenuDO dataObj = MenuInfraMapper.toDataObj(menu);
        Menu restored = MenuInfraMapper.toDomain(dataObj);

        assertThat(restored.getSource()).isEqualTo(MenuSource.PLUGIN);
        assertThat(restored.getPluginCode()).isEqualTo("yudream-wallet");
        assertThat(restored.getPluginModuleName()).isEqualTo("yudreamWallet");
        assertThat(restored.getPluginRegistrationKey())
                .isEqualTo("route:yudreamWallet:platform-plugin-yudream-wallet");
        assertThat(restored.getRuntimeAvailable()).isTrue();
    }

    @Test
    void legacyMenuWithoutSourceDefaultsToSystem() {
        MenuDO dataObj = new MenuDO();
        dataObj.setCode("system:dashboard");

        Menu restored = MenuInfraMapper.toDomain(dataObj);

        assertThat(restored.getSource()).isEqualTo(MenuSource.SYSTEM);
        assertThat(restored.isPluginMenu()).isFalse();
        assertThat(restored.isAvailableForRuntime()).isTrue();
    }
}
