package online.yudream.base.domain.platform.chat.valobj;

public record ChatAttachment(
        Long fileId,
        String fileName,
        String contentType,
        Long size,
        String kind,
        String url,
        String extractedText,
        String dataUrl
) {
}
