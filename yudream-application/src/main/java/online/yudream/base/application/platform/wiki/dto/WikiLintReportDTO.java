package online.yudream.base.application.platform.wiki.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WikiLintReportDTO(
        LocalDateTime generatedAt,
        String summary,
        List<Issue> issues
) {
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
