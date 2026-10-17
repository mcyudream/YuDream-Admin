package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.cmd.WikiCommentAnchorInput;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentCreateCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiCommentUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiPublicCommentPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentAiVerdict;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentReviewMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextAnchorState;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiCommentModerationGateway;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiCommentModerationVerdict;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 公开端评论：空间开关与发布状态 fail closed、作者只取会话、默认待审核、同页浅线程、
 * 锚点服务端定版（版本/哈希不接受客户端输入）、回复继承根锚点、编辑/删除在页面取消发布后 fail closed。
 */
class WikiCommentAppServiceTest {

    private static final WikiActor AUTHOR = WikiActor.of(100L, 7L, "小明", false);
    private static final String PUBLISHED_MARKDOWN = """
            ---
            title: 页面
            type: concept
            ---
            欢迎来到快速开始页面。
            更多说明在这里，补充说明在文末。
            """;

    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versions = mock(WikiPageVersionRepo.class);
    private final WikiCommentRepo comments = mock(WikiCommentRepo.class);
    private final WikiActorGateway actors = mock(WikiActorGateway.class);
    private final WikiSpaceAclAppService access = mock(WikiSpaceAclAppService.class);
    private final WikiCommentModerationGateway moderation = mock(WikiCommentModerationGateway.class);
    private final WikiCommentAppService service = new WikiCommentAppService(spaces, nodes, versions, comments,
            actors, access, moderation);

