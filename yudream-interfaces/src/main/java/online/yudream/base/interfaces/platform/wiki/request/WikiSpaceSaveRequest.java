package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WikiSpaceSaveRequest {
    @NotBlank(message = "知识库名称不能为空") private String name;
    @NotBlank(message = "知识库路径不能为空") private String slug;
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
}
