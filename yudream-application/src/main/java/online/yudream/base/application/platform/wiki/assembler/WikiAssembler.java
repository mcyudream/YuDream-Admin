package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.*;
import online.yudream.base.domain.platform.wiki.aggregate.*;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import java.util.List;

public final class WikiAssembler {
    private WikiAssembler() {}
    public static WikiSpaceDTO space(WikiSpace s) {
        return WikiSpaceDTO.builder().id(id(s.getId())).name(s.getName()).slug(s.getSlug()).description(s.getDescription()).publicReadEnabled(s.isPublicReadEnabled()).externalSearchEnabled(s.isExternalSearchEnabled()).embeddingProviderCode(s.getEmbeddingProviderCode()).embeddingModelCode(s.getEmbeddingModelCode()).graphEnabled(s.isGraphEnabled()).graphProviderCode(s.getGraphProviderCode()).graphModelCode(s.getGraphModelCode()).neo4jConnectionCode(s.getNeo4jConnectionCode()).chunkSize(s.getChunkSize()).chunkOverlap(s.getChunkOverlap()).topK(s.getTopK()).queryExpansionEnabled(s.isQueryExpansionEnabled()).rerankEnabled(s.isRerankEnabled()).build();
    }
    public static WikiNodeDTO node(WikiNode n, WikiIndexStatus status, List<WikiNodeDTO> children) { return WikiNodeDTO.builder().id(id(n.getId())).parentId(id(n.getParentId())).title(n.getTitle()).slug(n.getSlug()).nodeType(n.getNodeType()).sort(n.getSort()).markdown(n.getMarkdownDraft()).publishedVersionId(id(n.getPublishedVersionId())).indexStatus(status).children(children).build(); }
    private static String id(Long id) { return id == null ? null : id.toString(); }
}
