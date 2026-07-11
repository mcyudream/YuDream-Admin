package online.yudream.base.plugin.spi.system.command;

import java.util.List;

public interface PluginCommandService {
    List<PluginCommandInfo> listAccessible(Long userId);
}
