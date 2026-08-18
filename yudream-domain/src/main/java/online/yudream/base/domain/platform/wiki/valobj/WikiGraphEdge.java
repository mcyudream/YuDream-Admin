package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 知识图谱边（四信号关联度模型加权）。
 *
 * @param source 源节点 ID
 * @param target 目标节点 ID
 * @param weight 关联权重
 * @param signal 主导信号：direct_link / source_overlap / adamic_adar / type_affinity
 */
public record WikiGraphEdge(
        String source,
        String target,
        double weight,
        String signal
) {
}
