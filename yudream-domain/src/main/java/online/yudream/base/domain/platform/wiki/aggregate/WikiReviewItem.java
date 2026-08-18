package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 摄入过程中 LLM 标记、等待人工判断的异步审阅项。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiReviewItem extends BaseDomain {

    private Long spaceId;
    private Long sourceId;
    private WikiReviewItemType itemType;
    private String title;
    private String description;
    private String suggestedAction;
    private List<String> searchQueries;
    private List<String> pageTitles;
    private WikiReviewStatus status;
    private LocalDateTime resolvedAt;

    public static WikiReviewItem create(Long spaceId, Long sourceId, WikiReviewItemType itemType, String title,
                                        String description, String suggestedAction, List<String> searchQueries,
                                        List<String> pageTitles) {
        return WikiReviewItem.builder()
                .spaceId(spaceId)
                .sourceId(sourceId)
                .itemType(itemType == null ? WikiReviewItemType.FLAG : itemType)
                .title(title == null ? "" : title.trim())
                .description(description == null ? "" : description.trim())
                .suggestedAction(suggestedAction == null ? "" : suggestedAction.trim())
                .searchQueries(searchQueries == null ? new ArrayList<>() : new ArrayList<>(searchQueries))
                .pageTitles(pageTitles == null ? new ArrayList<>() : new ArrayList<>(pageTitles))
                .status(WikiReviewStatus.PENDING)
                .build();
    }

    public void resolve() {
        this.status = WikiReviewStatus.DONE;
        this.resolvedAt = LocalDateTime.now();
    }

    public void dismiss() {
        this.status = WikiReviewStatus.DISMISSED;
        this.resolvedAt = LocalDateTime.now();
    }
}
