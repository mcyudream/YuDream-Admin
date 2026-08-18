package online.yudream.base.domain.platform.wiki.valobj;

import java.util.List;

/**
 * 图谱洞察：惊奇连接 / 孤立页面 / 稀疏社区 / 桥接节点。
 *
 * @param kind         SURPRISING_CONNECTION / ORPHAN / SPARSE_COMMUNITY / BRIDGE
 * @param title        洞察标题
 * @param description  描述
 * @param nodeIds      相关节点 ID（用于图谱高亮）
 * @param searchQueries 触发深度研究的预生成搜索查询
 */
public record WikiGraphInsight(
        String kind,
        String title,
        String description,
        List<String> nodeIds,
        List<String> searchQueries
) {
    public static final String KIND_SURPRISING = "SURPRISING_CONNECTION";
    public static final String KIND_ORPHAN = "ORPHAN";
    public static final String KIND_SPARSE_COMMUNITY = "SPARSE_COMMUNITY";
    public static final String KIND_BRIDGE = "BRIDGE";

    public WikiGraphInsight {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        searchQueries = searchQueries == null ? List.of() : List.copyOf(searchQueries);
    }
}
