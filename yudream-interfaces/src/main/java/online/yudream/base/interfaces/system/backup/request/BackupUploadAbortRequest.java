package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 中止分片上传请求。 */
@Data
public class BackupUploadAbortRequest {
    private String uploadId;
}
