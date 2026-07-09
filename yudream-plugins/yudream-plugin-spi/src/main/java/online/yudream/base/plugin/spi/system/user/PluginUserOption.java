package online.yudream.base.plugin.spi.system.user;

import java.util.List;

public record PluginUserOption(
        String id,
        String username,
        String nickname,
        String email,
        String avatar,
        String status,
        List<String> deptIds,
        List<String> deptNames
) {
}
