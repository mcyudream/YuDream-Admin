package online.yudream.base.domain.platform.feishu.valobj;

/**
 * 飞书知识库节点条目（wiki/v2/spaces/{space_id}/nodes）。
 * <p>
 * 全部标识保持飞书返回字符串原样（长 ID 不转数值），字段语义与飞书官方返回一致；
 * 上游未返回的文本字段为 null，宿主不做补空改写；{@code hasChild} 缺省按 false。
 */
public record FeishuWikiNode(
        String nodeToken,
        String objToken,
        String objType,
        String title,
        boolean hasChild,
        String parentNodeToken,
        String nodeType,
        String originNodeToken,
        String originSpaceId,
        String spaceId
) {
}
