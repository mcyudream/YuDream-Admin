package online.yudream.base.plugin.spi.system.document;

public record PluginRenderedDocument(
        byte[] content,
        String contentType
) {
}
