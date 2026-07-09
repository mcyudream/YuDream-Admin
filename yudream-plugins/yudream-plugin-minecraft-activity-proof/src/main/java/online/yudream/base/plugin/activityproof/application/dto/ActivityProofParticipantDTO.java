package online.yudream.base.plugin.activityproof.application.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public record ActivityProofParticipantDTO(
        int index,
        String serverId,
        String userId,
        String playerId,
        String playerName,
        String studentName,
        String studentNo,
        String className,
        String college,
        boolean matched,
        boolean mapped,
        long totalOnlineMillis,
        long totalAfkMillis,
        long effectiveOnlineMillis
) {
    public Map<String, Object> templateData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("index", index);
        data.put("userId", userId);
        data.put("name", studentName == null || studentName.isBlank() ? playerName : studentName);
        data.put("studentName", studentName);
        data.put("studentNo", studentNo);
        data.put("className", className);
        data.put("college", college);
        data.put("playerId", playerId);
        data.put("playerName", playerName);
        data.put("totalOnlineMillis", totalOnlineMillis);
        data.put("totalAfkMillis", totalAfkMillis);
        data.put("effectiveOnlineMillis", effectiveOnlineMillis);
        data.put("onlineMinutes", effectiveOnlineMillis / 60000);
        data.put("onlineHours", BigDecimal.valueOf(effectiveOnlineMillis)
                .divide(BigDecimal.valueOf(3600000), 2, RoundingMode.HALF_UP)
                .toPlainString());
        data.put("matched", matched);
        data.put("mapped", mapped);
        return data;
    }
}
