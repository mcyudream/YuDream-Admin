package online.yudream.base.interfaces.platform.feishu.assembler;

import online.yudream.base.application.platform.feishu.cmd.FeishuBlockAppendCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockDeleteCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuElementUpdateCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuQuotaConfigCmd;
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
import online.yudream.base.application.platform.feishu.query.FeishuDocumentBlockQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDriveFileQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiNodeQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiSpaceQuery;
import online.yudream.base.interfaces.platform.feishu.request.FeishuBlockAppendRequest;
import online.yudream.base.interfaces.platform.feishu.request.FeishuBlockDeleteRequest;
import online.yudream.base.interfaces.platform.feishu.request.FeishuElementUpdateRequest;
import online.yudream.base.interfaces.platform.feishu.request.FeishuQuotaConfigRequest;
import online.yudream.base.interfaces.platform.feishu.res.FeishuBlockWriteResultRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuCloudDocPageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuCloudDocRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentBlockPageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentBlockRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentContentRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentMetaRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuQuotaConfigRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuQuotaRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiNodePageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiNodeRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiSpacePageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiSpaceRes;

/**
 * 飞书接口边界转换：request -> query/cmd、DTO -> res。
 */
public final class FeishuWebAssembler {

    private FeishuWebAssembler() {
    }

    public static FeishuDriveFileQuery driveFileQuery(String folderToken, String pageToken, Integer pageSize) {
        return new FeishuDriveFileQuery(folderToken, pageToken, size(pageSize));
    }

    public static FeishuWikiSpaceQuery wikiSpaceQuery(String pageToken, Integer pageSize) {
        return new FeishuWikiSpaceQuery(pageToken, size(pageSize));
    }

    public static FeishuWikiNodeQuery wikiNodeQuery(String spaceId, String parentNodeToken,
                                                    String pageToken, Integer pageSize) {
        return new FeishuWikiNodeQuery(spaceId, parentNodeToken, pageToken, size(pageSize));
    }

    public static FeishuDocumentQuery documentQuery(String documentId, Integer lang) {
        return new FeishuDocumentQuery(documentId, lang);
    }

    public static FeishuDocumentBlockQuery blockQuery(String documentId, String pageToken, Integer pageSize) {
        return new FeishuDocumentBlockQuery(documentId, pageToken, size(pageSize));
    }

    public static FeishuBlockAppendCmd appendCmd(String documentId, String parentBlockId, FeishuBlockAppendRequest request) {
        return FeishuBlockAppendCmd.builder()
                .documentId(documentId)
                .parentBlockId(parentBlockId)
                .children(request.getChildren())
                .index(request.getIndex())
                .build();
    }

    public static FeishuBlockDeleteCmd deleteCmd(String documentId, String parentBlockId, FeishuBlockDeleteRequest request) {
        return FeishuBlockDeleteCmd.builder()
                .documentId(documentId)
                .parentBlockId(parentBlockId)
                .startIndex(request.getStartIndex())
                .endIndex(request.getEndIndex())
                .build();
    }

    public static FeishuElementUpdateCmd elementUpdateCmd(String documentId, String blockId, FeishuElementUpdateRequest request) {
        return FeishuElementUpdateCmd.builder()
                .documentId(documentId)
                .blockId(blockId)
                .elements(request.getElements())
                .documentRevisionId(request.getDocumentRevisionId())
                .build();
    }

    public static FeishuQuotaConfigCmd quotaConfigCmd(FeishuQuotaConfigRequest request) {
        return FeishuQuotaConfigCmd.builder()
                .defaultPerMinuteLimit(request.getDefaultPerMinuteLimit())
                .defaultDailyLimit(request.getDefaultDailyLimit())
                .build();
    }

    public static FeishuCloudDocPageRes toRes(FeishuCloudDocPageDTO dto) {
        return FeishuCloudDocPageRes.builder()
                .items(dto.getItems().stream().map(FeishuWebAssembler::toRes).toList())
                .nextPageToken(dto.getNextPageToken())
                .hasMore(dto.isHasMore())
                .build();
    }

    public static FeishuWikiSpacePageRes toRes(FeishuWikiSpacePageDTO dto) {
        return FeishuWikiSpacePageRes.builder()
                .items(dto.getItems().stream().map(FeishuWebAssembler::toRes).toList())
                .nextPageToken(dto.getNextPageToken())
                .hasMore(dto.isHasMore())
                .build();
    }

