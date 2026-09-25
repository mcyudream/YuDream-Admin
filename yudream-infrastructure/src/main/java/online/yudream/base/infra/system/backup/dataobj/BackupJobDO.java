package online.yudream.base.infra.system.backup.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("sysBackupJob")
public class BackupJobDO extends BaseDO {
    private BackupJobType type;
    @Indexed
    private BackupJobStatus status;
    private BackupJobTrigger trigger;
    private List<String> scopeTags;
    @Indexed
    private String planCode;
    @Indexed
    private String targetCode;
    private String targetName;
    private BackupConflictStrategy strategy;
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
}
