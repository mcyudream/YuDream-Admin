package online.yudream.base.domain.platform.wiki.valobj;

import java.time.LocalDateTime;

/**
 * 知识库列表卡片的节点聚合事实。
 * <p>
 * 只读聚合：不加载节点正文，最近更新时间取节点 updateTime 的最大值，空库为 null。
 * 入口页卡片用它补充「页面数 / 目录数 / 未发布数 / 最近更新」，不再只有名称与描述。
 */
public record WikiSpaceStats(int pageCount, int directoryCount, int unpublishedPageCount, LocalDateTime lastUpdatedAt) {

    public static final WikiSpaceStats EMPTY = new WikiSpaceStats(0, 0, 0, null);
}
