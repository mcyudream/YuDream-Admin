package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public record PluginAiToolInfo(
        String pluginCode,
        String name,
        String title,
        String description,
        String permissionCode,
        String risk,
        boolean requiresConfirmation,
        List<String> allowedTriggers
) {
    public PluginAiToolInfo {
        allowedTriggers = allowedTriggers == null ? List.of() : List.copyOf(allowedTriggers);
    }
}
