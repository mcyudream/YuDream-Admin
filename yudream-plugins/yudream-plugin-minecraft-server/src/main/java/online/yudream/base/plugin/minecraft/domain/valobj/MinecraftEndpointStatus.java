package online.yudream.base.plugin.minecraft.domain.valobj;

public record MinecraftEndpointStatus(
        String endpointId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        String versionName,
        Integer protocolId,
        Long ping,
        String motd,
        String errorMessage,
        long checkedAt
) {

    public static MinecraftEndpointStatus offline(String endpointId, String errorMessage) {
        return new MinecraftEndpointStatus(endpointId, "OFFLINE", 0, 0, null, null, null,
                null, errorMessage, System.currentTimeMillis());
    }

    public static MinecraftEndpointStatus online(String endpointId, int onlinePlayers, int maxPlayers, String versionName,
                                                 Integer protocolId, Long ping, String motd) {
        return new MinecraftEndpointStatus(endpointId, "ONLINE", onlinePlayers, maxPlayers, versionName,
                protocolId, ping, motd, null, System.currentTimeMillis());
    }
}
