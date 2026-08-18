package online.yudream.base.application.platform.wiki.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WikiReviewItemDTO(
        String id,
        String spaceId,
        String sourceId,
        String itemType,
        String title,
        String description,
        String suggestedAction,
        List<String> searchQueries,
        List<String> pageTitles,
        String status,
        LocalDateTime resolvedAt,
        LocalDateTime createTime
) {
}
