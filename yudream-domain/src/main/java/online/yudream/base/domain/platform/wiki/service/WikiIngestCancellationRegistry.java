package online.yudream.base.domain.platform.wiki.service;

/**
 * 摄入任务取消标记端口：用于在运行中的任务之间传递“已取消”信号。
 */
public interface WikiIngestCancellationRegistry {

    void markCancelled(Long taskId);

    boolean isCancelled(Long taskId);

    void clear(Long taskId);
}
