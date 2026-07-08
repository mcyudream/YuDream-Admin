package online.yudream.base.plugin.minecraft.domain.valobj;

public record MinecraftStatusSnapshot(
        String id,
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        long checkedAt
) {

    public static MinecraftStatusSnapshot from(MinecraftServerStatus status) {
        return new MinecraftStatusSnapshot(
                status.serverId() + ":" + status.checkedAt(),
                status.serverId(),
                status.status(),
                status.onlinePlayers(),
                status.maxPlayers(),
                status.checkedAt()
        );
    }
}
