package online.yudream.base.plugin.spi.system.feishu;

/**
 * 飞书知识库节点条目（wiki/v2/spaces/{space_id}/nodes，只读视图）。
 * <p>
 * 全部标识与时间文本保持飞书返回字符串原样（长 ID 不转数值）。
 *
 * @param nodeToken       节点 token
 * @param objToken        节点承载的云文档 token
 * @param objType         承载文档类型（飞书原样字符串）；未知为空串
 * @param title           节点标题；未知为空串
 * @param hasChild        是否存在子节点
 * @param parentNodeToken 父节点 token；根节点为空串
 * @param nodeType        节点类型（飞书原样字符串）；未知为空串
 * @param originNodeToken 快捷方式源节点 token；非快捷方式为空串
 * @param originSpaceId   快捷方式源知识空间 ID；非快捷方式为空串
 * @param spaceId         所属知识空间 ID
 */
public record PluginFeishuWikiNode(
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
    public PluginFeishuWikiNode {
        nodeToken = nodeToken == null ? "" : nodeToken;
        objToken = objToken == null ? "" : objToken;
        objType = objType == null ? "" : objType;
        title = title == null ? "" : title;
        parentNodeToken = parentNodeToken == null ? "" : parentNodeToken;
        nodeType = nodeType == null ? "" : nodeType;
        originNodeToken = originNodeToken == null ? "" : originNodeToken;
        originSpaceId = originSpaceId == null ? "" : originSpaceId;
        spaceId = spaceId == null ? "" : spaceId;
    }
}
