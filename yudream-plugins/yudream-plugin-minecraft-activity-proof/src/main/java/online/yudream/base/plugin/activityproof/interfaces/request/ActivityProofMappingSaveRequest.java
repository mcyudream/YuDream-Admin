package online.yudream.base.plugin.activityproof.interfaces.request;

public record ActivityProofMappingSaveRequest(
        String serverId,
        String playerId,
        String playerName,
        String studentNo
) {
}
