package online.yudream.base.application.platform.wiki.dto;

/**
 * 公开知识库原文档目录项：已摄入的原始资料。
 */
public record WikiPublicDocumentDTO(
        String id,
        String title,
        String folderPath,
        String kind,
        String format
) {
}
