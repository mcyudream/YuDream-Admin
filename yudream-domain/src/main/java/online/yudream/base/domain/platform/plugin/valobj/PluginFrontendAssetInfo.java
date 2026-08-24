package online.yudream.base.domain.platform.plugin.valobj;

public record PluginFrontendAssetInfo(
        String path,
        String contentType,
        byte[] body,
        String etag,
        boolean immutable
) {
    public PluginFrontendAssetInfo(String path, String contentType, byte[] body) {
        this(path, contentType, body, "", false);
    }
}
