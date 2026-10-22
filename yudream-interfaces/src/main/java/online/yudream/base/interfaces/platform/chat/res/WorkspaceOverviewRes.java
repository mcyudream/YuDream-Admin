package online.yudream.base.interfaces.platform.chat.res;

import java.util.List;

/** quotaBytes 为 null 表示不限 */
public record WorkspaceOverviewRes(String workspaceId, String uri, List<WorkspaceEntryRes> entries, long usedBytes,
                                   Long quotaBytes, int tmpRetentionDays, int trashRetentionDays) {
}
