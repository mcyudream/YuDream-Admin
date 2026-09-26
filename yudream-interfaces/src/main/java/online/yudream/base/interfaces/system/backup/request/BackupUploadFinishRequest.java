package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

/** 结束分片上传请求。 */
@Data
public class BackupUploadFinishRequest {
    private String uploadId;
    /** 归档总字节数。 */
    private Long size;
    /** 整档 SHA-256（十六进制，可选；服务端分片增量摘要比对）。 */
    private String sha256;
}
