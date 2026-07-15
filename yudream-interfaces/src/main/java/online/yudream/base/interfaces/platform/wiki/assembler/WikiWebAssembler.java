package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.cmd.*;
import online.yudream.base.interfaces.platform.wiki.request.*;

public final class WikiWebAssembler {
    private WikiWebAssembler() {}
    public static WikiSpaceSaveCmd space(Long id, WikiSpaceSaveRequest r) {
        WikiSpaceSaveCmd c = new WikiSpaceSaveCmd(); c.setId(id); c.setName(r.getName()); c.setSlug(r.getSlug()); c.setDescription(r.getDescription()); c.setPublicReadEnabled(r.isPublicReadEnabled()); c.setExternalSearchEnabled(r.isExternalSearchEnabled()); c.setEmbeddingProviderCode(r.getEmbeddingProviderCode()); c.setEmbeddingModelCode(r.getEmbeddingModelCode()); c.setGraphEnabled(r.isGraphEnabled()); c.setGraphProviderCode(r.getGraphProviderCode()); c.setGraphModelCode(r.getGraphModelCode()); c.setNeo4jConnectionCode(r.getNeo4jConnectionCode()); c.setChunkSize(r.getChunkSize()); c.setChunkOverlap(r.getChunkOverlap()); c.setTopK(r.getTopK()); c.setQueryExpansionEnabled(r.isQueryExpansionEnabled()); c.setRerankEnabled(r.isRerankEnabled()); return c;
    }
    public static WikiNodeSaveCmd node(Long id, Long spaceId, WikiNodeSaveRequest r) { WikiNodeSaveCmd c = new WikiNodeSaveCmd(); c.setId(id); c.setSpaceId(spaceId); c.setParentId(r.getParentId() == null || r.getParentId().isBlank() ? null : Long.valueOf(r.getParentId())); c.setTitle(r.getTitle()); c.setSlug(r.getSlug()); c.setNodeType(r.getNodeType()); c.setSort(r.getSort()); c.setMarkdown(r.getMarkdown()); return c; }
}
