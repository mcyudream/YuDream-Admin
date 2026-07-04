package online.yudream.base.plugin.spi.system.skin;

import java.util.List;

public record PluginSkinUser(
        String id,
        String email,
        String nickname,
        List<PluginSkinProfile> profiles
) {
    public PluginSkinUser {
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
    }
}
