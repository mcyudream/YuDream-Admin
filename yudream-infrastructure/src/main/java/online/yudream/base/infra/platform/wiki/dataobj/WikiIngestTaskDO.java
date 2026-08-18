package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiIngestTask")
public class WikiIngestTaskDO extends BaseDO {
    @Indexed
    private Long spaceId;
    @Indexed
    private Long sourceId;
    private WikiIngestTaskType taskType;
    @Indexed
    private WikiIngestTaskStatus status;
    private int attempts;
    private int maxAttempts;
    private String errorMessage;
    private String phase;
    private int percent;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private long sortOrder;
    private String payloadJson;
}
