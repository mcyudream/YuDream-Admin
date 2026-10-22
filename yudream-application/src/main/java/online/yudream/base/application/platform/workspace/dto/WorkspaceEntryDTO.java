package online.yudream.base.application.platform.workspace.dto;

import java.time.LocalDateTime;

/** 工作区条目：kind 为 FILE / DIRECTORY；id 与 revision 供界面并发保存与版本查询。 */
public record WorkspaceEntryDTO(
        String id,
        String path,
        String kind,
        long size,
        String contentType,
        int revision,
        String updatedBy,
        LocalDateTime updatedAt
) {
}
