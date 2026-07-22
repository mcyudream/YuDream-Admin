package online.yudream.base.application.system.command;

import online.yudream.base.application.system.command.service.CommandManageAppService;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandManageAppServiceTest {

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;
    @Mock
    private SettingRepo settingRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private PluginQqBindingService pluginQqBindingService;

    @Test
    void listIncludesSystemMenuAliasesBeforePluginsAndKeepsPluginOrder() {
        PluginCommandInfo alpha = command("alpha", "alpha.status", "状态");
        PluginCommandInfo beta = command("beta", "beta.reload", "重载");
        PluginCommandInfo duplicateSystemMenu = command("SYSTEM", "system.menu", "菜单");
        when(pluginRuntimeGateway.commands()).thenReturn(List.of(alpha, beta, duplicateSystemMenu));
        CommandManageAppService service = new CommandManageAppService(
                pluginRuntimeGateway, settingRepo, userRepo, pluginQqBindingService);

        List<PluginCommandInfo> commands = service.list();

        assertThat(commands).extracting(PluginCommandInfo::pluginCode, PluginCommandInfo::code,
                        PluginCommandInfo::command, PluginCommandInfo::allowAnonymous)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SYSTEM", "system.menu", "菜单", true),
                        org.assertj.core.groups.Tuple.tuple("SYSTEM", "system.help", "帮助", true),
                        org.assertj.core.groups.Tuple.tuple("SYSTEM", "system.menu-commands", "菜单指令", true),
                        org.assertj.core.groups.Tuple.tuple("alpha", "alpha.status", "状态", true),
                        org.assertj.core.groups.Tuple.tuple("beta", "beta.reload", "重载", true));
        assertThat(commands).filteredOn(command -> command.pluginCode().equals("SYSTEM")
                        && command.code().equals("system.menu"))
                .singleElement()
                .satisfies(command -> {
                    assertThat(command.permission()).isNull();
                    assertThat(command.description()).isEqualTo("查看可用指令");
                });
        verify(pluginRuntimeGateway).commands();
    }

    private PluginCommandInfo command(String pluginCode, String code, String command) {
        return new PluginCommandInfo(pluginCode, code, command, command, null, command, true);
    }
}
