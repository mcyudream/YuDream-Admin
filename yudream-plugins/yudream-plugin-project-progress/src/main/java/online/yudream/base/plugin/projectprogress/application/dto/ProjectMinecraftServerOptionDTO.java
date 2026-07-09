package online.yudream.base.plugin.projectprogress.application.dto;

public record ProjectMinecraftServerOptionDTO(
        String id,
        String name,
        boolean enabled,
        String currentSeasonId,
        String currentSeasonName
) {
}
