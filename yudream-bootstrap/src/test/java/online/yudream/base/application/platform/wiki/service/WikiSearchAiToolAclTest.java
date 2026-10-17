package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * wiki.search 工具的实例 ACL：运行线程没有请求级安全上下文，
 * 主体取执行作用域里的发起人；受限空间非成员/无主体一律拒绝，LEGACY 空间行为不变。
 */
class WikiSearchAiToolAclTest {

    private static final WikiActor MEMBER = WikiActor.of(100L, 7L, "成员", false);
    private static final WikiActor OUTSIDER = WikiActor.of(200L, 8L, "外部用户", false);

    private final WikiSearchAppService search = mock(WikiSearchAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiActorGateway actors = mock(WikiActorGateway.class);
    private final WikiSpaceAclAppService access = new WikiSpaceAclAppService(
            mock(CapabilityAppService.class), spaces, mock(WikiSpaceMemberRepo.class), mock(WikiCommentRepo.class),
            mock(WikiNodeRepo.class), mock(WikiIngestTaskRepo.class), actors, mock(WikiAppService.class),
            mock(WikiPublicationAppService.class));
    private final WikiSearchAiTool tool = new WikiSearchAiTool(search, spaces, access);

    private WikiSpace space(long id, String slug, boolean restricted) {
        WikiSpace space = WikiSpace.create("库 " + slug, slug);
        space.setId(id);
        if (restricted) {
            space.enableRestrictedAcl();
        }
        return space;
    }

    private static WikiSpaceMember member(long id, long subjectId) {
        WikiSpaceMember member = WikiSpaceMember.create(id, WikiSpaceMemberSubjectType.USER, subjectId,
                List.of(WikiSpaceAclAction.READ), 1L);
        member.setId(id);
        return member;
    }

    @Test
    void restrictedSpaceDeniesRunWithoutActingUser() {
        WikiSpace space = space(10L, "secret", true);
        when(spaces.findBySlug("secret")).thenReturn(Optional.of(space));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(actors.actorFor(any())).thenReturn(null);

        assertThatThrownBy(() -> tool.execute(new AiAgentToolCall("wiki.search", Map.of("spaceSlug", "secret", "query", "x"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("身份");
        verify(search, never()).searchForAdmin(anyString(), anyString(), anyInt(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void restrictedSpaceDeniesNonMemberActorEvenWithGlobalPermission() {
        WikiSpace space = space(10L, "secret", true);
        when(spaces.findBySlug("secret")).thenReturn(Optional.of(space));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(actors.actorFor(200L)).thenReturn(OUTSIDER);

        try (AiAgentToolExecutionScope scope = AiAgentToolExecutionScope.open(List.of(tool), 200L)) {
            assertThatThrownBy(() -> tool.execute(new AiAgentToolCall("wiki.search", Map.of("spaceSlug", "secret", "query", "x"))))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("没有该知识库的访问权限");
        }
    }

    @Test
    void restrictedSpaceAllowsMemberActorFromExecutionContext() {
        WikiSpace space = space(10L, "secret", true);
        WikiSpaceMemberRepo members = mock(WikiSpaceMemberRepo.class);
        when(members.findBySpaceId(10L)).thenReturn(List.of(member(1L, 100L)));
        WikiSpaceAclAppService memberAccess = new WikiSpaceAclAppService(
                mock(CapabilityAppService.class), spaces, members, mock(WikiCommentRepo.class),
                mock(WikiNodeRepo.class), mock(WikiIngestTaskRepo.class), actors, mock(WikiAppService.class),
                mock(WikiPublicationAppService.class));
        WikiSearchAiTool memberTool = new WikiSearchAiTool(search, spaces, memberAccess);
        when(spaces.findBySlug("secret")).thenReturn(Optional.of(space));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(actors.actorFor(100L)).thenReturn(MEMBER);
        when(search.searchForAdmin(anyString(), anyString(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(WikiSearchHitDTO.builder().score(1.0).kind("PAGE").nodeId("5").title("页").build()));

        try (AiAgentToolExecutionScope scope = AiAgentToolExecutionScope.open(List.of(memberTool), 100L)) {
            var result = memberTool.execute(new AiAgentToolCall("wiki.search", Map.of("spaceSlug", "secret", "query", "x")));

            assertThat(result.payload()).containsEntry("count", 1);
        }
    }

    @Test
    void legacySpaceKeepsWorkingWithoutActingUser() {
        WikiSpace space = space(11L, "public", false);
        when(spaces.findBySlug("public")).thenReturn(Optional.of(space));
        when(spaces.findById(11L)).thenReturn(Optional.of(space));
        when(actors.actorFor(any())).thenReturn(null);
        when(search.searchForAdmin(eq("public"), eq("x"), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of());

        var result = tool.execute(new AiAgentToolCall("wiki.search", Map.of("spaceSlug", "public", "query", "x")));

        assertThat(result.payload()).containsEntry("count", 0);
    }
}
