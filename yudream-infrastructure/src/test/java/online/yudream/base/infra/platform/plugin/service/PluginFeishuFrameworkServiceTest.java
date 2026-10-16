package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockAppendCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockDeleteCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuElementUpdateCmd;
import online.yudream.base.application.platform.feishu.dto.FeishuBlockWriteResultDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentContentDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentMetaDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodeDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodePageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpaceDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpacePageDTO;
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
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuWikiNode;
import online.yudream.base.plugin.spi.system.feishu.PluginFeishuWikiSpace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书 SPI 适配映射：宿主 DTO 到插件端口 DTO 的保真转换与能力开关判定。
 */
class PluginFeishuFrameworkServiceTest {

    /** 记录调用参数的飞书用例桩：只覆盖 SPI 映射用到的入口。 */
    private static final class StubAppService extends FeishuAppService {
        private final AtomicReference<FeishuBlockAppendCmd> appendCmd = new AtomicReference<>();
        private final AtomicReference<FeishuElementUpdateCmd> elementCmd = new AtomicReference<>();
        private final AtomicReference<FeishuBlockDeleteCmd> deleteCmd = new AtomicReference<>();
        private final AtomicReference<FeishuWikiNodeQuery> wikiNodeQuery = new AtomicReference<>();

        StubAppService() {
            super(null, null, null, null);
        }

        @Override
        public FeishuCloudDocPageDTO driveFiles(FeishuDriveFileQuery query) {
            return FeishuCloudDocPageDTO.builder()
                    .items(List.of(FeishuCloudDocDTO.builder()
                            .token("doccnX").name("需求").type("docx").parentToken("fldroot").url("https://x")
                            .shortcutTargetType("").shortcutTargetToken("").createdTime("1700000000")
                            .modifiedTime("1700000001").ownerId("ou_1").build()))
                    .nextPageToken("next-1").hasMore(true).build();
        }

        @Override
        public FeishuWikiSpacePageDTO wikiSpaces(FeishuWikiSpaceQuery query) {
            return FeishuWikiSpacePageDTO.builder()
                    .items(List.of(FeishuWikiSpaceDTO.builder()
                            .spaceId("7001").name("知识库").description("desc")
                            .spaceType("team").visibility("public").openSharing("open").build()))
                    .nextPageToken("").hasMore(false).build();
        }

        @Override
        public FeishuWikiNodePageDTO wikiNodes(FeishuWikiNodeQuery query) {
            wikiNodeQuery.set(query);
            return FeishuWikiNodePageDTO.builder()
                    .spaceId(query.spaceId())
                    .items(List.of(FeishuWikiNodeDTO.builder()
                            .nodeToken("wikcnNode1").objToken("doxcnObj1").objType("docx").title("设计文档")
                            .hasChild(true).parentNodeToken("wikcnRoot").nodeType("origin")
                            .originNodeToken("wikcnOrigin").originSpaceId("spc0").spaceId(query.spaceId()).build()))
                    .nextPageToken("npt1").hasMore(true).build();
        }

        @Override
        public FeishuDocumentMetaDTO documentMeta(FeishuDocumentQuery query) {
            return FeishuDocumentMetaDTO.builder()
                    .documentId(query.documentId()).revisionId("87").title("标题").build();
        }

        @Override
        public FeishuDocumentContentDTO documentContent(FeishuDocumentQuery query) {
            return FeishuDocumentContentDTO.builder()
                    .documentId(query.documentId()).content("# 标题\n正文").build();
        }

        @Override
        public FeishuDocumentBlockPageDTO documentBlocks(FeishuDocumentBlockQuery query) {
            return FeishuDocumentBlockPageDTO.builder()
                    .documentId(query.documentId())
                    .items(List.of(FeishuDocumentBlockDTO.builder()
                            .blockId("blk1").parentId("blk0").blockType(2)
                            .children(List.of("blk2")).raw("{\"block_id\":\"blk1\",\"block_type\":2}").build()))
                    .nextPageToken("blk-next").hasMore(true).build();
        }

        @Override
        public FeishuBlockWriteResultDTO appendBlocks(FeishuBlockAppendCmd cmd) {
            appendCmd.set(cmd);
            return FeishuBlockWriteResultDTO.builder()
                    .documentId(cmd.getDocumentId()).revisionId("88")
                    .blockIds(List.of("blk-new"))
                    .blocksRaw(List.of("{\"block_id\":\"blk-new\"}")).build();
        }

        @Override
        public FeishuBlockWriteResultDTO updateBlockElements(FeishuElementUpdateCmd cmd) {
            elementCmd.set(cmd);
            return FeishuBlockWriteResultDTO.builder()
                    .documentId(cmd.getDocumentId()).revisionId("89")
                    .blockIds(List.of(cmd.getBlockId()))
                    .blocksRaw(List.of("{\"block_id\":\"blk1\",\"text\":{\"elements\":[]}}")).build();
        }

        @Override
        public void deleteBlocks(FeishuBlockDeleteCmd cmd) {
            deleteCmd.set(cmd);
        }
    }

    private static final class StubCapabilityAppService extends CapabilityAppService {
        private final boolean enabled;

        StubCapabilityAppService(boolean enabled) {
            super(null, List.of());
            this.enabled = enabled;
        }

        @Override
        public boolean enabled(String code) {
            return enabled && "feishu".equals(code);
        }
    }

    private static final class StubOpenApiClient extends FeishuOpenApiClient {
        private final boolean configured;

        StubOpenApiClient(boolean configured) {
            super(new ObjectMapper(), null);
            this.configured = configured;
        }

