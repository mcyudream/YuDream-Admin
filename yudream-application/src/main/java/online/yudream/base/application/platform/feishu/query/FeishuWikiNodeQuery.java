package online.yudream.base.application.platform.feishu.query;

/**
 * 知识库节点列表查询。
 *
 * @param spaceId         知识空间 ID（必填）
 * @param parentNodeToken 父节点 token；空表示根级
 * @param pageToken       分页游标；空表示第一页
 * @param pageSize        期望条数，网关钳制到飞书上限 50
 */
public record FeishuWikiNodeQuery(String spaceId, String parentNodeToken, String pageToken, int pageSize) {
}
