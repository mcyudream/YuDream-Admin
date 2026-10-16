package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceSaveCmd;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.repo.WikiCommentRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiSpaceStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ACL 应用闸门：LEGACY 兼容、RESTRICTED fail closed、新建空间授权创建者、超管既有语义。
 */
class WikiSpaceAclAppServiceTest {

    private static final WikiActor CREATOR = WikiActor.of(100L, 7L, "创建者", false);
    private static final WikiActor OUTSIDER = WikiActor.of(200L, 8L, "外部用户", false);

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiSpaceMemberRepo members = mock(WikiSpaceMemberRepo.class);
    private final WikiCommentRepo comments = mock(WikiCommentRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiIngestTaskRepo ingestTasks = mock(WikiIngestTaskRepo.class);
    private final WikiActorGateway actors = mock(WikiActorGateway.class);
    private final WikiAppService wikiAppService = mock(WikiAppService.class);
    private final WikiPublicationAppService publications = mock(WikiPublicationAppService.class);
    private final WikiSpaceAclAppService service = new WikiSpaceAclAppService(capabilities, spaces, members,
            comments, nodes, ingestTasks, actors, wikiAppService, publications);

    /** 列表/详情固定补算聚合事实；ACL 测试只关心闸门语义，聚合默认给空统计。 */
    @BeforeEach
    void stubAggregates() {
        lenient().when(nodes.statsBySpaceId(any())).thenReturn(WikiSpaceStats.EMPTY);
        lenient().when(ingestTasks.countBySpaceIdAndStatus(any(), any())).thenReturn(0L);
    }

    @Test
    void legacySpaceKeepsExistingGlobalPermissionBehaviourWithoutMembers() {
        WikiSpace space = WikiSpace.create("旧库", "legacy");
        space.setId(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(actors.currentActor()).thenReturn(OUTSIDER);
        when(actors.hasPermission(anyString())).thenReturn(true);

        assertThat(service.requireSpace(10L, WikiSpaceAclAction.EDIT, "platform:wiki:edit").space())
                .isSameAs(space);
    }

    @Test
    void restrictedSpaceDeniesActorWithoutMemberGrant() {
        WikiSpace space = restricted(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(members.findBySpaceId(10L)).thenReturn(List.of(member(1L, WikiSpaceMemberSubjectType.USER, 100L,
                WikiSpaceAclAction.READ)));
        when(actors.currentActor()).thenReturn(OUTSIDER);
        when(actors.hasPermission(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.requireSpace(10L, WikiSpaceAclAction.READ, "platform:wiki:view"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有该知识库的访问权限");
    }

    @Test
    void restrictedSpaceAllowsMemberByUserSubjectAndByCurrentRoleSubject() {
        WikiSpace space = restricted(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(actors.hasPermission(anyString())).thenReturn(true);
        when(actors.currentActor()).thenReturn(CREATOR);

        when(members.findBySpaceId(10L)).thenReturn(List.of(member(1L, WikiSpaceMemberSubjectType.USER, 100L,
                WikiSpaceAclAction.EDIT)));
        assertThat(service.requireSpace(10L, WikiSpaceAclAction.EDIT, "platform:wiki:edit").space()).isSameAs(space);

        when(members.findBySpaceId(10L)).thenReturn(List.of(member(2L, WikiSpaceMemberSubjectType.ROLE, 7L,
                WikiSpaceAclAction.MANAGE)));
        assertThat(service.requireSpace(10L, WikiSpaceAclAction.MANAGE, "platform:wiki:manage").space())
                .isSameAs(space);
    }

    @Test
    void superAdminKeepsWildcardSemanticsOnRestrictedSpace() {
        WikiSpace space = restricted(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(members.findBySpaceId(10L)).thenReturn(List.of());
        when(actors.currentActor()).thenReturn(WikiActor.of(999L, 1L, "超管", true));
        when(actors.hasPermission(anyString())).thenReturn(true);

        assertThat(service.requireSpace(10L, WikiSpaceAclAction.MANAGE, "platform:wiki:manage").space())
                .isSameAs(space);
    }

    @Test
    void missingGlobalPermissionIsRejectedBeforeSpaceLookup() {
        when(actors.currentActor()).thenReturn(CREATOR);
        when(actors.hasPermission("platform:wiki:member:manage")).thenReturn(false);

        assertThatThrownBy(() -> service.requireSpace(10L, WikiSpaceAclAction.MANAGE,
                "platform:wiki:member:manage"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    @Test
    void unauthenticatedActorFailsClosed() {
        when(actors.currentActor()).thenReturn(null);

        assertThatThrownBy(service::requireActor)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法确认当前登录用户");
    }

    @Test
    void newSpaceGetsCreatorManageAndOnlyBecomesRestrictedWhenExposureIsReady() {
        WikiSpaceSaveCmd command = new WikiSpaceSaveCmd();
        command.setName("新库");
        command.setSlug("new-lib");
        when(actors.currentActor()).thenReturn(CREATOR);
        when(actors.hasPermission("platform:wiki:manage")).thenReturn(true);
        when(wikiAppService.saveSpace(any())).thenReturn(WikiSpaceDTO.builder().id("10").name("新库").slug("new-lib").build());
        WikiSpace created = WikiSpace.create("新库", "new-lib");
        created.setId(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(created));
        when(members.findBySubject(10L, WikiSpaceMemberSubjectType.USER, 100L)).thenReturn(Optional.empty());
        when(members.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spaces.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceDTO saved = service.saveSpace(command);

        assertThat(saved.getAclMode()).isEqualTo(WikiSpaceAclMode.LEGACY.name());
        ArgumentCaptor<WikiSpaceMember> captor = ArgumentCaptor.forClass(WikiSpaceMember.class);
        verify(members).save(captor.capture());
        assertThat(captor.getValue().getSubjectType()).isEqualTo(WikiSpaceMemberSubjectType.USER);
        assertThat(captor.getValue().getSubjectId()).isEqualTo(100L);
        assertThat(captor.getValue().getActions()).containsExactly(WikiSpaceAclAction.MANAGE);
    }

    @Test
    void restrictedSpaceIsHiddenFromListForNonMember() {
        WikiSpace legacy = WikiSpace.create("旧库", "legacy");
        legacy.setId(10L);
        WikiSpace restricted = restricted(11L);
        when(spaces.findAll()).thenReturn(List.of(legacy, restricted));
        when(members.findBySpaceId(11L)).thenReturn(List.of(member(1L, WikiSpaceMemberSubjectType.USER, 100L,
                WikiSpaceAclAction.READ)));
        when(actors.currentActor()).thenReturn(OUTSIDER);
        when(actors.hasPermission("platform:wiki:view")).thenReturn(true);

        assertThat(service.spaces()).extracting(WikiSpaceDTO::getId).containsExactly("10");
    }

    @Test
    void enablingRestrictedAclFailsClosedUntilCoverageIsReady() {
        WikiSpace space = WikiSpace.create("旧库", "legacy");
        space.setId(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(members.findBySpaceId(10L)).thenReturn(List.of(member(1L, WikiSpaceMemberSubjectType.USER, 100L,
                WikiSpaceAclAction.READ)));
        when(members.findBySubject(10L, WikiSpaceMemberSubjectType.USER, 100L)).thenReturn(Optional.empty());
        when(members.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(spaces.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(actors.currentActor()).thenReturn(CREATOR);
        when(actors.hasPermission("platform:wiki:member:manage")).thenReturn(true);

        assertThatThrownBy(() -> service.enableRestrictedAcl(10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("受限访问尚未开放");
        assertThat(space.effectiveAclMode()).isEqualTo(WikiSpaceAclMode.LEGACY);
        org.mockito.Mockito.verifyNoInteractions(spaces, members);
    }

    private static WikiSpace restricted(Long id) {
        WikiSpace space = WikiSpace.create("受限库", "restricted-" + id);
        space.setId(id);
        space.enableRestrictedAcl();
        return space;
    }

    private static WikiSpaceMember member(Long id, WikiSpaceMemberSubjectType type, Long subjectId,
                                          WikiSpaceAclAction action) {
        WikiSpaceMember member = WikiSpaceMember.create(10L, type, subjectId, List.of(action), 1L);
        member.setId(id);
        return member;
    }
}
