package online.yudream.base.domain.system.backup.enumerate;

/** 异地备份目标协议。 */
public enum RemoteTargetType {
    /** FTP（明文，仅限可信内网）。 */
    FTP,
    /** FTPS（显式 TLS）。 */
    FTPS,
    /** WebDAV（HTTP/HTTPS）。 */
    WEBDAV
}
