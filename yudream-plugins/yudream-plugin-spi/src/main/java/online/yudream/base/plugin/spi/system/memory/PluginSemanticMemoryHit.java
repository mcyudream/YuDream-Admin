package online.yudream.base.plugin.spi.system.memory;

import java.util.Map;

public record PluginSemanticMemoryHit(String id, String content, double score,
                                      Map<String, Object> metadata) {
    public PluginSemanticMemoryHit {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
