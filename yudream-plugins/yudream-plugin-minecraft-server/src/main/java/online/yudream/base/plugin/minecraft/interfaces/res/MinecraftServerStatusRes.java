package online.yudream.base.plugin.minecraft.interfaces.res;

import java.util.List;

public record MinecraftServerStatusRes(
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        List<MinecraftEndpointStatusRes> endpoints,
        long checkedAt
) {
}
