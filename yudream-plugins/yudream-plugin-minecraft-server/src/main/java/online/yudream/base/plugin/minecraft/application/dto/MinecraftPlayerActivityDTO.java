package online.yudream.base.plugin.minecraft.application.dto;

public record MinecraftPlayerActivityDTO(
        String serverId,
        String playerId,
        String playerName,
        boolean online,
        boolean afk,
        long totalOnlineMillis,
        long totalAfkMillis,
        Long currentOnlineSince,
        Long currentAfkSince,
        Long lastJoinedAt,
        Long lastQuitAt,
        long updatedAt
) {
}
