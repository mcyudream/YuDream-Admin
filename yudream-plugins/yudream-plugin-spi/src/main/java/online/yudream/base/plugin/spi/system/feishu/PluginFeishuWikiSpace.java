package online.yudream.base.plugin.spi.system.feishu;

/**
 * 飞书知识库空间条目（wiki/v2/spaces，只读视图）。
 *
 * @param spaceId     空间 ID
 * @param name        空间名称
 * @param description 空间描述；无描述时为空串
 * @param spaceType   空间类型（飞书原样字符串）；未知为空串
 * @param visibility  可见性（飞书原样字符串）；未知为空串
 * @param openSharing 开放共享标识（飞书原样字符串）；未知为空串
 */
public record PluginFeishuWikiSpace(
        String spaceId,
        String name,
        String description,
        String spaceType,
        String visibility,
        String openSharing
) {
    public PluginFeishuWikiSpace {
        spaceId = spaceId == null ? "" : spaceId;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        spaceType = spaceType == null ? "" : spaceType;
        visibility = visibility == null ? "" : visibility;
        openSharing = openSharing == null ? "" : openSharing;
    }
}
