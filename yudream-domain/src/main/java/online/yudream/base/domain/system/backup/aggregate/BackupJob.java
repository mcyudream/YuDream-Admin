package online.yudream.base.domain.system.backup.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.domain.system.backup.valobj.BackupJobStats;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 备份任务：全量导出、异地推送、合并导入、异地恢复共用一张任务表，
 * 由基础设施队列单线程串行执行，重启后中断任务标记失败。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BackupJob extends BaseDomain {

    private BackupJobType type;
    private BackupJobStatus status;
    private BackupJobTrigger trigger;
    /** 范围 tag 列表：{@code system} 或 {@code plugin:{pluginCode}/{scopeCode}}。 */
    private List<String> scopeTags;
    private String planCode;
    private String targetCode;
    private String targetName;
    private BackupConflictStrategy strategy;
    /** 插件范围触发时的导出选项（原样传给提供者，如单实例过滤）。 */
    private Map<String, String> scopeOptions;
    private String archiveName;
    private String archivePath;
    private Long archiveSize;
    private String phase;
    private int percent;
    private String message;
    private long collectionCount;
    private long documentCount;
    private long objectCount;
    private long pluginFileCount;
    private long insertedCount;
    private long conflictCount;
    private long skippedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public static BackupJob create(BackupJobType type, BackupJobTrigger trigger, List<String> scopeTags,
                                   BackupConflictStrategy strategy, String planCode,
                                   String targetCode, String targetName) {
        return BackupJob.builder()
                .type(type)
                .status(BackupJobStatus.QUEUED)
                .trigger(trigger)
                .scopeTags(scopeTags == null ? List.of() : List.copyOf(scopeTags))
                .strategy(strategy)
                .planCode(planCode)
                .targetCode(targetCode)
                .targetName(targetName)
                .phase("queued")
                .percent(0)
                .build();
    }

    public void start() {
        this.status = BackupJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.message = null;
        this.phase = "running";
        this.percent = 0;
    }

    public void updateProgress(String phase, String message, int percent) {
        this.phase = phase == null ? this.phase : phase;
        this.message = message;
        this.percent = Math.clamp(percent, 0, 100);
    }

    public void succeed(String archiveName, String archivePath, Long archiveSize, BackupJobStats stats, String message) {
        this.status = BackupJobStatus.SUCCEEDED;
        this.finishedAt = LocalDateTime.now();
        this.phase = "done";
        this.percent = 100;
        this.message = message;
        this.archiveName = archiveName;
        this.archivePath = archivePath;
        this.archiveSize = archiveSize;
        if (stats != null) {
            this.collectionCount = stats.collectionCount();
            this.documentCount = stats.documentCount();
            this.objectCount = stats.objectCount();
            this.pluginFileCount = stats.pluginFileCount();
            this.insertedCount = stats.insertedCount();
            this.conflictCount = stats.conflictCount();
            this.skippedCount = stats.skippedCount();
        }
    }

    public void fail(String error) {
        this.status = BackupJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.phase = "failed";
        this.message = error == null || error.isBlank() ? "备份任务执行失败" : error;
    }

    /** 宿主重启后恢复中断任务。 */
    public void failInterrupted() {
        fail("宿主重启导致任务中断，请重新执行");
    }
}
