package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.dto.PluginMessagingConnectionDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMessagingGroupDTO;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingConnection;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingGroup;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginMessagingCatalogAppService {

    private final CapabilityAppService capabilityAppService;
    private final PluginMessagingService pluginMessagingService;

    public List<PluginMessagingConnectionDTO> connections() {
        if (!capabilityAppService.enabled("milky")) {
            return List.of();
        }
        return pluginMessagingService.connections().stream()
                .map(PluginMessagingCatalogAppService::toConnection)
                .toList();
    }

    public List<PluginMessagingGroupDTO> groups(String connectionId) {
        if (!capabilityAppService.enabled("milky") || !StringUtils.hasText(connectionId)) {
            return List.of();
        }
        return pluginMessagingService.groups(connectionId.trim()).stream()
                .map(PluginMessagingCatalogAppService::toGroup)
                .toList();
    }

    private static PluginMessagingConnectionDTO toConnection(PluginMessagingConnection connection) {
        return PluginMessagingConnectionDTO.builder()
                .id(connection.id())
                .name(connection.name())
                .platform(connection.platform())
                .userId(connection.userId())
                .protocol(connection.protocol())
                .build();
    }

    private static PluginMessagingGroupDTO toGroup(PluginMessagingGroup group) {
        return PluginMessagingGroupDTO.builder()
                .id(group.id())
                .name(group.name())
                .build();
    }
}
