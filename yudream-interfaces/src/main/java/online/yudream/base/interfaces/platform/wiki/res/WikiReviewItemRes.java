package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record WikiReviewItemRes(
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
