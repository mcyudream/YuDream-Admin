package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

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
    private String neo4jConnectionCode;
    private int chunkSize;
    private int chunkOverlap;
    private int topK;
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
    private int contextWindowTokens;
    private boolean sourceGroundedDefault;
    private boolean watchEnabled;
    private String watchFolderPath;
}
