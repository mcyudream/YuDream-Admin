package online.yudream.base.domain.platform.workspace.valobj;

import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;

import java.time.LocalDateTime;

/**
 * 文件内容版本：每次写入生成一个不可变版本，内容对象键 workspaces/&lt;wsId&gt;/objects/&lt;versionId&gt;。
 * 配额按工作区全部版本字节计。
 */
public record WorkspaceFileVersion(
        Long id,
        Long workspaceId,
        Long fileId,
        int revision,
        String objectKey,
        long size,
        String sha256,
        String contentType,
        WorkspaceActor actor,
        LocalDateTime createdAt
) {
    public static String objectKey(Long workspaceId, Long versionId) {
        return "workspaces/" + workspaceId + "/objects/" + versionId;
    }
}
