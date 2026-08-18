package online.yudream.base.application.platform.chat.dto;

public record ChatAttachmentDTO(
        String fileId,
        String fileName,
        String contentType,
        Long size,
        String kind,
        String url,
        String extractedText,
        String dataUrl
) {
}
