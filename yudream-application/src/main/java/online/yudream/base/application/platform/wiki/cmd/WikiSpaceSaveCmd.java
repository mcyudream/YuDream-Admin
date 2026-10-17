package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentReviewMode;

import java.io.Serializable;

@Data
public class WikiSpaceSaveCmd implements Serializable {
    private Long id;
    private String name;
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
    private int chunkSize = 1200;
    private int chunkOverlap = 160;
    private int topK = 8;
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
    private int contextWindowTokens = 32768;
    private boolean sourceGroundedDefault;
    private boolean watchEnabled;
    private String watchFolderPath;
    /** 是否允许登录用户在公开页面发表评论；默认关闭。 */
    private boolean commentEnabled;
    /** 评论审核方式：MANUAL（默认）/ AI；AI 审核通过即公开，判定不了回落人工。 */
    private WikiCommentReviewMode commentReviewMode;
}
