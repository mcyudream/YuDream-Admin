package online.yudream.base.application.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.dto.PluginMarketSourceDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;

public class PluginMarketSourceAssembler {

    private PluginMarketSourceAssembler() {
    }

    public static PluginMarketSourceDTO toDTO(PluginMarketSource source, Integer pluginCount) {
        return PluginMarketSourceDTO.builder()
                .id(source.getId())
                .code(source.getCode())
                .name(source.getName())
                .rootUrl(source.getRootUrl())
                .tokenConfigured(source.getToken() != null && !source.getToken().isBlank())
                .enabled(source.enabled())
                .builtIn(source.builtIn())
                .sortOrder(source.getSortOrder())
                .syncStatus(source.getSyncStatus())
                .syncErrorMessage(source.getSyncErrorMessage())
                .syncedAt(source.getSyncedAt())
                .pluginCount(pluginCount)
                .build();
    }
}
