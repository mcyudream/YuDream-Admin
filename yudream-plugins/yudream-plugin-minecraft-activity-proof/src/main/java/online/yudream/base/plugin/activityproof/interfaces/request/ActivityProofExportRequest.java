package online.yudream.base.plugin.activityproof.interfaces.request;

import java.util.List;

public record ActivityProofExportRequest(
        String serverId,
        String activityName,
        String activityDate,
        String proofNo,
        String college,
        String issuer,
        String issueDate,
        Integer minOnlineMinutes,
        Boolean includeAfk,
        List<String> selectedPlayerIds
) {
}
