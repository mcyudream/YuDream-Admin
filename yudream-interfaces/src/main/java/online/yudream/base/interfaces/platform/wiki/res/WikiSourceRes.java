package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record WikiSourceRes(
        String id,
        String spaceId,
        String folderPath,
        String fileName,
        String title,
        String kind,
        String url,
        String mimeType,
        String format,
        String fileObjectId,
        String contentHash,
        String extractedText,
        String extractionStatus,
        String extractionError,
        List<Image> images,
        String ingestStatus,
        String ingestError,
        LocalDateTime ingestedAt,
        int sort,
        String fileUrl
) {
    @Builder
    public record Image(
            String fileObjectId,
            int pageNumber,
            int sequence,
            String caption,
            String captionStatus,
            String captionProviderCode,
            String captionModelCode,
            int width,
            int height,
            String contentType,
            String url
    ) {
    }
}
