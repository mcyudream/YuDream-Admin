package online.yudream.base.application.platform.feishu.service;

import online.yudream.base.application.platform.feishu.cmd.FeishuBlockAppendCmd;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodePageDTO;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentBlockQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDriveFileQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiNodeQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.exception.FeishuApiException;
import online.yudream.base.domain.platform.feishu.repo.FeishuCallQuotaRepo;
import online.yudream.base.domain.platform.feishu.service.FeishuActorGateway;
import online.yudream.base.domain.platform.feishu.service.FeishuCloudDocGateway;
import online.yudream.base.domain.platform.feishu.valobj.FeishuCloudDoc;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocPage;
import online.yudream.base.domain.platform.feishu.valobj.FeishuDocumentBlock;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;
import online.yudream.base.domain.platform.feishu.valobj.FeishuWikiNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 云文档用例：应用闸门、会话身份、调用前原子预占、失败按约定退还、每次调用只计一次额度、raw 保真。
 */
class FeishuAppServiceTest {

    private static final FeishuQuotaPolicy POLICY = new FeishuQuotaPolicy(20, 500);
    private static final FeishuQuotaWindowKeys WINDOWS = FeishuQuotaWindowKeys.of(Instant.parse("2026-10-16T03:00:00Z"));

    private final FeishuQuotaAppService quotas = mock(FeishuQuotaAppService.class);
    private final FeishuActorGateway actors = mock(FeishuActorGateway.class);
    private final FeishuCloudDocGateway cloudDocs = mock(FeishuCloudDocGateway.class);
    private final FeishuCallQuotaRepo quotaRepo = mock(FeishuCallQuotaRepo.class);
    private final FeishuAppService service = new FeishuAppService(quotas, actors, cloudDocs, quotaRepo);

