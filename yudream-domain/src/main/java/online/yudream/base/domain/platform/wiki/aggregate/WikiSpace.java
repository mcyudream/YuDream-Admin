package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.valobj.WikiSlug;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
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

    public static WikiSpace create(String name, String slug) {
        WikiSpace space = new WikiSpace();
        space.update(name, slug, null, false, false, null, null, false, null, null, 1200, 160, 8);
        return space;
    }
    public void update(String name, String slug, String description, boolean publicReadEnabled, boolean externalSearchEnabled,
                       String embeddingProviderCode, String embeddingModelCode, boolean graphEnabled, String graphProviderCode,
                       String graphModelCode, int chunkSize, int chunkOverlap, int topK) {
        if (name == null || name.trim().isEmpty()) throw new BizException("知识库名称不能为空");
        if (chunkSize < 200 || chunkSize > 8000 || chunkOverlap < 0 || chunkOverlap >= chunkSize) throw new BizException("分块参数不合法");
        this.name = name.trim(); this.slug = WikiSlug.of(slug).value(); this.description = description == null ? "" : description.trim();
        this.publicReadEnabled = publicReadEnabled; this.externalSearchEnabled = externalSearchEnabled;
        this.embeddingProviderCode = text(embeddingProviderCode); this.embeddingModelCode = text(embeddingModelCode);
        this.graphEnabled = graphEnabled; this.graphProviderCode = text(graphProviderCode); this.graphModelCode = text(graphModelCode);
        this.chunkSize = chunkSize; this.chunkOverlap = chunkOverlap; this.topK = Math.clamp(topK, 1, 30);
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
