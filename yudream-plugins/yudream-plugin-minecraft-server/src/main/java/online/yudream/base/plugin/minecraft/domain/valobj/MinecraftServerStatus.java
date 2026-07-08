package online.yudream.base.plugin.minecraft.domain.valobj;

import java.util.Comparator;
import java.util.List;

public record MinecraftServerStatus(
        String serverId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        List<MinecraftEndpointStatus> endpoints,
        long checkedAt
) {

    public MinecraftServerStatus {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }

    public static MinecraftServerStatus from(String serverId, List<MinecraftEndpointStatus> endpointStatuses) {
        List<MinecraftEndpointStatus> items = endpointStatuses == null ? List.of() : endpointStatuses;
        int online = items.stream().filter(item -> "ONLINE".equals(item.status()))
                .mapToInt(MinecraftEndpointStatus::onlinePlayers)
                .max()
                .orElse(0);
        int max = items.stream().filter(item -> "ONLINE".equals(item.status()))
                .mapToInt(MinecraftEndpointStatus::maxPlayers)
                .max()
                .orElse(0);
        long checkedAt = items.stream()
                .map(MinecraftEndpointStatus::checkedAt)
                .max(Comparator.naturalOrder())
                .orElse(System.currentTimeMillis());
        String status = items.stream().anyMatch(item -> "ONLINE".equals(item.status())) ? "ONLINE" : "OFFLINE";
        return new MinecraftServerStatus(serverId, status, online, max, items, checkedAt);
    }
}
