package online.yudream.base.plugin.spi.system.ai;

import java.util.Map;
import java.util.Set;

public record PluginAiToolDescriptor(
        String name,
        String title,
        String description,
        String permissionCode,
        PluginAiToolRisk risk,
        boolean requiresConfirmation,
        Set<String> allowedTriggers,
        Map<String, Object> inputSchema
) {
    public PluginAiToolDescriptor {
        risk = risk == null ? PluginAiToolRisk.READ : risk;
        allowedTriggers = allowedTriggers == null ? Set.of("MENTION") : Set.copyOf(allowedTriggers);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
