package online.yudream.base.plugin.activityproof.application.cmd;

public record ActivityProofMappingSaveCmd(
        String serverId,
        String playerId,
        String playerName,
        String studentNo
) {
}
