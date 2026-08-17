package online.yudream.base.application.platform.wiki.dto;

import java.util.List;

/**
 * 公开知识库原文档详情：原始资料正文与图片。
 */
public record WikiPublicDocumentDetailDTO(
        String id,
        String title,
        String folderPath,
        String kind,
        String format,
        String content,
        List<Image> images
) {
    public record Image(
            String url,
            String caption,
            int width,
            int height
    ) {
    }
}
