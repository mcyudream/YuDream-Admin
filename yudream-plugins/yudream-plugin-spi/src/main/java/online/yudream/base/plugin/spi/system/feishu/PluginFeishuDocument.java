package online.yudream.base.plugin.spi.system.feishu;

/**
 * 飞书新版文档元信息（docx/v1/documents/:document_id，只读视图）。
 *
 * @param documentId 文档 ID
 * @param revisionId 当前版本号（字符串保留精度）；未知为空串
 * @param title      文档标题
 */
public record PluginFeishuDocument(
        String documentId,
        String revisionId,
        String title
) {
    public PluginFeishuDocument {
        documentId = documentId == null ? "" : documentId;
        revisionId = revisionId == null ? "" : revisionId;
        title = title == null ? "" : title;
    }
}
