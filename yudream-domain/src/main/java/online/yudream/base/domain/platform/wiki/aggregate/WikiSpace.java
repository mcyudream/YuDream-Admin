package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
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
    private String neo4jConnectionCode;
    private int chunkSize;
    private int chunkOverlap;
    private int topK;
    private boolean queryExpansionEnabled;
    private boolean rerankEnabled;
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

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
