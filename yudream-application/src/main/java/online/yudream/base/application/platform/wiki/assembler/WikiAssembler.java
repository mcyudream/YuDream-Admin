package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiNodeDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;

import java.util.List;

public final class WikiAssembler {
    private WikiAssembler() {
    }

    public static WikiSpaceDTO space(WikiSpace s) {
        return WikiSpaceDTO.builder()
                .id(id(s.getId()))
                .name(s.getName())
                .slug(s.getSlug())
                .description(s.getDescription())
                .publicReadEnabled(s.isPublicReadEnabled())
                .externalSearchEnabled(s.isExternalSearchEnabled())
                .embeddingProviderCode(s.getEmbeddingProviderCode())
                .embeddingModelCode(s.getEmbeddingModelCode())
                .graphEnabled(s.isGraphEnabled())
                .graphProviderCode(s.getGraphProviderCode())
                .graphModelCode(s.getGraphModelCode())
                .neo4jConnectionCode(s.getNeo4jConnectionCode())
                .chunkSize(s.getChunkSize())
                .chunkOverlap(s.getChunkOverlap())
                .topK(s.getTopK())
                .queryExpansionEnabled(s.isQueryExpansionEnabled())
                .rerankEnabled(s.isRerankEnabled())
                .purpose(s.getPurpose())
                .schemaContent(s.getSchemaContent())
                .language(s.getLanguage())
                .chatProviderCode(s.getChatProviderCode())
                .chatModelCode(s.getChatModelCode())
                .ingestProviderCode(s.getIngestProviderCode())
                .ingestModelCode(s.getIngestModelCode())
                .visionProviderCode(s.getVisionProviderCode())
                .visionModelCode(s.getVisionModelCode())
                .webSearchProviderCode(s.getWebSearchProviderCode())
                .webSearchApiKey(s.getWebSearchApiKey())
                .webSearchInstanceUrl(s.getWebSearchInstanceUrl())
                .webSearchEngine(s.getWebSearchEngine())
                .contextWindowTokens(s.getContextWindowTokens())
                .sourceGroundedDefault(s.isSourceGroundedDefault())
                .watchEnabled(s.isWatchEnabled())
                .watchFolderPath(s.getWatchFolderPath())
                .build();
    }

    public static WikiNodeDTO node(WikiNode n, WikiIndexStatus status, List<WikiNodeDTO> children) {
        return WikiNodeDTO.builder()
                .id(id(n.getId()))
                .parentId(id(n.getParentId()))
                .title(n.getTitle())
                .slug(n.getSlug())
                .nodeType(n.getNodeType())
                .sort(n.getSort())
                .markdown(n.getMarkdownDraft())
                .body(n.bodyMarkdown())
                .publishedVersionId(id(n.getPublishedVersionId()))
                .indexStatus(status)
                .children(children)
                .pageType(pageType(n.getPageType()))
                .sources(n.getSources())
                .related(n.getRelated())
                .tags(n.getTags())
                .summary(n.getSummary())
                .build();
    }

    private static String id(Long id) {
        return id == null ? null : id.toString();
    }

    private static String pageType(WikiPageType type) {
        return type == null ? null : type.name().toLowerCase();
    }

    /**
     * 由稳定路由 DTO 与发布版本构造完全发布化的公开页面 DTO。
     * 页面展示字段全部来自发布版本 frontmatter/正文，目录仍使用稳定元数据。
     */
    public static WikiNodeDTO publishedNode(WikiNodeDTO stable, WikiPageVersion version) {
        if (stable == null || version == null || version.getMarkdown() == null) {
            return null;
        }
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(version.getMarkdown());
        return WikiNodeDTO.builder()
                .id(stable.getId())
                .parentId(stable.getParentId())
                .title(frontmatter.title().isBlank() ? version.getTitle() : frontmatter.title())
                .slug(stable.getSlug())
                .path(stable.getPath())
                .nodeType(stable.getNodeType())
                .sort(stable.getSort())
                .markdown(version.getMarkdown())
                .body(frontmatter.bodyOnly())
                .publishedVersionId(stable.getPublishedVersionId())
                .indexStatus(stable.getIndexStatus())
                .children(stable.getChildren())
                .pageType(pageType(frontmatter.pageType()))
                .sources(frontmatter.sources())
                .related(frontmatter.related())
                .tags(frontmatter.tags())
                .summary(frontmatter.summary())
                .build();
    }
}
