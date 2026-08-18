package online.yudream.base.application.platform.wiki.dto;

import java.util.List;

public record WikiResearchPlanDTO(
        String topic,
        String rationale,
        List<String> queries
) {
}
