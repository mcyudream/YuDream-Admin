package online.yudream.base.application.platform.render.dto;

public record RenderedImageDTO(String contentType, byte[] content, int width, int height) {
    public RenderedImageDTO {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
