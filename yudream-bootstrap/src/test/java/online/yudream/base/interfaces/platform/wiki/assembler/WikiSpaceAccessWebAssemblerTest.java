package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiCommentDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.interfaces.platform.wiki.request.WikiCommentCreateRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceCoverRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberSaveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceMemberUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口层映射：长 ID 只走字符串边界、非法枚举与非法 ID fail closed、审核字段不外泄到公开视图。
 */
class WikiSpaceAccessWebAssemblerTest {

    @Test
    void coverRequestKeepsLongIdAsStringAndParsesType() {
        WikiSpaceCoverRequest request = new WikiSpaceCoverRequest();
        request.setCoverType("upload");
        request.setFileObjectId("123456789012345678");
        request.setAlt("封面");

        var cmd = WikiSpaceAccessWebAssembler.cover(10L, request);

        assertThat(cmd.getCoverType()).isEqualTo(WikiSpaceCoverType.UPLOAD);
        assertThat(cmd.getCoverFileObjectId()).isEqualTo(123456789012345678L);
    }

    @Test
    void coverRequestRejectsUnknownTypeAndNonNumericId() {
        WikiSpaceCoverRequest unknown = new WikiSpaceCoverRequest();
        unknown.setCoverType("rotating");
        assertThatThrownBy(() -> WikiSpaceAccessWebAssembler.cover(10L, unknown))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("封面类型不合法");

        WikiSpaceCoverRequest badId = new WikiSpaceCoverRequest();
        badId.setCoverType("UPLOAD");
        badId.setFileObjectId("12.3");
        assertThatThrownBy(() -> WikiSpaceAccessWebAssembler.cover(10L, badId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("ID 不合法");
    }

    @Test
    void memberRequestParsesSubjectAndActionsAndRejectsUnknownAction() {
        WikiSpaceMemberSaveRequest request = new WikiSpaceMemberSaveRequest();
        request.setSubjectType("role");
        request.setSubjectId("7");
        request.setActions(List.of("read", "MANAGE", "read"));

        WikiSpaceMemberSaveCmd cmd = WikiSpaceAccessWebAssembler.member(10L, request);

        assertThat(cmd.getSubjectType()).isEqualTo(WikiSpaceMemberSubjectType.ROLE);
        assertThat(cmd.getSubjectId()).isEqualTo(7L);
        assertThat(cmd.getActions()).containsExactly(WikiSpaceAclAction.READ, WikiSpaceAclAction.MANAGE);

        WikiSpaceMemberSaveRequest unknown = new WikiSpaceMemberSaveRequest();
        unknown.setSubjectType("USER");
        unknown.setSubjectId("7");
        unknown.setActions(List.of("approve"));
        assertThatThrownBy(() -> WikiSpaceAccessWebAssembler.member(10L, unknown))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员动作不合法");
    }

    @Test
    void memberUpdateCarriesExpectedVersionForCas() {
        WikiSpaceMemberUpdateRequest request = new WikiSpaceMemberUpdateRequest();
        request.setActions(List.of("edit"));
        request.setExpectedVersion(3);

        WikiSpaceMemberUpdateCmd cmd = WikiSpaceAccessWebAssembler.member(10L, 11L, request);

        assertThat(cmd.getMemberId()).isEqualTo(11L);
        assertThat(cmd.getExpectedVersion()).isEqualTo(3);
        assertThat(cmd.getActions()).containsExactly(WikiSpaceAclAction.EDIT);
    }

    @Test
    void commentCreateIgnoresAuthorAndKeepsParentIdAsString() {
        WikiCommentCreateRequest request = new WikiCommentCreateRequest();
        request.setParentId("123456789012345678");
        request.setContent("内容");

        var cmd = WikiCommentWebAssembler.create("demo", 20L, request);

        assertThat(cmd.getSpaceSlug()).isEqualTo("demo");
        assertThat(cmd.getNodeId()).isEqualTo(20L);
        assertThat(cmd.getParentId()).isEqualTo(123456789012345678L);
        assertThat(cmd.getContent()).isEqualTo("内容");
    }

    @Test
    void publicCommentViewDropsReviewAuditFields() {
        online.yudream.base.domain.platform.wiki.aggregate.WikiComment comment =
                online.yudream.base.domain.platform.wiki.aggregate.WikiComment.create(10L, 20L, null, 100L, "小明", "内容");
        comment.setId(1L);
        comment.publish(200L);

        // 公开链路：应用 assembler 落公开视图，接口层只透传，审核人信息不会出现在响应里
        WikiCommentDTO publicDto = online.yudream.base.application.platform.wiki.assembler.WikiCommentAssembler
                .publicView(comment, List.of());

        assertThat(publicDto.getReviewedByUserId()).isNull();
        assertThat(publicDto.getReviewedAt()).isNull();
        assertThat(WikiCommentWebAssembler.toRes(publicDto).getReviewedByUserId()).isNull();
        assertThat(WikiCommentWebAssembler.toRes(publicDto).getContent()).isEqualTo("内容");
    }
}
