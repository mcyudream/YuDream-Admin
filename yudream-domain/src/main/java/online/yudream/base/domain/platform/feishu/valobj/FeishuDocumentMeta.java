package online.yudream.base.domain.platform.feishu.valobj;

/**
 * 飞书新版文档元信息（docx/v1/documents/:document_id）。
 */
public record FeishuDocumentMeta(String documentId, String revisionId, String title) {
}
