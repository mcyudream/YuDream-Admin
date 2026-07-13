package online.yudream.base.plugin.spi.system.memory;

import java.util.List;

public record PluginSemanticMemoryStatus(boolean available, String message,
                                         List<PluginSemanticMemoryModelOption> models) {
    public PluginSemanticMemoryStatus {
        message = message == null ? "" : message;
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static PluginSemanticMemoryStatus unavailable(String message) {
        return new PluginSemanticMemoryStatus(false, message, List.of());
    }
}
