package online.yudream.base.plugin.spi.system.memory;

public record PluginSemanticMemoryQuery(String namespace, String text, String providerCode,
                                        String modelCode, int limit) {
    public PluginSemanticMemoryQuery {
        limit = Math.clamp(limit, 1, 30);
    }
}
