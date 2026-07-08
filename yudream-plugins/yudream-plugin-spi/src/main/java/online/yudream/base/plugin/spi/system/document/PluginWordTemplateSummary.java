package online.yudream.base.plugin.spi.system.document;

public record PluginWordTemplateSummary(
        Long id,
        String code,
        String name,
        String originalFilename,
        long updatedAt
) {
}
