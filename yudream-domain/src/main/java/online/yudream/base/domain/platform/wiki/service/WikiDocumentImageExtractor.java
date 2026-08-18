package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import online.yudream.base.domain.platform.wiki.valobj.WikiExtractedImage;

import java.util.List;

/**
 * 文档图片抽取端口：从 PDF 等格式中提取内嵌图片（llm_wiki 的“抽图”能力）。
 */
public interface WikiDocumentImageExtractor {

    List<WikiExtractedImage> extractImages(DocumentSource source);
}
