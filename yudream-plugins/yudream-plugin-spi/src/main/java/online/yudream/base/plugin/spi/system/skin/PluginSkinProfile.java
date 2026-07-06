package online.yudream.base.plugin.spi.system.skin;

public record PluginSkinProfile(
        String uuid,
        String name,
        String ownerId,
        PluginSkinTexture skin,
        PluginSkinTexture cape,
        Long lastModified
) {
}
