package online.yudream.base.plugin.spi.system.user;

import java.util.List;

public record PluginDeptOption(
        String id,
        String name,
        String parentId,
        String status,
        List<PluginDeptOption> children
) {
}
