package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiReviewItem")
public class WikiReviewItemDO extends BaseDO {
    @Indexed
    private Long spaceId;
    @Indexed
    private Long sourceId;
    private WikiReviewItemType itemType;
    private String title;
    private String description;
    private String suggestedAction;
    private List<String> searchQueries = new ArrayList<>();
    private List<String> pageTitles = new ArrayList<>();
    private WikiReviewStatus status;
    private LocalDateTime resolvedAt;
}
