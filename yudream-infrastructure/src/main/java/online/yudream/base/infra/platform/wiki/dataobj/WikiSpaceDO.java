package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentReviewMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiSpace")
public class WikiSpaceDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String slug;
    private String description;
    private boolean publicReadEnabled;
    private boolean externalSearchEnabled;
    private String embeddingProviderCode;
    private String embeddingModelCode;
    private boolean graphEnabled;
    private String graphProviderCode;
    private String graphModelCode;
    private String graphTableCode;
    private int chunkSize;
    private int chunkOverlap;
    private int topK;
    private boolean queryExpansionEnabled;
    private boolean rerankEnabled;
    private String rerankProviderCode;
    private String rerankModelCode;
    private Integer hitImageLimit;

    private String purpose;
    private String schemaContent;
    private String language;
    private String chatProviderCode;
    private String chatModelCode;
    private String ingestProviderCode;
    private String ingestModelCode;
    private String visionProviderCode;
    private String visionModelCode;
    private String webSearchProviderCode;
    private String webSearchApiKey;
    private String webSearchInstanceUrl;
    private String webSearchEngine;
    private int contextWindowTokens;
    private boolean sourceGroundedDefault;
    private boolean watchEnabled;
    private String watchFolderPath;

    private WikiSpaceCoverType coverType;
    private String coverSeed;
    private Long coverFileObjectId;
    private String coverAlt;

    private boolean commentEnabled;
    /** 评论审核方式（人工/AI）；旧数据为空按人工审核处理。 */
    private WikiCommentReviewMode commentReviewMode;
    private WikiSpaceAclMode aclMode;
}
