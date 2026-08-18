package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 知识图谱节点（基于 wikilink / sources / 类型的页面关系图）。
 *
 * @param id       节点 ID（WikiNode id 的字符串形式，遵守长 ID 全程字符串约定）
 * @param title     页面标题
 * @param type      页面类型
 * @param degree    度数
 * @param community 所属社区 ID
 */
public record WikiGraphNode(
        String id,
        String title,
        String type,
        int degree,
        String community
) {
}
