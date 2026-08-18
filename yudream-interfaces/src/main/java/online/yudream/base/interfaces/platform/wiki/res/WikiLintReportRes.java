package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record WikiLintReportRes(
        LocalDateTime generatedAt,
        String summary,
        List<Issue> issues
) {
    @Builder
    public record Issue(
            String category,
            String severity,
            String title,
            String description,
            List<String> pageTitles,
            String suggestedAction,
            List<String> searchQueries
    ) {
    }
}
