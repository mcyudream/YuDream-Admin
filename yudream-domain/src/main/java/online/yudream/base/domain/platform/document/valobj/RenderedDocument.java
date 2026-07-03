package online.yudream.base.domain.platform.document.valobj;

public record RenderedDocument(
        byte[] content,
        String contentType
) {
}
