package online.yudream.base.plugin.minecraft.interfaces.res;

public record MinecraftStatusSnapshotRes(
        String id,
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        long checkedAt
) {
}
