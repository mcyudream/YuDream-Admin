package online.yudream.base.domain.platform.chat.valobj;

import java.util.List;
import java.util.Map;

public record ChatActivity(
        String activityType,
        String phase,
        String status,
        String title,
        String content,
        String query,
        List<Map<String, Object>> hits,
        Map<String, Object> graph
) {
}
