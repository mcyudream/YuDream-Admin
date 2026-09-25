package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 备份任务响应。 */
@Data
@Builder
public class BackupJobRes {
    private String id;
    private String type;
    private String status;
    private String trigger;
    private List<String> scopeTags;
    private String planCode;
    private String targetCode;
    private String targetName;
    private String strategy;
    private String archiveName;
    private Long archiveSize;
    private String phase;
    private Integer percent;
    private String message;
    private Long collectionCount;
    private Long documentCount;
    private Long objectCount;
    private Long pluginFileCount;
    private Long insertedCount;
    private Long conflictCount;
    private Long skippedCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
}
