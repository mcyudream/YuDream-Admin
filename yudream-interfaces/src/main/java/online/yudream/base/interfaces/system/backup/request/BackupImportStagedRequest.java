package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 用已暂存分片归档发起合并导入请求。 */
@Data
public class BackupImportStagedRequest {
    private String uploadId;
    /** LOCAL_WINS / ARCHIVE_WINS。 */
    private String strategy;
}
