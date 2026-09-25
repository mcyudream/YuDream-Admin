package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 从远端归档恢复请求。 */
@Data
public class BackupRestoreRequest {
    private String archiveName;
    /** LOCAL_WINS / ARCHIVE_WINS。 */
    private String strategy;
}
