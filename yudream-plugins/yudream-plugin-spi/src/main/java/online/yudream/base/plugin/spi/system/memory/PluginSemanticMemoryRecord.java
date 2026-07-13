package online.yudream.base.plugin.spi.system.memory;

import java.util.Map;

public record PluginSemanticMemoryRecord(String namespace, String id, String content,
                                         String providerCode, String modelCode,
                                         Map<String, Object> metadata) {
    public PluginSemanticMemoryRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
