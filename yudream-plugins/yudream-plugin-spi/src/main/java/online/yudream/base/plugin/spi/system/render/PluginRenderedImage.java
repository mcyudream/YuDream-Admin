package online.yudream.base.plugin.spi.system.render;

public record PluginRenderedImage(String contentType, byte[] content, int width, int height) {
    public PluginRenderedImage {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() { return content.clone(); }
}
