package online.yudream.base.application.platform.feishu.query;

/**
 * 单个文档查询（元信息 / 纯文本内容）。
 */
public record FeishuDocumentQuery(String documentId, Integer lang) {
}
