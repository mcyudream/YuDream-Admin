package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;

import java.time.LocalDateTime;

/**
 * 持久化摄入队列任务。串行执行、崩溃恢复、可取消/重试。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiIngestTask extends BaseDomain {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private Long spaceId;
    private Long sourceId;
    private WikiIngestTaskType taskType;
    private WikiIngestTaskStatus status;
    private int attempts;
    private int maxAttempts;
    private String errorMessage;
    private String phase;
    private int percent;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private long sortOrder;
    private String payloadJson;

    public static WikiIngestTask create(Long spaceId, Long sourceId, WikiIngestTaskType taskType,
                                        String payloadJson, long sortOrder) {
        return WikiIngestTask.builder()
                .spaceId(spaceId)
                .sourceId(sourceId)
                .taskType(taskType == null ? WikiIngestTaskType.INGEST : taskType)
                .status(WikiIngestTaskStatus.QUEUED)
                .attempts(0)
                .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                .phase("queued")
                .percent(0)
                .sortOrder(sortOrder)
                .payloadJson(payloadJson)
                .build();
    }

    public void start() {
        this.status = WikiIngestTaskStatus.RUNNING;
        this.attempts += 1;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
        this.phase = "running";
        this.percent = 0;
    }

    public void updateProgress(String phase, String message, int percent) {
        this.phase = phase == null ? this.phase : phase;
        this.errorMessage = message;
        this.percent = Math.clamp(percent, 0, 100);
    }

    public void succeed() {
        this.status = WikiIngestTaskStatus.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();
        this.phase = "done";
        this.percent = 100;
        this.errorMessage = null;
    }

    public void fail(String error) {
        this.status = WikiIngestTaskStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = error == null || error.isBlank() ? "任务执行失败" : error;
        this.phase = "failed";
    }

    public void cancel() {
        this.status = WikiIngestTaskStatus.CANCELLED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = "已取消";
        this.phase = "cancelled";
    }

    public boolean canRetry() {
        return status == WikiIngestTaskStatus.FAILED && attempts < maxAttempts;
    }

    /**
     * 自动重试：失败后重新入队，保留已累计的 attempts。
     */
    public void requeue() {
        this.status = WikiIngestTaskStatus.QUEUED;
        this.phase = "queued";
        this.percent = 0;
        this.errorMessage = null;
        this.finishedAt = null;
    }

    /**
     * 手动重试：清空 attempts 后重新入队。
     */
    public void resetForRetry() {
        this.attempts = 0;
        this.status = WikiIngestTaskStatus.QUEUED;
        this.phase = "queued";
        this.percent = 0;
        this.errorMessage = null;
        this.startedAt = null;
        this.finishedAt = null;
    }
}
