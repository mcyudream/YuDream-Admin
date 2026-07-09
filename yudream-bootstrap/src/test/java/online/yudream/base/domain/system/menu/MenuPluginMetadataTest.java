package online.yudream.base.domain.system.menu;

import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
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
        assertThat(menu.isRuntimeAvailable()).isFalse();
    }
}
