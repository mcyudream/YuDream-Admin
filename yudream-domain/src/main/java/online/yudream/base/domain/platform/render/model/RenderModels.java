package online.yudream.base.domain.platform.render.model;

import java.util.Map;

public final class RenderModels {
    private RenderModels() {
    }

    public enum SourceType { HTML, MARKDOWN, URL }

    public record RenderRequest(SourceType sourceType, String content, Integer width, Integer maxHeight,
                                Boolean transparent, String format, Map<String, Object> options) {
        public RenderRequest {
            sourceType = sourceType == null ? SourceType.HTML : sourceType;
            options = options == null ? Map.of() : Map.copyOf(options);
        }
    }

    public record RenderedImage(String contentType, byte[] content, int width, int height) {
        public RenderedImage {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
