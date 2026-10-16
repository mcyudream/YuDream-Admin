package online.yudream.base.application.platform.feishu.assembler;

import online.yudream.base.application.platform.feishu.dto.FeishuBlockWriteResultDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentContentDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentMetaDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuQuotaConfigDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuQuotaDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodeDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodePageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpaceDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpacePageDTO;
import online.yudream.base.domain.platform.feishu.valobj.FeishuBlockWriteResult;
import online.yudream.base.domain.platform.feishu.valobj.FeishuCloudDoc;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocPage;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentBlock;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentMeta;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaSnapshot;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiNode;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiSpace;

/**
 * 飞书 domain -> DTO 转换。
 */
public final class FeishuAssembler {

    private FeishuAssembler() {
    }

    public static FeishuCloudDocPageDTO cloudDocPage(FeishuDocPage<FeishuCloudDoc> page) {
        return FeishuCloudDocPageDTO.builder()
                .items(page.items().stream().map(FeishuAssembler::cloudDoc).toList())
                .nextPageToken(page.nextPageToken())
                .hasMore(page.hasMore())
                .build();
    }

    public static FeishuWikiSpacePageDTO wikiSpacePage(FeishuDocPage<FeishuWikiSpace> page) {
        return FeishuWikiSpacePageDTO.builder()
                .items(page.items().stream().map(FeishuAssembler::wikiSpace).toList())
                .nextPageToken(page.nextPageToken())
                .hasMore(page.hasMore())
                .build();
    }

    public static FeishuWikiNodePageDTO wikiNodePage(String spaceId, FeishuDocPage<FeishuWikiNode> page) {
        return FeishuWikiNodePageDTO.builder()
                .spaceId(spaceId)
                .items(page.items().stream().map(FeishuAssembler::wikiNode).toList())
                .nextPageToken(page.nextPageToken())
                .hasMore(page.hasMore())
                .build();
    }

    public static FeishuDocumentBlockPageDTO blockPage(String documentId, FeishuDocPage<FeishuDocumentBlock> page) {
        return FeishuDocumentBlockPageDTO.builder()
                .documentId(documentId)
                .items(page.items().stream().map(FeishuAssembler::block).toList())
                .nextPageToken(page.nextPageToken())
                .hasMore(page.hasMore())
                .build();
    }

    public static FeishuDocumentMetaDTO documentMeta(FeishuDocumentMeta meta) {
        return FeishuDocumentMetaDTO.builder()
                .documentId(meta.documentId())
                .revisionId(meta.revisionId())
                .title(meta.title())
                .build();
    }

    public static FeishuDocumentContentDTO documentContent(String documentId, String content) {
        return FeishuDocumentContentDTO.builder()
                .documentId(documentId)
                .content(content)
                .build();
    }

    public static FeishuBlockWriteResultDTO writeResult(FeishuBlockWriteResult result) {
        return FeishuBlockWriteResultDTO.builder()
                .documentId(result.documentId())
                .revisionId(result.revisionId())
                .blockIds(result.blockIds())
                .blocksRaw(result.blocksRaw())
                .build();
    }

    public static FeishuQuotaDTO quota(FeishuQuotaSnapshot snapshot) {
        return FeishuQuotaDTO.builder()
                .userId(snapshot.userId() == null ? null : String.valueOf(snapshot.userId()))
                .minuteKey(snapshot.minuteKey())
                .minuteUsed(snapshot.minuteUsed())
                .minuteLimit(snapshot.minuteLimit())
                .dailyKey(snapshot.dayKey())
                .dailyUsed(snapshot.dayUsed())
                .dailyLimit(snapshot.dayLimit())
                .build();
    }

    public static FeishuQuotaConfigDTO quotaConfig(FeishuQuotaPolicy policy) {
        return FeishuQuotaConfigDTO.builder()
                .defaultPerMinuteLimit(policy.perMinuteLimit())
                .defaultDailyLimit(policy.dailyLimit())
                .build();
    }

    private static FeishuCloudDocDTO cloudDoc(FeishuCloudDoc doc) {
        return FeishuCloudDocDTO.builder()
                .token(doc.token())
                .name(doc.name())
                .type(doc.type())
                .parentToken(doc.parentToken())
                .url(doc.url())
                .shortcutTargetType(doc.shortcutTargetType())
                .shortcutTargetToken(doc.shortcutTargetToken())
                .createdTime(doc.createdTime())
                .modifiedTime(doc.modifiedTime())
                .ownerId(doc.ownerId())
                .build();
    }

    private static FeishuWikiSpaceDTO wikiSpace(FeishuWikiSpace space) {
        return FeishuWikiSpaceDTO.builder()
                .spaceId(space.spaceId())
                .name(space.name())
                .description(space.description())
                .spaceType(space.spaceType())
                .visibility(space.visibility())
                .openSharing(space.openSharing())
                .build();
    }

    private static FeishuWikiNodeDTO wikiNode(FeishuWikiNode node) {
        return FeishuWikiNodeDTO.builder()
                .nodeToken(node.nodeToken())
                .objToken(node.objToken())
                .objType(node.objType())
                .title(node.title())
                .hasChild(node.hasChild())
                .parentNodeToken(node.parentNodeToken())
                .nodeType(node.nodeType())
                .originNodeToken(node.originNodeToken())
                .originSpaceId(node.originSpaceId())
                .spaceId(node.spaceId())
                .build();
    }

    private static FeishuDocumentBlockDTO block(FeishuDocumentBlock block) {
        return FeishuDocumentBlockDTO.builder()
                .blockId(block.blockId())
                .parentId(block.parentId())
                .blockType(block.blockType())
                .children(block.children())
                .raw(block.raw())
                .build();
    }
}
