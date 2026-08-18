package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 摄入队列的实时进度事件（通过 SSE 广播）。
 */
public record WikiIngestProgress(
        Long taskId,
        Long spaceId,
        Long sourceId,
        String phase,
        String message,
        int percent,
        boolean completed
) {
}