        @Override
        public boolean configured() {
            return configured;
        }
    }

    private static PluginFeishuFrameworkService service(StubAppService app, boolean capabilityEnabled, boolean configured) {
        return new PluginFeishuFrameworkService(app, new StubCapabilityAppService(capabilityEnabled),
                new StubOpenApiClient(configured));
    }

    @Test
    void enabledRequiresCapabilityGateAndCredentials() {
        assertTrue(service(new StubAppService(), true, true).enabled());
        assertFalse(service(new StubAppService(), true, false).enabled());
        assertFalse(service(new StubAppService(), false, true).enabled());
    }

    @Test
    void readsMapHostDtosToPluginViews() {
        StubAppService app = new StubAppService();
        PluginFeishuFrameworkService service = service(app, true, true);

        PluginFeishuDocPage<PluginFeishuDriveFile> files = service.driveFiles("fldroot", "", 20);
        assertEquals(1, files.items().size());
        PluginFeishuDriveFile file = files.items().get(0);
        assertEquals("doccnX", file.token());
        assertEquals("需求", file.name());
        assertEquals("docx", file.type());
        assertEquals("1700000001", file.modifiedTime());
        assertEquals("ou_1", file.ownerId());
        assertEquals("next-1", files.nextPageToken());
        assertTrue(files.hasMore());

        PluginFeishuDocPage<PluginFeishuWikiSpace> spaces = service.wikiSpaces("", 20);
        assertEquals("7001", spaces.items().get(0).spaceId());
        assertEquals("知识库", spaces.items().get(0).name());
        assertFalse(spaces.hasMore());

        PluginFeishuDocPage<PluginFeishuWikiNode> nodes = service.wikiNodes("7001", "wikcnRoot", "np0", 50);
        assertEquals(1, nodes.items().size());
        PluginFeishuWikiNode node = nodes.items().get(0);
        assertEquals("wikcnNode1", node.nodeToken());
        assertEquals("doxcnObj1", node.objToken());
        assertEquals("docx", node.objType());
        assertEquals("设计文档", node.title());
        assertTrue(node.hasChild());
        assertEquals("wikcnRoot", node.parentNodeToken());
        assertEquals("origin", node.nodeType());
        assertEquals("wikcnOrigin", node.originNodeToken());
        assertEquals("spc0", node.originSpaceId());
        assertEquals("7001", node.spaceId());
        assertEquals("npt1", nodes.nextPageToken());
        assertTrue(nodes.hasMore());
        // 入参原样透传到应用用例（分页与父节点游标不做改写）
        assertEquals("7001", app.wikiNodeQuery.get().spaceId());
        assertEquals("wikcnRoot", app.wikiNodeQuery.get().parentNodeToken());
        assertEquals("np0", app.wikiNodeQuery.get().pageToken());
        assertEquals(50, app.wikiNodeQuery.get().pageSize());

        PluginFeishuDocument document = service.document("doccnX");
        assertEquals("87", document.revisionId());
        assertEquals("标题", document.title());
        assertEquals("# 标题\n正文", service.documentContent("doccnX", 1));

        PluginFeishuDocPage<PluginFeishuDocBlock> blocks = service.documentBlocks("doccnX", "", 100);
        PluginFeishuDocBlock block = blocks.items().get(0);
        assertEquals("blk1", block.blockId());
        assertEquals("blk0", block.parentBlockId());
        assertEquals(2, block.blockType());
        assertEquals(List.of("blk2"), block.children());
        // 原始块 JSON 必须原样透传，供插件读改写回
        assertEquals("{\"block_id\":\"blk1\",\"block_type\":2}", block.rawJson());
        assertEquals("blk-next", blocks.nextPageToken());
    }

    @Test
    void writesForwardRawJsonAndOptionalParameters() {
        StubAppService app = new StubAppService();
        PluginFeishuFrameworkService service = service(app, true, true);

        PluginFeishuBlockWriteResult created = service.createBlockChildren("doccnX", "blk0",
                List.of("{\"block_type\":2,\"text\":{\"elements\":[]}}"), null);
        assertEquals("88", created.revisionId());
        assertEquals(List.of("blk-new"), created.blockIds());
        assertEquals("{\"block_id\":\"blk-new\"}", created.blocksJson().get(0));
        assertEquals("doccnX", app.appendCmd.get().getDocumentId());
        assertEquals("blk0", app.appendCmd.get().getParentBlockId());
        assertNull(app.appendCmd.get().getIndex());

        PluginFeishuBlockWriteResult updated = service.updateBlockElements("doccnX", "blk1",
                List.of("{\"text_run\":{\"content\":\"新文本\"}}"), null);
        assertEquals("89", updated.revisionId());
        assertNull(app.elementCmd.get().getDocumentRevisionId());
        assertEquals(List.of("{\"text_run\":{\"content\":\"新文本\"}}"), app.elementCmd.get().getElements());

        service.deleteBlockChildren("doccnX", "blk0", 0, 2);
        assertEquals(0, app.deleteCmd.get().getStartIndex());
        assertEquals(2, app.deleteCmd.get().getEndIndex());
    }

    @Test
    void unavailableHostDefaultsFailClosed() {
        var defaults = new online.yudream.base.plugin.spi.system.feishu.PluginFeishuService() {
        };
        assertFalse(defaults.enabled());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> defaults.driveFiles("", "", 10));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> defaults.wikiNodes("spc1", "", "", 10));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> defaults.createBlockChildren("doc", "blk", List.of("{}"), null));
    }
}
