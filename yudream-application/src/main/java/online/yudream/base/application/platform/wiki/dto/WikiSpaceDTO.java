package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WikiSpaceDTO {
    private String id;
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
    private int chunkSize;
    private int chunkOverlap;
    private int topK;
    private boolean queryExpansionEnabled;
    private boolean rerankEnabled;
    /** 重排序端点（未配置时检索回落到向量供应商的首个重排模型）。 */
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
    /** 是否已配置 Web 搜索 API Key；明文 Key 不出服务。 */
    private boolean webSearchApiKeyConfigured;
    private String webSearchInstanceUrl;
    private String webSearchEngine;
    private int contextWindowTokens;
    private boolean sourceGroundedDefault;
    private boolean watchEnabled;
    private String watchFolderPath;

    /** NONE / SEEDED / UPLOAD */
    private String coverType;
    /** SEEDED 封面的稳定 seed（同一 seed 确定性呈现）。 */
    private String coverSeed;
    /** UPLOAD 封面引用的文件对象 ID（字符串，保持长 ID 精度）。 */
    private String coverFileObjectId;
    private String coverAlt;

    /** 是否允许登录用户在公开页面发表评论。 */
    private boolean commentEnabled;
    /** LEGACY / RESTRICTED 实例 ACL 模式。 */
    private String aclMode;

    /** 列表卡片聚合：页面节点数 / 目录节点数 / 未发布页面数（由应用层按空间补算）。 */
    private int pageCount;
    private int directoryCount;
    private int unpublishedPageCount;
    /** 节点最近更新时间；空库为 null。 */
    private LocalDateTime lastUpdatedAt;
    /** 失败状态的摄入任务数：大于 0 表示该知识库有需要处理的任务。 */
    private int failedIngestCount;
}
