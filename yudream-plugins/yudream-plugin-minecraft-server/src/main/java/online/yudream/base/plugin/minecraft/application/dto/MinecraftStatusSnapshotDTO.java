package online.yudream.base.plugin.minecraft.application.dto;

public record MinecraftStatusSnapshotDTO(
        String id,
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        long checkedAt
) {
}
