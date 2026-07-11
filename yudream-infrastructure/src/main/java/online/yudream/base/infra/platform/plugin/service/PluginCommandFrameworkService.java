package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.plugin.spi.system.command.PluginCommandInfo;
import online.yudream.base.plugin.spi.system.command.PluginCommandService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginCommandFrameworkService implements PluginCommandService {
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final PermissionAppService permissionAppService;

    @Override
    public List<PluginCommandInfo> listAccessible(Long userId) {
        List<String> permissions = userId == null ? List.of() : permissionAppService.getUserPermissions(userId);
        return pluginRuntimeGateway.commands().stream()
                .filter(command -> command.allowAnonymous() || userId != null)
                .filter(command -> command.permission() == null || command.permission().isBlank()
                        || permissions.contains("*") || permissions.contains(command.permission()))
                .map(command -> new PluginCommandInfo(command.pluginCode(), command.code(), command.command(),
                        command.name(), command.permission(), command.description(), command.allowAnonymous()))
                .toList();
    }
}
