package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofServerDTO(
        String id,
        String name,
        boolean enabled,
        String currentSeasonName,
        Long currentSeasonStartedAt
) {
}