    @BeforeEach
    void stubPolicy() {
        lenient().when(quotas.policy()).thenReturn(POLICY);
        lenient().when(actors.currentUserId()).thenReturn(7L);
        lenient().when(quotaRepo.reserve(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), eq(POLICY)))
                .thenReturn(Optional.of(WINDOWS));
    }

    @Test
    void rejectsWhenCapabilityDisabledBeforeAnyReservation() {
        doThrow(new BizException("飞书开放平台能力未启用，请先在平台能力中启用")).when(quotas).ensureEnabled();

        assertThatThrownBy(() -> service.driveFiles(new FeishuDriveFileQuery(null, null, 20)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("能力未启用");
        verify(quotaRepo, never()).reserve(any(), any(), any(), any());
        verifyNoInteractions(cloudDocs);
    }

    @Test
    void rejectsUnauthenticatedWithoutReservation() {
        when(actors.currentUserId()).thenReturn(null);

        assertThatThrownBy(() -> service.driveFiles(new FeishuDriveFileQuery(null, null, 20)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verify(quotaRepo, never()).reserve(any(), any(), any(), any());
        verifyNoInteractions(cloudDocs);
    }

    @Test
    void rejectsWhenQuotaExhaustedWithoutCallingFeishu() {
        when(quotaRepo.reserve(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.driveFiles(new FeishuDriveFileQuery(null, null, 20)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("额度已用完")
                .hasMessageContaining("20")
                .hasMessageContaining("500");
        verifyNoInteractions(cloudDocs);
    }

    @Test
    void reservesBeforeCallingFeishuAndKeepsCountOnSuccess() {
        when(cloudDocs.listDriveFiles("fldr1", "np0", 20)).thenReturn(new FeishuDocPage<>(
                List.of(new FeishuCloudDoc("tok1", "文档", "docx", "fldr0", "https://x", null, null, "1", "2", "ou_1")),
                "np1", true));

        FeishuCloudDocPageDTO page = service.driveFiles(new FeishuDriveFileQuery("fldr1", "np0", 20));

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getToken()).isEqualTo("tok1");
        assertThat(page.getNextPageToken()).isEqualTo("np1");
        assertThat(page.isHasMore()).isTrue();
        InOrder order = inOrder(quotaRepo, cloudDocs);
        order.verify(quotaRepo).reserve(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), eq(POLICY));
        order.verify(cloudDocs).listDriveFiles("fldr1", "np0", 20);
        verify(quotaRepo, never()).refund(any(), any(), any());
    }

    @Test
    void countsEveryPagedRequestSeparately() {
        when(cloudDocs.listDriveFiles(any(), any(), anyInt())).thenReturn(FeishuDocPage.empty());

        service.driveFiles(new FeishuDriveFileQuery(null, null, 20));
        service.driveFiles(new FeishuDriveFileQuery(null, "np1", 20));

        verify(quotaRepo, org.mockito.Mockito.times(2)).reserve(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), eq(POLICY));
    }

    @Test
    void refundsWhenFeishuRejectsWithThrottle() {
        when(cloudDocs.documentBlocks("doxcnA", null, 100))
                .thenThrow(new FeishuApiException("飞书接口限频（获取文档块），请稍后重试（code=99991400）", 99991400, 400));

        assertThatThrownBy(() -> service.documentBlocks(new FeishuDocumentBlockQuery("doxcnA", null, 100)))
                .isInstanceOf(FeishuApiException.class)
                .hasMessageContaining("限频");
        verify(quotaRepo).refund(7L, FeishuQuotaScope.CLOUD_DOC, WINDOWS);
    }

    @Test
    void wikiNodesReserveOnceBeforeCallAndKeepCountOnSuccess() {
        when(cloudDocs.listWikiNodes("spc1", "wikcnRoot", "np0", 50)).thenReturn(new FeishuDocPage<>(
                List.of(new FeishuWikiNode("wikcnNode1", "doxcnObj1", "docx", "设计文档", true,
                        "wikcnRoot", "origin", "wikcnOrigin", "spc0", "spc1")),
                "npt1", true));

        FeishuWikiNodePageDTO page = service.wikiNodes(new FeishuWikiNodeQuery("spc1", "wikcnRoot", "np0", 50));

        assertThat(page.getSpaceId()).isEqualTo("spc1");
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getNodeToken()).isEqualTo("wikcnNode1");
        assertThat(page.getItems().get(0).getObjToken()).isEqualTo("doxcnObj1");
        assertThat(page.getItems().get(0).isHasChild()).isTrue();
        assertThat(page.getItems().get(0).getParentNodeToken()).isEqualTo("wikcnRoot");
        assertThat(page.getNextPageToken()).isEqualTo("npt1");
        assertThat(page.isHasMore()).isTrue();
        InOrder order = inOrder(quotaRepo, cloudDocs);
        order.verify(quotaRepo).reserve(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), eq(POLICY));
        order.verify(cloudDocs).listWikiNodes("spc1", "wikcnRoot", "np0", 50);
        verify(quotaRepo, never()).refund(any(), any(), any());
    }

    @Test
    void wikiNodesRefundWhenFeishuRejectsBeforeExecuting() {
        when(cloudDocs.listWikiNodes("spc1", "", "", 20))
                .thenThrow(new FeishuApiException("飞书接口调用失败（列出知识库节点）：HTTP 403（code=1770001）", 1770001, 403));

        assertThatThrownBy(() -> service.wikiNodes(new FeishuWikiNodeQuery("spc1", "", "", 20)))
                .isInstanceOf(FeishuApiException.class);
        verify(quotaRepo).refund(7L, FeishuQuotaScope.CLOUD_DOC, WINDOWS);
    }

    @Test
    void keepsCountWhenFailureCannotBeProvenUnExecuted() {
        when(cloudDocs.documentBlocks("doxcnA", null, 100))
                .thenThrow(new FeishuApiException("飞书接口调用失败（获取文档块）：HTTP 200（code=1770001，block not found）", 1770001, 200));

        assertThatThrownBy(() -> service.documentBlocks(new FeishuDocumentBlockQuery("doxcnA", null, 100)))
                .isInstanceOf(FeishuApiException.class);
        verify(quotaRepo, never()).refund(any(), any(), any());
    }

    @Test
    void passesRawBlockJsonThroughUnchanged() {
        String raw = "{\"block_id\":\"doxcnB\",\"parent_id\":\"doxcnA\",\"children\":[\"doxcnC\"],\"block_type\":2,"
                + "\"text\":{\"elements\":[{\"text_run\":{\"content\":\"原始文本\"}}]}}";
        when(cloudDocs.documentBlocks("doxcnA", "bp0", 50)).thenReturn(new FeishuDocPage<>(
                List.of(new FeishuDocumentBlock("doxcnB", "doxcnA", 2, List.of("doxcnC"), raw)), "bp1", true));

        FeishuDocumentBlockPageDTO page = service.documentBlocks(new FeishuDocumentBlockQuery("doxcnA", "bp0", 50));

        assertThat(page.getItems().get(0).getRaw()).isEqualTo(raw);
        assertThat(page.getItems().get(0).getChildren()).containsExactly("doxcnC");
    }

    @Test
    void appendBlocksCountsOnceAndReturnsRawResult() {
        String childJson = "{\"block_type\":2,\"text\":{\"elements\":[{\"text_run\":{\"content\":\"新块\"}}]}}";
        when(cloudDocs.appendDocumentBlocks("doxcnA", "doxcnB", List.of(childJson), 3))
                .thenReturn(new online.yudream.base.domain.platform.feishu.valobj.FeishuBlockWriteResult(
                        "doxcnA", "43", List.of("doxcnD"), List.of("{\"block_id\":\"doxcnD\"}")));

        var result = service.appendBlocks(FeishuBlockAppendCmd.builder()
                .documentId("doxcnA").parentBlockId("doxcnB").children(List.of(childJson)).index(3).build());

        assertThat(result.getBlockIds()).containsExactly("doxcnD");
        assertThat(result.getBlocksRaw()).containsExactly("{\"block_id\":\"doxcnD\"}");
        verify(quotaRepo, org.mockito.Mockito.times(1)).reserve(eq(7L), eq(FeishuQuotaScope.CLOUD_DOC), any(Instant.class), eq(POLICY));
    }
}
