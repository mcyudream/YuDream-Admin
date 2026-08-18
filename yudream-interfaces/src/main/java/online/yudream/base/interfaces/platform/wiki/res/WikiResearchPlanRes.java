package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.util.List;

@Builder
public record WikiResearchPlanRes(
        String topic,
        String rationale,
        List<String> queries
) {
}
