package online.yudream.base.plugin.activityproof.application.cmd;

import java.util.List;

public record ActivityProofExportCmd(
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