    public static FeishuWikiNodePageRes toRes(FeishuWikiNodePageDTO dto) {
        return FeishuWikiNodePageRes.builder()
                .spaceId(dto.getSpaceId())
                .items(dto.getItems().stream().map(FeishuWebAssembler::toRes).toList())
                .nextPageToken(dto.getNextPageToken())
                .hasMore(dto.isHasMore())
                .build();
    }

    public static FeishuDocumentMetaRes toRes(FeishuDocumentMetaDTO dto) {
        return FeishuDocumentMetaRes.builder()
                .documentId(dto.getDocumentId())
                .revisionId(dto.getRevisionId())
                .title(dto.getTitle())
                .build();
    }

    public static FeishuDocumentContentRes toRes(FeishuDocumentContentDTO dto) {
        return FeishuDocumentContentRes.builder()
                .documentId(dto.getDocumentId())
                .content(dto.getContent())
                .build();
    }

    public static FeishuDocumentBlockPageRes toRes(FeishuDocumentBlockPageDTO dto) {
        return FeishuDocumentBlockPageRes.builder()
                .documentId(dto.getDocumentId())
                .items(dto.getItems().stream().map(FeishuWebAssembler::toRes).toList())
                .nextPageToken(dto.getNextPageToken())
                .hasMore(dto.isHasMore())
                .build();
    }

    public static FeishuBlockWriteResultRes toRes(FeishuBlockWriteResultDTO dto) {
        return FeishuBlockWriteResultRes.builder()
                .documentId(dto.getDocumentId())
                .revisionId(dto.getRevisionId())
                .blockIds(dto.getBlockIds())
                .blocksRaw(dto.getBlocksRaw())
                .build();
    }

    public static FeishuQuotaRes toRes(FeishuQuotaDTO dto) {
        return FeishuQuotaRes.builder()
                .userId(dto.getUserId())
                .minuteKey(dto.getMinuteKey())
                .minuteUsed(dto.getMinuteUsed())
                .minuteLimit(dto.getMinuteLimit())
                .dailyKey(dto.getDailyKey())
                .dailyUsed(dto.getDailyUsed())
                .dailyLimit(dto.getDailyLimit())
                .build();
    }

    public static FeishuQuotaConfigRes toRes(FeishuQuotaConfigDTO dto) {
        return FeishuQuotaConfigRes.builder()
                .defaultPerMinuteLimit(dto.getDefaultPerMinuteLimit())
                .defaultDailyLimit(dto.getDefaultDailyLimit())
                .build();
    }

    private static FeishuCloudDocRes toRes(FeishuCloudDocDTO dto) {
        return FeishuCloudDocRes.builder()
                .token(dto.getToken())
                .name(dto.getName())
                .type(dto.getType())
                .parentToken(dto.getParentToken())
                .url(dto.getUrl())
                .shortcutTargetType(dto.getShortcutTargetType())
                .shortcutTargetToken(dto.getShortcutTargetToken())
                .createdTime(dto.getCreatedTime())
                .modifiedTime(dto.getModifiedTime())
                .ownerId(dto.getOwnerId())
                .build();
    }

    private static FeishuWikiSpaceRes toRes(FeishuWikiSpaceDTO dto) {
        return FeishuWikiSpaceRes.builder()
                .spaceId(dto.getSpaceId())
                .name(dto.getName())
                .description(dto.getDescription())
                .spaceType(dto.getSpaceType())
                .visibility(dto.getVisibility())
                .openSharing(dto.getOpenSharing())
                .build();
    }

    private static FeishuWikiNodeRes toRes(FeishuWikiNodeDTO dto) {
        return FeishuWikiNodeRes.builder()
                .nodeToken(dto.getNodeToken())
                .objToken(dto.getObjToken())
                .objType(dto.getObjType())
                .title(dto.getTitle())
                .hasChild(dto.isHasChild())
                .parentNodeToken(dto.getParentNodeToken())
                .nodeType(dto.getNodeType())
                .originNodeToken(dto.getOriginNodeToken())
                .originSpaceId(dto.getOriginSpaceId())
                .spaceId(dto.getSpaceId())
                .build();
    }

    private static FeishuDocumentBlockRes toRes(FeishuDocumentBlockDTO dto) {
        return FeishuDocumentBlockRes.builder()
                .blockId(dto.getBlockId())
                .parentId(dto.getParentId())
                .blockType(dto.getBlockType())
                .children(dto.getChildren())
                .raw(dto.getRaw())
                .build();
    }

    private static int size(Integer pageSize) {
        return pageSize == null ? 0 : pageSize;
    }
}
