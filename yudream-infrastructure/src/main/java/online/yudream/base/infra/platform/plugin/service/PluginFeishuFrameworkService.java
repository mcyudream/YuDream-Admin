package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockAppendCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockDeleteCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuElementUpdateCmd;
import online.yudream.base.application.platform.feishu.dto.FeishuBlockWriteResultDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentMetaDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodeDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpaceDTO;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentBlockQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDriveFileQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiNodeQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiSpaceQuery;
import online.yudream.base.application.platform.feishu.service.FeishuAppService;
import online.yudream.base.infra.platform.feishu.service.FeishuOpenApiClient;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuBlockWriteResult;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuDocBlock;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuDocPage;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuDocument;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuDriveFile;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuService;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuWikiNode;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuWikiSpace;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 飞书云文档 SPI 实现（SPI 2.37.0）：把插件端口映射到宿主飞书用例。
 * <p>
 * 所有调用都经 {@link FeishuAppService}，复用同一条「能力应用闸门 → 会话身份 → 调用前原子
 * 预占额度 → 按约定退还」链路；插件与宿主页面共用同一额度计数，不存在绕过计数的旁路。
 * 本类不做任何额度和身份判断，映射保持飞书字符串与原始 JSON 原样透传。
 */
@Service
@RequiredArgsConstructor
public class PluginFeishuFrameworkService implements PluginFeishuService {

    private final FeishuAppService feishuAppService;
    private final CapabilityAppService capabilityAppService;
    private final FeishuOpenApiClient feishuOpenApiClient;

    @Override
    public boolean enabled() {
        return capabilityAppService.enabled(CAPABILITY_CODE) && feishuOpenApiClient.configured();
    }

    @Override
    public PluginFeishuDocPage<PluginFeishuDriveFile> driveFiles(String folderToken, String pageToken, int pageSize) {
        var page = feishuAppService.driveFiles(new FeishuDriveFileQuery(folderToken, pageToken, pageSize));
        return new PluginFeishuDocPage<>(
                page.getItems() == null ? List.of() : page.getItems().stream().map(this::driveFile).toList(),
                page.getNextPageToken(), page.isHasMore());
    }

    @Override
    public PluginFeishuDocPage<PluginFeishuWikiSpace> wikiSpaces(String pageToken, int pageSize) {
        var page = feishuAppService.wikiSpaces(new FeishuWikiSpaceQuery(pageToken, pageSize));
        return new PluginFeishuDocPage<>(
                page.getItems() == null ? List.of() : page.getItems().stream().map(this::wikiSpace).toList(),
                page.getNextPageToken(), page.isHasMore());
    }

    @Override
    public PluginFeishuDocPage<PluginFeishuWikiNode> wikiNodes(String spaceId, String parentNodeToken,
                                                              String pageToken, int pageSize) {
        var page = feishuAppService.wikiNodes(new FeishuWikiNodeQuery(spaceId, parentNodeToken, pageToken, pageSize));
        return new PluginFeishuDocPage<>(
                page.getItems() == null ? List.of() : page.getItems().stream().map(this::wikiNode).toList(),
                page.getNextPageToken(), page.isHasMore());
    }

    @Override
    public PluginFeishuDocument document(String documentId) {
        return document(feishuAppService.documentMeta(new FeishuDocumentQuery(documentId, null)));
    }

    @Override
    public String documentContent(String documentId, Integer lang) {
        var content = feishuAppService.documentContent(new FeishuDocumentQuery(documentId, lang));
        return content == null || content.getContent() == null ? "" : content.getContent();
    }

    @Override
    public PluginFeishuDocPage<PluginFeishuDocBlock> documentBlocks(String documentId, String pageToken, int pageSize) {
        var page = feishuAppService.documentBlocks(new FeishuDocumentBlockQuery(documentId, pageToken, pageSize));
        return new PluginFeishuDocPage<>(
                page.getItems() == null ? List.of() : page.getItems().stream().map(this::docBlock).toList(),
                page.getNextPageToken(), page.isHasMore());
    }

    @Override
    public PluginFeishuBlockWriteResult createBlockChildren(String documentId, String parentBlockId,
                                                            List<String> childrenJson, Integer index) {
        return writeResult(feishuAppService.appendBlocks(FeishuBlockAppendCmd.builder()
                .documentId(documentId)
                .parentBlockId(parentBlockId)
                .children(childrenJson)
                .index(index)
                .build()));
    }

    @Override
    public void deleteBlockChildren(String documentId, String parentBlockId, int startIndex, int endIndex) {
        feishuAppService.deleteBlocks(FeishuBlockDeleteCmd.builder()
                .documentId(documentId)
                .parentBlockId(parentBlockId)
                .startIndex(startIndex)
                .endIndex(endIndex)
                .build());
    }

    @Override
    public PluginFeishuBlockWriteResult updateBlockElements(String documentId, String blockId,
                                                            List<String> elementsJson, Long documentRevisionId) {
        return writeResult(feishuAppService.updateBlockElements(FeishuElementUpdateCmd.builder()
                .documentId(documentId)
                .blockId(blockId)
                .elements(elementsJson)
                .documentRevisionId(documentRevisionId)
                .build()));
    }

    private PluginFeishuDriveFile driveFile(FeishuCloudDocDTO dto) {
        return new PluginFeishuDriveFile(dto.getToken(), dto.getName(), dto.getType(), dto.getParentToken(),
                dto.getUrl(), dto.getShortcutTargetType(), dto.getShortcutTargetToken(),
                dto.getCreatedTime(), dto.getModifiedTime(), dto.getOwnerId());
    }

    private PluginFeishuWikiSpace wikiSpace(FeishuWikiSpaceDTO dto) {
        return new PluginFeishuWikiSpace(dto.getSpaceId(), dto.getName(), dto.getDescription(),
                dto.getSpaceType(), dto.getVisibility(), dto.getOpenSharing());
    }

    private PluginFeishuWikiNode wikiNode(FeishuWikiNodeDTO dto) {
        return new PluginFeishuWikiNode(dto.getNodeToken(), dto.getObjToken(), dto.getObjType(),
                dto.getTitle(), dto.isHasChild(), dto.getParentNodeToken(), dto.getNodeType(),
                dto.getOriginNodeToken(), dto.getOriginSpaceId(), dto.getSpaceId());
    }

    private PluginFeishuDocument document(FeishuDocumentMetaDTO dto) {
        return new PluginFeishuDocument(dto.getDocumentId(), dto.getRevisionId(), dto.getTitle());
    }

    private PluginFeishuDocBlock docBlock(FeishuDocumentBlockDTO dto) {
        return new PluginFeishuDocBlock(dto.getBlockId(), dto.getParentId(), dto.getBlockType(),
                dto.getChildren(), dto.getRaw());
    }

    private PluginFeishuBlockWriteResult writeResult(FeishuBlockWriteResultDTO dto) {
        return new PluginFeishuBlockWriteResult(dto.getDocumentId(), dto.getRevisionId(),
                dto.getBlockIds(), dto.getBlocksRaw());
    }
}
