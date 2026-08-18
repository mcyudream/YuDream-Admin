package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 摄入队列任务状态。
 */
public enum WikiIngestTaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
