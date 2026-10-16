package online.yudream.base.application.platform.feishu.query;

/**
 * 文档块分页查询。
 */
public record FeishuDocumentBlockQuery(String documentId, String pageToken, int pageSize) {
}
