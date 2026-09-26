package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 开启分片上传请求。 */
@Data
public class BackupUploadBeginRequest {
    /** 原始文件名（仅用于展示）。 */
    private String name;
    /** 归档总字节数。 */
    private Long size;
}
