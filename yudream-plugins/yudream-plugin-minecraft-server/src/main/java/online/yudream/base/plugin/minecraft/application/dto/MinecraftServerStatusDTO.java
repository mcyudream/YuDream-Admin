package online.yudream.base.plugin.minecraft.application.dto;

import java.util.List;

public record MinecraftServerStatusDTO(
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        List<MinecraftEndpointStatusDTO> endpoints,
        long checkedAt
) {
}
