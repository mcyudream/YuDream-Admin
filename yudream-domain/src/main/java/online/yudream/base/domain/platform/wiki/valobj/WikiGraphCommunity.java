package online.yudream.base.domain.platform.wiki.valobj;

import java.util.List;

/**
 * Louvain 社区检测得到的知识聚类。
 *
 * @param id          社区 ID
 * @param label       核心节点标签（用于图例展示）
 * @param nodeIds     社区成员节点 ID
 * @param size        成员数
 * @param cohesion    内聚度（内部实际边 / 可能边）
 * @param lowCohesion 是否低内聚（< 0.15）
 */
public record WikiGraphCommunity(
        String id,
        String label,
        List<String> nodeIds,
        int size,
        double cohesion,
        boolean lowCohesion
) {
    public WikiGraphCommunity {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
    }
}
