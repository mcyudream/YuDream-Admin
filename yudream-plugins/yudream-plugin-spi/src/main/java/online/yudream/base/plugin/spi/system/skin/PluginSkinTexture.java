package online.yudream.base.plugin.spi.system.skin;

public record PluginSkinTexture(
        String hash,
        String name,
        String type,
        String model,
        String contentType,
        Long size,
        String objectKey,
        Boolean publicAccess
) {
}
