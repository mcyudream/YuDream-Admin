package online.yudream.base.domain.platform.feishu.valobj;

/**
 * 飞书知识库空间条目（wiki/v2/spaces）。
 */
public record FeishuWikiSpace(
        String spaceId,
        String name,
        String description,
        String spaceType,
        String visibility,
        String openSharing
) {
}
