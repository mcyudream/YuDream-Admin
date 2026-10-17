package online.yudream.base.infra.platform.wiki.mapper;

import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentReviewMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextBlockType;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextBlockHint;
import online.yudream.base.infra.platform.wiki.dataobj.WikiCommentAnchorDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiCommentDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiNodeDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiPageVersionDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceMemberDO;

import java.util.ArrayList;

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
                .graphTableCode(dataObj.getGraphTableCode())
                .chunkSize(dataObj.getChunkSize())
                .chunkOverlap(dataObj.getChunkOverlap())
                .topK(dataObj.getTopK())
                .queryExpansionEnabled(dataObj.isQueryExpansionEnabled())
                .rerankEnabled(dataObj.isRerankEnabled())
                .rerankProviderCode(dataObj.getRerankProviderCode())
                .rerankModelCode(dataObj.getRerankModelCode())
                .hitImageLimit(dataObj.getHitImageLimit())
                .purpose(dataObj.getPurpose())
                .schemaContent(dataObj.getSchemaContent())
                .language(dataObj.getLanguage())
                .chatProviderCode(dataObj.getChatProviderCode())
                .chatModelCode(dataObj.getChatModelCode())
                .ingestProviderCode(dataObj.getIngestProviderCode())
                .ingestModelCode(dataObj.getIngestModelCode())
                .visionProviderCode(dataObj.getVisionProviderCode())
                .visionModelCode(dataObj.getVisionModelCode())
                .webSearchProviderCode(dataObj.getWebSearchProviderCode())
                .webSearchApiKey(dataObj.getWebSearchApiKey())
                .webSearchInstanceUrl(dataObj.getWebSearchInstanceUrl())
                .webSearchEngine(dataObj.getWebSearchEngine())
                .contextWindowTokens(dataObj.getContextWindowTokens())
                .sourceGroundedDefault(dataObj.isSourceGroundedDefault())
                .watchEnabled(dataObj.isWatchEnabled())
                .watchFolderPath(dataObj.getWatchFolderPath())
                .coverType(WikiSpaceCoverType.of(dataObj.getCoverType()))
                .coverSeed(dataObj.getCoverSeed() == null ? "" : dataObj.getCoverSeed())
                .coverFileObjectId(dataObj.getCoverFileObjectId())
                .coverAlt(dataObj.getCoverAlt() == null ? "" : dataObj.getCoverAlt())
                .commentEnabled(dataObj.isCommentEnabled())
                .commentReviewMode(WikiCommentReviewMode.of(dataObj.getCommentReviewMode()))
                .aclMode(WikiSpaceAclMode.of(dataObj.getAclMode()))
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
        dataObj.setGraphTableCode(domain.getGraphTableCode());
        dataObj.setChunkSize(domain.getChunkSize());
        dataObj.setChunkOverlap(domain.getChunkOverlap());
        dataObj.setTopK(domain.getTopK());
        dataObj.setQueryExpansionEnabled(domain.isQueryExpansionEnabled());
        dataObj.setRerankEnabled(domain.isRerankEnabled());
        dataObj.setRerankProviderCode(domain.getRerankProviderCode());
        dataObj.setRerankModelCode(domain.getRerankModelCode());
        dataObj.setHitImageLimit(domain.getHitImageLimit());
        dataObj.setPurpose(domain.getPurpose());
        dataObj.setSchemaContent(domain.getSchemaContent());
        dataObj.setLanguage(domain.getLanguage());
        dataObj.setChatProviderCode(domain.getChatProviderCode());
        dataObj.setChatModelCode(domain.getChatModelCode());
        dataObj.setIngestProviderCode(domain.getIngestProviderCode());
        dataObj.setIngestModelCode(domain.getIngestModelCode());
        dataObj.setVisionProviderCode(domain.getVisionProviderCode());
        dataObj.setVisionModelCode(domain.getVisionModelCode());
        dataObj.setWebSearchProviderCode(domain.getWebSearchProviderCode());
        dataObj.setWebSearchApiKey(domain.getWebSearchApiKey());
        dataObj.setWebSearchInstanceUrl(domain.getWebSearchInstanceUrl());
        dataObj.setWebSearchEngine(domain.getWebSearchEngine());
        dataObj.setContextWindowTokens(domain.getContextWindowTokens());
        dataObj.setSourceGroundedDefault(domain.isSourceGroundedDefault());
        dataObj.setWatchEnabled(domain.isWatchEnabled());
        dataObj.setWatchFolderPath(domain.getWatchFolderPath());
        dataObj.setCoverType(WikiSpaceCoverType.of(domain.getCoverType()));
        dataObj.setCoverSeed(domain.getCoverSeed() == null ? "" : domain.getCoverSeed());
        dataObj.setCoverFileObjectId(domain.getCoverFileObjectId());
        dataObj.setCoverAlt(domain.getCoverAlt() == null ? "" : domain.getCoverAlt());
        dataObj.setCommentEnabled(domain.isCommentEnabled());
        dataObj.setCommentReviewMode(domain.effectiveCommentReviewMode());
        dataObj.setAclMode(WikiSpaceAclMode.of(domain.getAclMode()));
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
                .pageType(dataObj.getPageType())
                .sources(dataObj.getSources() == null ? new ArrayList<>() : dataObj.getSources())
                .related(dataObj.getRelated() == null ? new ArrayList<>() : dataObj.getRelated())
                .tags(dataObj.getTags() == null ? new ArrayList<>() : dataObj.getTags())
                .summary(dataObj.getSummary())
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
        dataObj.setPageType(domain.getPageType());
        dataObj.setSources(domain.getSources() == null ? new ArrayList<>() : new ArrayList<>(domain.getSources()));
        dataObj.setRelated(domain.getRelated() == null ? new ArrayList<>() : new ArrayList<>(domain.getRelated()));
        dataObj.setTags(domain.getTags() == null ? new ArrayList<>() : new ArrayList<>(domain.getTags()));
        dataObj.setSummary(domain.getSummary());
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

    public static WikiSpaceMember member(WikiSpaceMemberDO dataObj) {
        if (dataObj == null) return null;
        return WikiSpaceMember.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .subjectType(dataObj.getSubjectType())
                .subjectId(dataObj.getSubjectId())
                .actions(dataObj.getActions() == null ? new ArrayList<>() : new ArrayList<>(dataObj.getActions()))
                .createdByUserId(dataObj.getCreatedByUserId())
                .build();
    }

    public static WikiSpaceMemberDO member(WikiSpaceMember domain) {
        if (domain == null) return null;
        WikiSpaceMemberDO dataObj = new WikiSpaceMemberDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setSubjectType(domain.getSubjectType());
        dataObj.setSubjectId(domain.getSubjectId());
        dataObj.setActions(domain.getActions() == null ? new ArrayList<>() : new ArrayList<>(domain.getActions()));
        dataObj.setCreatedByUserId(domain.getCreatedByUserId());
        return dataObj;
    }

    public static WikiComment comment(WikiCommentDO dataObj) {
        if (dataObj == null) return null;
        return WikiComment.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .nodeId(dataObj.getNodeId())
                .parentId(dataObj.getParentId())
                .authorType(dataObj.getAuthorType())
                .authorUserId(dataObj.getAuthorUserId())
                .authorName(dataObj.getAuthorName())
                .content(dataObj.getContent())
                .anchor(anchor(dataObj.getAnchor()))
                .status(dataObj.getStatus())
                .pinned(dataObj.isPinned())
                .reviewedByUserId(dataObj.getReviewedByUserId())
                .reviewedAt(dataObj.getReviewedAt())
                .ragStatus(dataObj.getRagStatus())
                .reviewMode(dataObj.getReviewMode())
                .aiVerdict(dataObj.getAiVerdict())
                .aiNote(dataObj.getAiNote())
                .aiModel(dataObj.getAiModel())
                .aiReviewedAt(dataObj.getAiReviewedAt())
                .build();
    }

    public static WikiCommentDO comment(WikiComment domain) {
        if (domain == null) return null;
        WikiCommentDO dataObj = new WikiCommentDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setNodeId(domain.getNodeId());
        dataObj.setParentId(domain.getParentId());
        dataObj.setAuthorType(domain.getAuthorType());
        dataObj.setAuthorUserId(domain.getAuthorUserId());
        dataObj.setAuthorName(domain.getAuthorName());
        dataObj.setContent(domain.getContent());
        dataObj.setAnchor(anchor(domain.getAnchor()));
        dataObj.setStatus(domain.getStatus());
        dataObj.setPinned(domain.isPinned());
        dataObj.setReviewedByUserId(domain.getReviewedByUserId());
        dataObj.setReviewedAt(domain.getReviewedAt());
        dataObj.setRagStatus(domain.getRagStatus());
        dataObj.setReviewMode(domain.getReviewMode());
        dataObj.setAiVerdict(domain.getAiVerdict());
        dataObj.setAiNote(domain.getAiNote());
        dataObj.setAiModel(domain.getAiModel());
        dataObj.setAiReviewedAt(domain.getAiReviewedAt());
        return dataObj;
    }

    /** 锚点回写：版本/哈希为服务端盖章值，客户端无法从请求侧注入。 */
    public static WikiCommentAnchorDO anchor(WikiTextAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        WikiCommentAnchorDO dataObj = new WikiCommentAnchorDO();
        dataObj.setExact(anchor.exact());
        dataObj.setPrefix(anchor.prefix());
        dataObj.setSuffix(anchor.suffix());
        WikiTextBlockHint blockHint = anchor.blockHint();
        if (blockHint != null) {
            dataObj.setBlockType(blockHint.type().wireValue());
            dataObj.setBlockOrdinal(blockHint.ordinal());
            dataObj.setTableRow(blockHint.tableRow());
            dataObj.setTableCell(blockHint.tableCell());
        }
        dataObj.setStartOffset(anchor.startOffset());
        dataObj.setEndOffset(anchor.endOffset());
        dataObj.setVersionId(anchor.versionId());
        dataObj.setRevision(anchor.revision());
        dataObj.setContentHash(anchor.contentHash());
        return dataObj;
    }

    /**
     * 锚点读取：历史评论（无 anchor 子文档）保持 null；异常存量字段按「丢弃块提示、
     * 保留引用文本」降级，不因单条脏数据阻断整页评论读取。
     */
    public static WikiTextAnchor anchor(WikiCommentAnchorDO dataObj) {
        if (dataObj == null || dataObj.getExact() == null || dataObj.getExact().isBlank()) {
            return null;
        }
        try {
            return new WikiTextAnchor(dataObj.getExact(), dataObj.getPrefix(), dataObj.getSuffix(),
                    blockHint(dataObj), dataObj.getStartOffset(), dataObj.getEndOffset(),
                    dataObj.getVersionId(), dataObj.getRevision(), dataObj.getContentHash());
        }
        catch (RuntimeException error) {
            return null;
        }
    }

    private static WikiTextBlockHint blockHint(WikiCommentAnchorDO dataObj) {
        WikiTextBlockType blockType = WikiTextBlockType.of(dataObj.getBlockType());
        Integer ordinal = dataObj.getBlockOrdinal();
        if (blockType == null || ordinal == null || ordinal < 0) {
            return null;
        }
        if (blockType == WikiTextBlockType.TABLE_CELL) {
            Integer row = dataObj.getTableRow();
            Integer cell = dataObj.getTableCell();
            return row == null || cell == null || row < 0 || cell < 0 ? null
                    : WikiTextBlockHint.tableCell(ordinal, row, cell);
        }
        return WikiTextBlockHint.of(blockType, ordinal);
    }

    private static void copyBase(WikiSpaceMember source, WikiSpaceMemberDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private static void copyBase(WikiComment source, WikiCommentDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }
}
