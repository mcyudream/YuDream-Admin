package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofMappingDTO(
        String id,
        String serverId,
        String playerId,
        String playerName,
        String studentNo,
        long createdAt,
        long updatedAt
) {
}
