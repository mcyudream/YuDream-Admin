package online.yudream.base.application.system.backup.dto;

/** 本机归档下载信息。 */
public record BackupArchiveDownloadDTO(String archiveName, String archivePath, long archiveSize) {
}
