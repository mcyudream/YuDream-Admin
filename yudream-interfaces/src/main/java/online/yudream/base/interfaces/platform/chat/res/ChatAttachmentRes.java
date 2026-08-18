package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;

@Builder
public record ChatAttachmentRes(
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
