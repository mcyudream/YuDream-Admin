package online.yudream.base.plugin.spi.system.minecraft;

import java.util.List;
import java.util.Optional;

public interface PluginMinecraftService {

    List<PluginMinecraftServer> minecraftServers(boolean includeDisabled);

    Optional<PluginMinecraftServer> minecraftServer(String serverId);

    List<PluginMinecraftPlayerActivity> minecraftPlayerActivities(String serverId, int page, int size);

    /**
     * Returns a player's activity calculated only from the requested time window.
     * Implementations without retained activity events may return an empty result.
     */
    default Optional<PluginMinecraftOnlineWindow> minecraftOnlineWindow(String serverId, String playerId,
                                                                         long windowStart, long windowEnd) {
        return Optional.empty();
    }
}
