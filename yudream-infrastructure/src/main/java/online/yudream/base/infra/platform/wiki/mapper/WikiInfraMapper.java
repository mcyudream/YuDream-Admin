package online.yudream.base.infra.platform.wiki.mapper;

import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.infra.platform.wiki.dataobj.WikiNodeDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiPageVersionDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceDO;

public final class WikiInfraMapper {

    private WikiInfraMapper() {
    }

    public static WikiSpace toDomain(WikiSpaceDO dataObj) {
        if (dataObj == null) return null;
        return WikiSpace.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .name(dataObj.getName())
                .slug(dataObj.getSlug())
                .description(dataObj.getDescription())
                .publicReadEnabled(dataObj.isPublicReadEnabled())
                .externalSearchEnabled(dataObj.isExternalSearchEnabled())
                .embeddingProviderCode(dataObj.getEmbeddingProviderCode())
                .embeddingModelCode(dataObj.getEmbeddingModelCode())
                .graphEnabled(dataObj.isGraphEnabled())
                .graphProviderCode(dataObj.getGraphProviderCode())
                .graphModelCode(dataObj.getGraphModelCode())
                .neo4jConnectionCode(dataObj.getNeo4jConnectionCode())
                .chunkSize(dataObj.getChunkSize())
                .chunkOverlap(dataObj.getChunkOverlap())
                .topK(dataObj.getTopK())
                .queryExpansionEnabled(dataObj.isQueryExpansionEnabled())
                .rerankEnabled(dataObj.isRerankEnabled())
                .build();
    }

    public static WikiSpaceDO space(WikiSpace domain) {
        if (domain == null) return null;
        WikiSpaceDO dataObj = new WikiSpaceDO();
        copyBase(domain, dataObj);
        dataObj.setName(domain.getName());
        dataObj.setSlug(domain.getSlug());
        dataObj.setDescription(domain.getDescription());
        dataObj.setPublicReadEnabled(domain.isPublicReadEnabled());
        dataObj.setExternalSearchEnabled(domain.isExternalSearchEnabled());
        dataObj.setEmbeddingProviderCode(domain.getEmbeddingProviderCode());
        dataObj.setEmbeddingModelCode(domain.getEmbeddingModelCode());
        dataObj.setGraphEnabled(domain.isGraphEnabled());
        dataObj.setGraphProviderCode(domain.getGraphProviderCode());
        dataObj.setGraphModelCode(domain.getGraphModelCode());
        dataObj.setNeo4jConnectionCode(domain.getNeo4jConnectionCode());
        dataObj.setChunkSize(domain.getChunkSize());
        dataObj.setChunkOverlap(domain.getChunkOverlap());
        dataObj.setTopK(domain.getTopK());
        dataObj.setQueryExpansionEnabled(domain.isQueryExpansionEnabled());
        dataObj.setRerankEnabled(domain.isRerankEnabled());
        return dataObj;
    }

    public static WikiNode node(WikiNodeDO dataObj) {
        if (dataObj == null) return null;
        return WikiNode.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .parentId(dataObj.getParentId())
                .ancestorPath(dataObj.getAncestorPath())
                .title(dataObj.getTitle())
                .slug(dataObj.getSlug())
                .nodeType(dataObj.getNodeType())
                .sort(dataObj.getSort())
                .markdownDraft(dataObj.getMarkdownDraft())
                .publishedVersionId(dataObj.getPublishedVersionId())
                .build();
    }

    public static WikiNodeDO node(WikiNode domain) {
        if (domain == null) return null;
        WikiNodeDO dataObj = new WikiNodeDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setParentId(domain.getParentId());
        dataObj.setAncestorPath(domain.getAncestorPath());
        dataObj.setTitle(domain.getTitle());
        dataObj.setSlug(domain.getSlug());
        dataObj.setNodeType(domain.getNodeType());
        dataObj.setSort(domain.getSort());
        dataObj.setMarkdownDraft(domain.getMarkdownDraft());
        dataObj.setPublishedVersionId(domain.getPublishedVersionId());
        return dataObj;
    }

    public static WikiPageVersion version(WikiPageVersionDO dataObj) {
        if (dataObj == null) return null;
        return WikiPageVersion.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .nodeId(dataObj.getNodeId())
                .revision(dataObj.getRevision())
                .title(dataObj.getTitle())
                .markdown(dataObj.getMarkdown())
                .contentHash(dataObj.getContentHash())
                .indexStatus(dataObj.getIndexStatus())
                .indexError(dataObj.getIndexError())
                .indexedAt(dataObj.getIndexedAt())
                .build();
    }

    public static WikiPageVersionDO version(WikiPageVersion domain) {
        if (domain == null) return null;
        WikiPageVersionDO dataObj = new WikiPageVersionDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setNodeId(domain.getNodeId());
        dataObj.setRevision(domain.getRevision());
        dataObj.setTitle(domain.getTitle());
        dataObj.setMarkdown(domain.getMarkdown());
        dataObj.setContentHash(domain.getContentHash());
        dataObj.setIndexStatus(domain.getIndexStatus());
        dataObj.setIndexError(domain.getIndexError());
        dataObj.setIndexedAt(domain.getIndexedAt());
        return dataObj;
    }

    private static void copyBase(WikiSpace source, WikiSpaceDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private static void copyBase(WikiNode source, WikiNodeDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private static void copyBase(WikiPageVersion source, WikiPageVersionDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }
}