    @Test
    void createFailsClosedWhenCommentDisabledOrSpaceMissing() {
        WikiSpace space = publicSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, null, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未开放评论");

        when(spaces.findBySlug("demo")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, null, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("知识库不存在");
    }

    @Test
    void createRequiresAuthenticatedUserAndNeverTrustsRequestAuthor() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L)));
        when(actors.currentActor()).thenReturn(null);

        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, null, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先登录");
        verify(comments, never()).save(any());
    }

    @Test
    void createRejectsUnpublishedNodeOrForeignSpaceNode() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(actors.currentActor()).thenReturn(AUTHOR);

        WikiNode draft = WikiNode.page(10L, null, "草稿", "draft", 0);
        draft.setId(20L);
        when(nodes.findById(20L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, null, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");

        WikiNode foreign = publishedNode(99L, 20L);
        when(nodes.findById(20L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, null, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
    }

    @Test
    void createStoresPendingCommentWithSessionAuthor() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L)));
        when(actors.currentActor()).thenReturn(AUTHOR);
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiCommentDTO dto = service.create(createCmd("demo", 20L, null, "  正文  "));

        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);
        verify(comments).save(captor.capture());
        WikiComment saved = captor.getValue();
        assertThat(saved.getAuthorUserId()).isEqualTo(100L);
        assertThat(saved.getAuthorName()).isEqualTo("小明");
        assertThat(saved.getStatus()).isEqualTo(WikiCommentStatus.PENDING);
        assertThat(saved.getContent()).isEqualTo("正文");
        assertThat(dto.getStatus()).isEqualTo(WikiCommentStatus.PENDING.name());
    }

    @Test
    void createRejectsReplyToReplyAndToUnpublishedParent() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L)));
        when(actors.currentActor()).thenReturn(AUTHOR);

        WikiComment nested = WikiComment.create(10L, 20L, 30L, 100L, "小明", "二级回复");
        nested.setId(31L);
        nested.publish(7L);
        when(comments.findById(31L)).thenReturn(Optional.of(nested));
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, 31L, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不再嵌套");

        WikiComment pendingParent = WikiComment.create(10L, 20L, null, 100L, "小明", "待审核");
        pendingParent.setId(32L);
        when(comments.findById(32L)).thenReturn(Optional.of(pendingParent));
        assertThatThrownBy(() -> service.create(createCmd("demo", 20L, 32L, "内容")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能回复已发布的评论");
    }

    @Test
    void pageOnlyAsksPublishedRootsAndGroupsPublishedReplies() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L)));

        WikiComment root = WikiComment.create(10L, 20L, null, 100L, "小明", "根评论");
        root.setId(1L);
        root.publish(7L);
        WikiComment reply = WikiComment.create(10L, 20L, 1L, 101L, "小红", "回复");
        reply.setId(2L);
        reply.publish(7L);
        WikiComment hiddenReply = WikiComment.create(10L, 20L, 1L, 102L, "小刚", "被隐藏");
        hiddenReply.setId(3L);
        hiddenReply.publish(7L);
        hiddenReply.hide(7L);

        when(comments.pagePublishedRoots(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(root), 1, 1, 10));
        when(comments.findPublishedReplies(anyLong(), anyLong(), any())).thenReturn(List.of(reply));

        PageResult<WikiCommentDTO> page = service.page("demo", 20L, new WikiPublicCommentPageQuery());

        assertThat(page.getRecords()).singleElement().satisfies(dto -> {
            assertThat(dto.getContent()).isEqualTo("根评论");
            assertThat(dto.getReviewedByUserId()).isNull();
            assertThat(dto.getReplies()).singleElement().satisfies(item -> {
                assertThat(item.getContent()).isEqualTo("回复");
                assertThat(item.getReplies()).isEmpty();
            });
        });
    }

    @Test
    void updateAndDeleteRequireOriginalAuthor() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L)));
        WikiComment comment = WikiComment.create(10L, 20L, null, 100L, "小明", "原始");
        comment.setId(5L);
        comment.publish(7L);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));

        when(actors.currentActor()).thenReturn(WikiActor.of(200L, 8L, "别人", false));
        WikiCommentUpdateCmd cmd = new WikiCommentUpdateCmd();
        cmd.setSpaceSlug("demo");
        cmd.setCommentId(5L);
        cmd.setContent("篡改");
        assertThatThrownBy(() -> service.update(cmd)).isInstanceOf(BizException.class)
                .hasMessageContaining("只能编辑本人评论");

        when(actors.currentActor()).thenReturn(AUTHOR);
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.update(cmd).getContent()).isEqualTo("篡改");

        service.delete("demo", 5L);
        assertThat(comment.getStatus()).isEqualTo(WikiCommentStatus.DELETED);
    }

    @Test
    void createStampsAnchorFromCurrentPublishedVersionAndIgnoresClientVersionFields() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        WikiPageVersion published = version(10L, 20L, PUBLISHED_MARKDOWN, "hash-1");
        when(versions.findById(200L)).thenReturn(Optional.of(published));
        when(actors.currentActor()).thenReturn(AUTHOR);
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiCommentCreateCmd cmd = createCmd("demo", 20L, null, "这里需要说明");
        WikiCommentAnchorInput anchorInput = anchorInput("快速开始", "欢迎来到", "页面。");
        anchorInput.setBlockOrdinal(4);
        anchorInput.setBlockType("tableCell");
        anchorInput.setTableRow(1);
        anchorInput.setTableCell(2);
        anchorInput.setStartOffset(3);
        anchorInput.setEndOffset(8);
        cmd.setAnchor(anchorInput);

        WikiCommentDTO dto = service.create(cmd);

        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);
        verify(comments).save(captor.capture());
        WikiTextAnchor anchor = captor.getValue().getAnchor();
        assertThat(anchor).isNotNull();
        assertThat(anchor.exact()).isEqualTo("快速开始");
        assertThat(anchor.prefix()).isEqualTo("欢迎来到");
        assertThat(anchor.suffix()).isEqualTo("页面。");
        assertThat(anchor.blockHint().type().wireValue()).isEqualTo("tableCell");
        assertThat(anchor.blockHint().ordinal()).isEqualTo(4);
        assertThat(anchor.blockHint().tableRow()).isEqualTo(1);
        assertThat(anchor.blockHint().tableCell()).isEqualTo(2);
        assertThat(anchor.startOffset()).isEqualTo(3);
        assertThat(anchor.endOffset()).isEqualTo(8);
        // 版本/修订/哈希只能是服务端按当前已发布版本盖章的结果
        assertThat(anchor.versionId()).isEqualTo(200L);
        assertThat(anchor.revision()).isEqualTo(1L);
        assertThat(anchor.contentHash()).isEqualTo("hash-1");
        assertThat(dto.getAnchor().getExact()).isEqualTo("快速开始");
        assertThat(dto.getAnchor().getBlockType()).isEqualTo("tableCell");
        assertThat(dto.getVersionId()).isEqualTo("200");
        assertThat(dto.getRevision()).isEqualTo("1");
        assertThat(dto.getContentHash()).isEqualTo("hash-1");
        assertThat(dto.getAnchorState()).isEqualTo(WikiTextAnchorState.ACTIVE.wireValue());
        // 公开视图不暴露空间/作者身份
        assertThat(dto.getSpaceId()).isNull();
        assertThat(dto.getAuthorUserId()).isNull();
    }

    @Test
    void createRejectsAnchorThatCannotBeUniquelyResolvedInPublishedBody() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L, PUBLISHED_MARKDOWN, "hash-1")));
        when(actors.currentActor()).thenReturn(AUTHOR);

        WikiCommentCreateCmd missing = createCmd("demo", 20L, null, "内容");
        missing.setAnchor(anchorInput("不存在的引用", null, null));
        assertThatThrownBy(() -> service.create(missing))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法在当前已发布版本中唯一定位");

        // 正文出现两次且无上下文消歧：拒绝，而不是随便挑一处
        WikiCommentCreateCmd ambiguous = createCmd("demo", 20L, null, "内容");
        ambiguous.setAnchor(anchorInput("说明", null, null));
        assertThatThrownBy(() -> service.create(ambiguous))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法在当前已发布版本中唯一定位");

        verify(comments, never()).save(any());
    }

    @Test
    void replyCannotSendAnotherAnchorAndInheritsRootAnchor() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L, PUBLISHED_MARKDOWN, "hash-1")));
        when(actors.currentActor()).thenReturn(AUTHOR);

        WikiTextAnchor rootAnchor = stampedAnchor("快速开始", "hash-1");
        WikiComment root = WikiComment.create(10L, 20L, null, 100L, "小明", "根评论", rootAnchor);
        root.setId(31L);
        root.publish(7L);
        when(comments.findById(31L)).thenReturn(Optional.of(root));

        WikiCommentCreateCmd withAnchor = createCmd("demo", 20L, 31L, "回复");
        withAnchor.setAnchor(anchorInput("快速开始", null, null));
        assertThatThrownBy(() -> service.create(withAnchor))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("回复评论不能自定义锚点");
        verify(comments, never()).save(any());

        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WikiCommentDTO reply = service.create(createCmd("demo", 20L, 31L, "回复"));

        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);
        verify(comments).save(captor.capture());
        assertThat(captor.getValue().getAnchor()).isEqualTo(rootAnchor);
        assertThat(reply.getAnchor().getExact()).isEqualTo("快速开始");
    }

    @Test
    void pageComputesAnchorStateAndKeepsStaleCommentsVisible() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(10L, 20L, PUBLISHED_MARKDOWN, "hash-2")));

        WikiComment active = publishedRoot(1L, "命中当前版本", stampedAnchor("快速开始", "hash-2"));
        WikiComment reanchorable = publishedRoot(2L, "页面已改版但引用还在", stampedAnchor("快速开始", "hash-1"));
        WikiComment orphaned = publishedRoot(3L, "引用已删除", stampedAnchor("已删除的段落", "hash-1"));
        WikiComment legacy = publishedRoot(4L, "历史无锚点评论", null);
        when(comments.pagePublishedRoots(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(active, reanchorable, orphaned, legacy), 4, 1, 10));
        when(comments.findPublishedReplies(anyLong(), anyLong(), any())).thenReturn(List.of());

        PageResult<WikiCommentDTO> page = service.page("demo", 20L, new WikiPublicCommentPageQuery());

        assertThat(page.getRecords()).hasSize(4);
        assertThat(page.getRecords().get(0).getAnchorState()).isEqualTo(WikiTextAnchorState.ACTIVE.wireValue());
        assertThat(page.getRecords().get(1).getAnchorState()).isEqualTo(WikiTextAnchorState.REANCHORABLE.wireValue());
        assertThat(page.getRecords().get(2).getAnchorState()).isEqualTo(WikiTextAnchorState.ORPHANED.wireValue());
        // 失配锚点不隐藏评论，只返回状态；历史无锚点评论保持兼容
        assertThat(page.getRecords().get(2).getContent()).isEqualTo("引用已删除");
        assertThat(page.getRecords().get(3).getAnchor()).isNull();
        assertThat(page.getRecords().get(3).getAnchorState()).isNull();
        assertThat(page.getRecords().get(3).getVersionId()).isNull();
        assertThat(page.getRecords().get(3).getSpaceId()).isNull();
        assertThat(page.getRecords().get(3).getAuthorUserId()).isNull();
    }

    @Test
    void updateAndDeleteFailClosedWhenNodeIsNoLongerPublished() {
        WikiSpace space = commentableSpace(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        WikiComment comment = WikiComment.create(10L, 20L, null, 100L, "小明", "原始");
        comment.setId(5L);
        comment.publish(7L);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));
        when(actors.currentActor()).thenReturn(AUTHOR);

        WikiNode unpublished = WikiNode.page(space.getId(), null, "页面", "page", 0);
        unpublished.setId(20L);
        unpublished.setPublishedVersionId(null);
        when(nodes.findById(20L)).thenReturn(Optional.of(unpublished));

        WikiCommentUpdateCmd cmd = new WikiCommentUpdateCmd();
        cmd.setSpaceSlug("demo");
        cmd.setCommentId(5L);
        cmd.setContent("编辑");
        assertThatThrownBy(() -> service.update(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
        assertThatThrownBy(() -> service.delete("demo", 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");

        assertThat(comment.getStatus()).isEqualTo(WikiCommentStatus.PUBLISHED);
        assertThat(comment.getContent()).isEqualTo("原始");
        verify(comments, never()).save(any());
    }

    @Test
    void aiReviewPublishesApprovedCommentsAndKeepsTheRestPendingForHumans() {
        WikiSpace space = aiReviewSpace(10L);
        stubCommentableSpace(space);
        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);

        // 判定通过：直接公开，并留下可追溯的机器判定（模型、理由、时间）
        when(moderation.review(any(), any(), any(), any(), any(), any()))
                .thenReturn(WikiCommentModerationVerdict.approve("与页面主题相关"));
        service.create(createCmd("demo", 20L, null, "写得清楚"));
        WikiComment approved = lastSavedComment(captor);
        assertThat(approved.getStatus()).isEqualTo(WikiCommentStatus.PUBLISHED);
        assertThat(approved.visibleToPublic()).isTrue();
        assertThat(approved.getReviewMode()).isEqualTo(WikiCommentReviewMode.AI);
        assertThat(approved.getAiVerdict()).isEqualTo(WikiCommentAiVerdict.APPROVED);
        assertThat(approved.getAiNote()).isEqualTo("与页面主题相关");
        assertThat(approved.getAiModel()).isEqualTo("openai/gpt-4o-mini");
        assertThat(approved.getAiReviewedAt()).isNotNull();
        // 机器判定不等于人工审核：审核人字段保持为空
        assertThat(approved.getReviewedByUserId()).isNull();

        // 判定不通过：留在待审核，绝不自动隐藏或丢弃
        when(moderation.review(any(), any(), any(), any(), any(), any()))
                .thenReturn(WikiCommentModerationVerdict.reject("广告引流"));
        service.create(createCmd("demo", 20L, null, "加微信领资料"));
        WikiComment rejected = lastSavedComment(captor);
        assertThat(rejected.getStatus()).isEqualTo(WikiCommentStatus.PENDING);
        assertThat(rejected.getAiVerdict()).isEqualTo(WikiCommentAiVerdict.REJECTED);
        assertThat(rejected.getAiNote()).isEqualTo("广告引流");

        // 审核不可用（模型/能力问题）：内容照常落库，只把原因记进审计并转人工
        when(moderation.review(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BizException("AI 能力未启用"));
        service.create(createCmd("demo", 20L, null, "正常发言"));
        WikiComment skipped = lastSavedComment(captor);
        assertThat(skipped.getStatus()).isEqualTo(WikiCommentStatus.PENDING);
        assertThat(skipped.getAiVerdict()).isEqualTo(WikiCommentAiVerdict.UNAVAILABLE);
        assertThat(skipped.getAiModel()).isNull();
        assertThat(skipped.getAiNote()).contains("已转人工审核");
    }

    @Test
    void manualReviewModeNeverCallsTheModeratorEvenWhenTheModelIsConfigured() {
        // 人工审核是默认：即使配了模型也不能偷偷自动放行
        WikiSpace space = commentableSpace(10L);
        space.setChatProviderCode("openai");
        space.setChatModelCode("gpt-4o-mini");
        stubCommentableSpace(space);
        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);

        service.create(createCmd("demo", 20L, null, "内容"));

        WikiComment saved = lastSavedComment(captor);
        assertThat(saved.getStatus()).isEqualTo(WikiCommentStatus.PENDING);
        assertThat(saved.getAiVerdict()).isNull();
        assertThat(saved.getReviewMode()).isEqualTo(WikiCommentReviewMode.MANUAL);
        verify(moderation, never()).review(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aiReviewWithoutChatModelFallsBackToHumanReview() {
        WikiSpace space = aiReviewSpace(10L);
        space.setChatProviderCode("");
        space.setChatModelCode("");
        stubCommentableSpace(space);
        ArgumentCaptor<WikiComment> captor = ArgumentCaptor.forClass(WikiComment.class);

        service.create(createCmd("demo", 20L, null, "内容"));

        WikiComment saved = lastSavedComment(captor);
        assertThat(saved.getStatus()).isEqualTo(WikiCommentStatus.PENDING);
        assertThat(saved.getAiVerdict()).isEqualTo(WikiCommentAiVerdict.UNAVAILABLE);
        assertThat(saved.getAiNote()).contains("未配置问答模型");
        verify(moderation, never()).review(any(), any(), any(), any(), any(), any());
    }

    /** 取最后一次落库的评论实体（AI 审核先落库、判定后再更新一次），并清空抓取记录供下一步使用。 */
    private WikiComment lastSavedComment(ArgumentCaptor<WikiComment> captor) {
        verify(comments, atLeastOnce()).save(captor.capture());
        List<WikiComment> values = captor.getAllValues();
        WikiComment last = values.get(values.size() - 1);
        clearInvocations(comments);
        captor.getAllValues().clear();
        return last;
    }

    /** 可评论 + AI 审核 + 已配置问答模型的空间。 */
    private static WikiSpace aiReviewSpace(Long id) {
        WikiSpace space = commentableSpace(id);
        space.applyCommentReviewMode(WikiCommentReviewMode.AI);
        space.setChatProviderCode("openai");
        space.setChatModelCode("gpt-4o-mini");
        return space;
    }

    /** 已发布页面 + 认证作者 + 落库回显，供 AI 审核用例复用。 */
    private void stubCommentableSpace(WikiSpace space) {
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findById(20L)).thenReturn(Optional.of(publishedNode(space.getId(), 20L)));
        when(versions.findById(200L)).thenReturn(Optional.of(version(space.getId(), 20L, PUBLISHED_MARKDOWN, "hash-1")));
        when(actors.currentActor()).thenReturn(AUTHOR);
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static WikiCommentCreateCmd createCmd(String slug, Long nodeId, Long parentId, String content) {
        WikiCommentCreateCmd cmd = new WikiCommentCreateCmd();
        cmd.setSpaceSlug(slug);
        cmd.setNodeId(nodeId);
        cmd.setParentId(parentId);
        cmd.setContent(content);
        return cmd;
    }

    private static WikiCommentAnchorInput anchorInput(String exact, String prefix, String suffix) {
        WikiCommentAnchorInput input = new WikiCommentAnchorInput();
        input.setExact(exact);
        input.setPrefix(prefix);
        input.setSuffix(suffix);
        return input;
    }

    /** 测试用锚点：模拟「已在旧版本上盖章」的存量锚点。 */
    private static WikiTextAnchor stampedAnchor(String exact, String contentHash) {
        return WikiTextAnchor.stamp(WikiTextAnchor.draft(exact, null, null, null, null, null),
                200L, 1L, contentHash, exact + " 的上下文");
    }

    private static WikiComment publishedRoot(Long id, String content, WikiTextAnchor anchor) {
        WikiComment comment = WikiComment.create(10L, 20L, null, 100L, "小明", content, anchor);
        comment.setId(id);
        comment.publish(7L);
        return comment;
    }

    private static WikiSpace publicSpace(Long id) {
        WikiSpace space = WikiSpace.create("库", "demo");
        space.setId(id);
        space.setPublicReadEnabled(true);
        return space;
    }

    private static WikiSpace commentableSpace(Long id) {
        WikiSpace space = publicSpace(id);
        space.applyCommentEnabled(true);
        return space;
    }

    private static WikiNode publishedNode(Long spaceId, Long nodeId) {
        WikiNode node = WikiNode.page(spaceId, null, "页面", "page", 0);
        node.setId(nodeId);
        node.setNodeType(WikiNodeType.PAGE);
        node.setPublishedVersionId(200L);
        return node;
    }

    private static WikiPageVersion version(Long spaceId, Long nodeId) {
        return version(spaceId, nodeId, null, "hash-1");
    }

    private static WikiPageVersion version(Long spaceId, Long nodeId, String markdown, String contentHash) {
        WikiPageVersion version = WikiPageVersion.builder().spaceId(spaceId).nodeId(nodeId).revision(1L)
                .markdown(markdown).contentHash(contentHash).build();
        version.setId(200L);
        return version;
    }
}
