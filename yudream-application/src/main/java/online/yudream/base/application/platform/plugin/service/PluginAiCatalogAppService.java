package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.dto.PluginAiAgentCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginAiModelCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginAiProviderCatalogDTO;
import online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiModelOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginAiCatalogAppService {

    private final CapabilityAppService capabilityAppService;
    private final PluginAiService pluginAiService;

    public List<PluginAiAgentCatalogDTO> agents() {
        if (!capabilityAppService.enabled("ai")) {
            return List.of();
        }
        return pluginAiService.agents().stream()
                .map(PluginAiCatalogAppService::toAgent)
                .toList();
    }

    public List<PluginAiProviderCatalogDTO> providers() {
        if (!capabilityAppService.enabled("ai")) {
            return List.of();
        }
        return pluginAiService.providers().stream()
                .map(PluginAiCatalogAppService::toProvider)
                .toList();
    }

    private static PluginAiAgentCatalogDTO toAgent(PluginAiAgentOption agent) {
        return PluginAiAgentCatalogDTO.builder()
                .code(agent.code())
                .name(agent.name())
                .description(agent.description())
                .build();
    }

    private static PluginAiProviderCatalogDTO toProvider(PluginAiProviderOption provider) {
        List<PluginAiModelCatalogDTO> models = provider.models() == null ? List.of() : provider.models().stream()
                .map(PluginAiCatalogAppService::toModel)
                .toList();
        return PluginAiProviderCatalogDTO.builder()
                .code(provider.code())
                .name(provider.name())
                .models(models)
                .build();
    }

    private static PluginAiModelCatalogDTO toModel(PluginAiModelOption model) {
        return PluginAiModelCatalogDTO.builder()
                .code(model.code())
                .name(model.name())
                .build();
    }
}
