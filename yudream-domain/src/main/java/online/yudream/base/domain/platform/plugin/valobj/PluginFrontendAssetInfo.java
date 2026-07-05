package online.yudream.base.domain.platform.plugin.valobj;

public record PluginFrontendAssetInfo(
        String path,
        String contentType,
        byte[] body
) {
}
