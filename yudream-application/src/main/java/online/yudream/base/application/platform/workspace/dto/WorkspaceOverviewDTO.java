package online.yudream.base.application.platform.workspace.dto;

import java.util.List;

/**
 * 工作区总览：quotaBytes 为 null 表示不限（管理员）；usedBytes 含历史版本与回收站内容。
 */
public record WorkspaceOverviewDTO(
        String workspaceId,
        String uri,
        List<WorkspaceEntryDTO> entries,
        long usedBytes,
        Long quotaBytes,
        int tmpRetentionDays,
        int trashRetentionDays
) {
}
