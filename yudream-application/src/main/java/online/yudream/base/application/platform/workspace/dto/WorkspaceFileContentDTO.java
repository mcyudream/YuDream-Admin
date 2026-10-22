package online.yudream.base.application.platform.workspace.dto;

/**
 * 文件内容预览：encoding=text 时 content 为 UTF-8 正文；binary 时 content 为空（经下载端点获取）；
 * 文本超过预览上限时 truncated=true。
 */
public record WorkspaceFileContentDTO(
        String path,
        String encoding,
        String content,
        boolean truncated,
        long size,
        String contentType,
        int revision
) {
}
