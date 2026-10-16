package online.yudream.base.application.platform.feishu.query;

/**
 * 云空间文件列表查询。folderToken 为空时列出应用根目录。
 */
public record FeishuDriveFileQuery(String folderToken, String pageToken, int pageSize) {
}
