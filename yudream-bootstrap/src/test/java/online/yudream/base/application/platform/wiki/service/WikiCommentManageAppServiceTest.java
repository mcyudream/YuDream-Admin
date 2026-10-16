package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.application.platform.wiki.query.WikiCommentPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiComment;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 治理端评论：空间归属由评论推导、审核动作审计、受限空间实例 ACL 生效、列表带状态过滤。
 */
class WikiCommentManageAppServiceTest {

    private static final WikiActor REVIEWER = WikiActor.of(200L, 8L, "审核员", false);

    private final WikiCommentRepo comments = mock(WikiCommentRepo.class);
    private final WikiSpaceAclAppService access = mock(WikiSpaceAclAppService.class);
    private final WikiCommentManageAppService service = new WikiCommentManageAppService(comments, access);

    @Test
    void moderationUsesCommentSpaceForInstanceAcl() {
        WikiComment comment = pendingComment(10L, 5L);
        when(access.requireGlobalPermission("platform:wiki:comment:manage")).thenReturn(REVIEWER);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));
        when(access.requireSpaceAcl(10L, WikiSpaceAclAction.MANAGE))
                .thenThrow(new BizException("当前用户没有该知识库的访问权限"));

        assertThatThrownBy(() -> service.publish(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有该知识库的访问权限");
    }

    @Test
    void publishStoresAuditFieldsAndMarksRetrievalCandidate() {
        WikiComment comment = pendingComment(10L, 5L);
        when(access.requireGlobalPermission("platform:wiki:comment:manage")).thenReturn(REVIEWER);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));
        when(access.requireSpaceAcl(10L, WikiSpaceAclAction.MANAGE)).thenReturn(restrictedSpace(10L));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiCommentDTO dto = service.publish(5L);

        assertThat(dto.getStatus()).isEqualTo(WikiCommentStatus.PUBLISHED.name());
        assertThat(dto.getReviewedByUserId()).isEqualTo("200");
        assertThat(dto.getReviewedAt()).isNotNull();
        assertThat(comment.getRagStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void hideAndDeleteAreRecoverableSoftStates() {
        WikiComment comment = pendingComment(10L, 5L);
        when(access.requireGlobalPermission(any())).thenReturn(REVIEWER);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));
        when(access.requireSpaceAcl(anyLong(), any())).thenReturn(restrictedSpace(10L));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(5L);
        assertThat(service.hide(5L).getStatus()).isEqualTo(WikiCommentStatus.HIDDEN.name());
        assertThat(comment.getRagStatus().name()).isEqualTo("DISABLED");

        assertThat(service.restore(5L).getStatus()).isEqualTo(WikiCommentStatus.PUBLISHED.name());
        assertThat(service.delete(5L).getStatus()).isEqualTo(WikiCommentStatus.DELETED.name());
        assertThat(service.restore(5L).getStatus()).isEqualTo(WikiCommentStatus.PUBLISHED.name());

        verify(access).requireGlobalPermission("platform:wiki:comment:delete");
    }

    @Test
    void pinRequiresPublishedComment() {
        WikiComment comment = pendingComment(10L, 5L);
        when(access.requireGlobalPermission(any())).thenReturn(REVIEWER);
        when(comments.findById(5L)).thenReturn(Optional.of(comment));
        when(access.requireSpaceAcl(anyLong(), any())).thenReturn(restrictedSpace(10L));

        assertThatThrownBy(() -> service.pin(5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只有已发布评论可以置顶");
    }

    @Test
    void pageRequiresInstanceAclAndForwardsStatusFilter() {
        WikiSpace space = restrictedSpace(10L);
        when(access.requireSpace(eq(10L), eq(WikiSpaceAclAction.READ), eq("platform:wiki:comment:view")))
                .thenReturn(new WikiAccessGrant(space, REVIEWER));
        when(comments.pageBySpace(eq(10L), eq(WikiCommentStatus.PENDING), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(pendingComment(10L, 5L)), 1, 1, 10));

        WikiCommentPageQuery query = new WikiCommentPageQuery();
        query.setSpaceId(10L);
        query.setStatus(WikiCommentStatus.PENDING);

        PageResult<WikiCommentDTO> page = service.page(query);

        assertThat(page.getRecords()).singleElement()
                .satisfies(dto -> assertThat(dto.getStatus()).isEqualTo(WikiCommentStatus.PENDING.name()));
    }

    private static WikiComment pendingComment(Long spaceId, Long commentId) {
        WikiComment comment = WikiComment.create(spaceId, 20L, null, 100L, "小明", "内容");
        comment.setId(commentId);
        return comment;
    }

    private static WikiSpace restrictedSpace(Long id) {
        WikiSpace space = WikiSpace.create("库", "space-" + id);
        space.setId(id);
        space.enableRestrictedAcl();
        return space;
    }
}
