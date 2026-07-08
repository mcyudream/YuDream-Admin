package online.yudream.base.plugin.spi.system.minecraft;

public record PluginMinecraftServer(
        String id,
        String name,
        String descriptionMarkdown,
        boolean enabled,
        String currentSeasonId,
        String currentSeasonName,
        Long currentSeasonStartedAt,
        long createdAt,
        long updatedAt
) {
}
