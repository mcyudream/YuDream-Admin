package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.valobj.WikiCover;
import online.yudream.base.domain.platform.wiki.valobj.WikiSlug;

/**
 * 知识库（对应 llm_wiki 的一个 project）。
 * <p>
 * 除基础展示与检索配置外，还承载 purpose（方向意图）、schema（结构规则）、
 * Chat/Ingest/Vision 独立模型路由、网络搜索与 source 文件夹监听配置。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiSpace extends BaseDomain {

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
    /** 重排序端点：为空时检索沿用向量模型的供应商与首个重排模型（兼容旧数据）。 */
    private String rerankProviderCode;
    private String rerankModelCode;
    /** 每条检索命中最多随带的相关图片数；null 表示默认 4，0 表示不带图片 */
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

    /** 封面来源；旧数据为空按 NONE 处理。 */
    private WikiSpaceCoverType coverType;
    /** SEEDED 封面的稳定 seed（同一 seed 确定性呈现）。 */
    private String coverSeed;
    /** UPLOAD 封面引用的系统文件对象 ID。 */
    private Long coverFileObjectId;
    /** 封面替代文本（无障碍展示）。 */
    private String coverAlt;

    /** 是否允许登录用户在公开页面发表评论；默认关闭。 */
    private boolean commentEnabled;

    /** 实例 ACL 模式；旧数据为空按 LEGACY 处理。 */
    private WikiSpaceAclMode aclMode;

    public static WikiSpace create(String name, String slug) {
        WikiSpace space = new WikiSpace();
        space.update(name, slug, null, false, false, null, null, false, null, null, 1200, 160, 8);
        space.purpose = "";
        space.schemaContent = "";
        space.language = "zh-CN";
        space.chatProviderCode = "";
        space.chatModelCode = "";
        space.ingestProviderCode = "";
        space.ingestModelCode = "";
        space.visionProviderCode = "";
        space.visionModelCode = "";
        space.webSearchProviderCode = "";
        space.webSearchApiKey = "";
        space.webSearchInstanceUrl = "";
        space.webSearchEngine = "";
        space.contextWindowTokens = 32768;
        space.sourceGroundedDefault = false;
        space.watchEnabled = false;
        space.watchFolderPath = "";
        // 存量空间与导入/迁移空间保持 LEGACY；平台侧新建空间由应用用例显式启用 RESTRICTED 并授予创建者 MANAGE。
        space.coverType = WikiSpaceCoverType.NONE;
        space.coverSeed = "";
        space.coverAlt = "";
        space.commentEnabled = false;
        space.aclMode = WikiSpaceAclMode.LEGACY;
        return space;
    }

    public void update(String name, String slug, String description, boolean publicReadEnabled, boolean externalSearchEnabled,
                       String embeddingProviderCode, String embeddingModelCode, boolean graphEnabled, String graphProviderCode,
                       String graphModelCode, int chunkSize, int chunkOverlap, int topK) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("知识库名称不能为空");
        }
        if (chunkSize < 200 || chunkSize > 8000 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new BizException("分块参数不合法");
        }
        this.name = name.trim();
        this.slug = WikiSlug.of(slug).value();
        this.description = description == null ? "" : description.trim();
        this.publicReadEnabled = publicReadEnabled;
        this.externalSearchEnabled = externalSearchEnabled;
        this.embeddingProviderCode = text(embeddingProviderCode);
        this.embeddingModelCode = text(embeddingModelCode);
        this.graphEnabled = graphEnabled;
        this.graphProviderCode = text(graphProviderCode);
        this.graphModelCode = text(graphModelCode);
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.topK = Math.clamp(topK, 1, 30);
    }

    public void applyKnowledgeConfig(String purpose, String schemaContent, String language) {
        this.purpose = purpose == null ? "" : purpose.trim();
        this.schemaContent = schemaContent == null ? "" : schemaContent.trim();
        this.language = language == null || language.isBlank() ? "zh-CN" : language.trim();
    }

    public void applyModelRouting(String chatProviderCode, String chatModelCode, String ingestProviderCode,
                                  String ingestModelCode, String visionProviderCode, String visionModelCode) {
        this.chatProviderCode = text(chatProviderCode);
        this.chatModelCode = text(chatModelCode);
        this.ingestProviderCode = text(ingestProviderCode);
        this.ingestModelCode = text(ingestModelCode);
        this.visionProviderCode = text(visionProviderCode);
        this.visionModelCode = text(visionModelCode);
    }

    /** 重排序端点与其余模型分开维护：它只在检索重排时使用，未配置时回落到向量供应商。 */
    public void applyRerankRouting(String providerCode, String modelCode) {
        this.rerankProviderCode = text(providerCode);
        this.rerankModelCode = text(modelCode);
    }

    public void applyWebSearch(String providerCode, String apiKey, String instanceUrl, String engine) {
        this.webSearchProviderCode = text(providerCode);
        this.webSearchApiKey = text(apiKey);
        this.webSearchInstanceUrl = text(instanceUrl);
        this.webSearchEngine = text(engine);
    }

    public void applyRuntime(int contextWindowTokens, boolean sourceGroundedDefault) {
        if (contextWindowTokens < 4096 || contextWindowTokens > 1_000_000) {
            throw new BizException("上下文窗口必须在 4096 到 1000000 token 之间");
        }
        this.contextWindowTokens = contextWindowTokens;
        this.sourceGroundedDefault = sourceGroundedDefault;
    }

    public void applyWatch(boolean watchEnabled, String watchFolderPath) {
        this.watchEnabled = watchEnabled;
        this.watchFolderPath = watchFolderPath == null ? "" : watchFolderPath.trim();
    }

    public int effectiveHitImageLimit() {
        return hitImageLimit == null ? 4 : Math.clamp(hitImageLimit, 0, 12);
    }

    /** 当前封面值对象；旧数据无封面字段时视为无封面。 */
    public WikiCover cover() {
        WikiSpaceCoverType type = WikiSpaceCoverType.of(coverType);
        return new WikiCover(type, coverSeed, coverFileObjectId, coverAlt);
    }

    /** 应用封面（SEEDED 需稳定 seed；UPLOAD 需文件对象 ID；NONE 清空全部封面字段）。 */
    public void applyCover(WikiCover cover) {
        WikiCover next = cover == null ? WikiCover.NONE : cover;
        this.coverType = next.type();
        this.coverSeed = next.seed();
        this.coverFileObjectId = next.fileObjectId();
        this.coverAlt = next.alt();
    }

    /** 移除封面只清空引用，不删除可能被其他位置共享的文件对象。 */
    public void clearCover() {
        applyCover(WikiCover.NONE);
    }

    /** 评论开关（默认关闭；匿名策略不在本用例内）。 */
    public void applyCommentEnabled(boolean enabled) {
        this.commentEnabled = enabled;
    }

    /** 当前 ACL 模式（旧数据回落 LEGACY）。 */
    public WikiSpaceAclMode effectiveAclMode() {
        return WikiSpaceAclMode.of(aclMode);
    }

    /**
     * 启用受限 ACL。调用方必须先确保存在可管理成员（创建者 MANAGE），
     * 否则会锁死空间；本方法只表达状态流转，不代替成员校验。
     */
    public void enableRestrictedAcl() {
        this.aclMode = WikiSpaceAclMode.RESTRICTED;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
