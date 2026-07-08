package online.yudream.base.plugin.spi.system.minecraft;

import java.util.List;
import java.util.Optional;

public interface PluginMinecraftService {

    List<PluginMinecraftServer> minecraftServers(boolean includeDisabled);

    Optional<PluginMinecraftServer> minecraftServer(String serverId);

    List<PluginMinecraftPlayerActivity> minecraftPlayerActivities(String serverId, int page, int size);
}
