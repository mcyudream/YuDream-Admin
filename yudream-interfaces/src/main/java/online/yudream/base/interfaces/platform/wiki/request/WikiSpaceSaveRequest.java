package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WikiSpaceSaveRequest {
    @NotBlank(message = "知识库名称不能为空")
    private String name;
    @NotBlank(message = "知识库路径不能为空")
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
    private int chunkSize = 1200;
    private int chunkOverlap = 160;
    private int topK = 8;
    private boolean queryExpansionEnabled;
    private boolean rerankEnabled;
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
}
