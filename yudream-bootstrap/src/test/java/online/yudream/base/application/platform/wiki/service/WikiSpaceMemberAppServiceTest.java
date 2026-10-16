package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceMemberUpdateCmd;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceMemberDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceMemberRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成员管理用例：CAS、主体存在性校验、受限空间「必须保留可管理成员」不变量。
 */
class WikiSpaceMemberAppServiceTest {

    private final WikiSpaceMemberRepo members = mock(WikiSpaceMemberRepo.class);
    private final UserRepo users = mock(UserRepo.class);
    private final RoleRepo roles = mock(RoleRepo.class);
    private final WikiSpaceAclAppService access = mock(WikiSpaceAclAppService.class);
    private final WikiSpaceMemberAppService service = new WikiSpaceMemberAppService(members, users, roles, access);

    @Test
    void updateRequiresExpectedVersionAndRejectsStaleCas() {
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(legacySpace(10L)));
        WikiSpaceMember member = member(1L, 3, WikiSpaceMemberSubjectType.USER, 100L, WikiSpaceAclAction.MANAGE);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        WikiSpaceMemberUpdateCmd withoutVersion = updateCmd(10L, 1L, List.of(WikiSpaceAclAction.MANAGE), null);
        assertThatThrownBy(() -> service.update(withoutVersion))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("期望版本");

        WikiSpaceMemberUpdateCmd stale = updateCmd(10L, 1L, List.of(WikiSpaceAclAction.EDIT), 2);
        assertThatThrownBy(() -> service.update(stale))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员授权已被调整");
    }

    @Test
    void updateAppliesCasAndReturnsNewVersion() {
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(legacySpace(10L)));
        WikiSpaceMember member = member(1L, 3, WikiSpaceMemberSubjectType.USER, 100L, WikiSpaceAclAction.READ);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(users.findById(100L)).thenReturn(Optional.of(user(100L, "小明")));
        when(members.save(any())).thenAnswer(invocation -> {
            WikiSpaceMember saved = invocation.getArgument(0);
            saved.setVersion(4);
            return saved;
        });

        WikiSpaceMemberDTO dto = service.update(updateCmd(10L, 1L, List.of(WikiSpaceAclAction.EDIT, WikiSpaceAclAction.READ), 3));

        assertThat(dto.getActions()).containsExactly("EDIT", "READ");
        assertThat(dto.getVersion()).isEqualTo(4);
        assertThat(dto.getSubjectName()).isEqualTo("小明");
    }

    @Test
    void cannotDropLastManagerFromRestrictedSpace() {
        WikiSpace space = legacySpace(10L);
        space.enableRestrictedAcl();
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(space));
        WikiSpaceMember onlyManager = member(1L, 1, WikiSpaceMemberSubjectType.USER, 100L, WikiSpaceAclAction.MANAGE);
        when(members.findById(1L)).thenReturn(Optional.of(onlyManager));
        when(members.findBySpaceId(10L)).thenReturn(List.of(onlyManager));

        assertThatThrownBy(() -> service.delete(10L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须保留至少一名可管理成员");
        assertThatThrownBy(() -> service.update(updateCmd(10L, 1L, List.of(WikiSpaceAclAction.READ), 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须保留至少一名可管理成员");
        verify(members, never()).deleteById(any());
    }

    @Test
    void restrictedSpaceAllowsDowngradeWhenAnotherManagerRemains() {
        WikiSpace space = legacySpace(10L);
        space.enableRestrictedAcl();
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(space));
        WikiSpaceMember downgraded = member(1L, 2, WikiSpaceMemberSubjectType.USER, 100L, WikiSpaceAclAction.MANAGE);
        WikiSpaceMember otherManager = member(2L, 1, WikiSpaceMemberSubjectType.ROLE, 7L, WikiSpaceAclAction.MANAGE);
        when(members.findById(1L)).thenReturn(Optional.of(downgraded));
        when(members.findBySpaceId(10L)).thenReturn(List.of(downgraded, otherManager));
        when(users.findById(100L)).thenReturn(Optional.of(user(100L, "小明")));
        when(members.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceMemberDTO dto = service.update(updateCmd(10L, 1L, List.of(WikiSpaceAclAction.READ), 2));

        assertThat(dto.getActions()).containsExactly("READ");
    }

    @Test
    void createRejectsUnknownSubjectAndDuplicateGrant() {
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(legacySpace(10L)));
        when(users.findById(100L)).thenReturn(Optional.empty());

        WikiSpaceMemberSaveCmd unknown = saveCmd(10L, WikiSpaceMemberSubjectType.USER, 100L,
                List.of(WikiSpaceAclAction.READ));
        assertThatThrownBy(() -> service.create(unknown))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员主体不存在");

        when(users.findById(100L)).thenReturn(Optional.of(user(100L, "小明")));
        when(members.findBySubject(10L, WikiSpaceMemberSubjectType.USER, 100L))
                .thenReturn(Optional.of(member(9L, 1, WikiSpaceMemberSubjectType.USER, 100L, WikiSpaceAclAction.READ)));
        assertThatThrownBy(() -> service.create(unknown))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("该主体已授权");
    }

    @Test
    void createStoresMemberWithSessionActorAsCreator() {
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(legacySpace(10L)));
        when(users.findById(100L)).thenReturn(Optional.of(user(100L, "小明")));
        when(members.findBySubject(10L, WikiSpaceMemberSubjectType.USER, 100L)).thenReturn(Optional.empty());
        when(members.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceMemberDTO dto = service.create(saveCmd(10L, WikiSpaceMemberSubjectType.USER, 100L,
                List.of(WikiSpaceAclAction.EDIT)));

        assertThat(dto.getActions()).containsExactly("EDIT");
        assertThat(dto.getSubjectId()).isEqualTo("100");
        assertThat(dto.getCreatedByUserId()).isEqualTo("100");
    }

    @Test
    void memberOfAnotherSpaceIsRejected() {
        when(access.requireSpace(any(), any(), any())).thenReturn(grant(legacySpace(10L)));
        WikiSpaceMember foreign = WikiSpaceMember.create(99L, WikiSpaceMemberSubjectType.USER, 100L,
                List.of(WikiSpaceAclAction.READ), 1L);
        foreign.setId(1L);
        when(members.findById(1L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.delete(10L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("成员不属于当前知识库");
    }

    private static WikiAccessGrant grant(WikiSpace space) {
        return new WikiAccessGrant(space, WikiActor.of(100L, 7L, "操作者", false));
    }

    private static WikiSpace legacySpace(Long id) {
        WikiSpace space = WikiSpace.create("库", "space-" + id);
        space.setId(id);
        return space;
    }

    private static WikiSpaceMember member(Long id, int version, WikiSpaceMemberSubjectType type, Long subjectId,
                                          WikiSpaceAclAction action) {
        WikiSpaceMember member = WikiSpaceMember.create(10L, type, subjectId, List.of(action), 1L);
        member.setId(id);
        member.setVersion(version);
        return member;
    }

    private static User user(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname(nickname);
        return user;
    }

    private static WikiSpaceMemberUpdateCmd updateCmd(Long spaceId, Long memberId, List<WikiSpaceAclAction> actions,
                                                      Integer expectedVersion) {
        WikiSpaceMemberUpdateCmd cmd = new WikiSpaceMemberUpdateCmd();
        cmd.setSpaceId(spaceId);
        cmd.setMemberId(memberId);
        cmd.setActions(actions);
        cmd.setExpectedVersion(expectedVersion);
        return cmd;
    }

    private static WikiSpaceMemberSaveCmd saveCmd(Long spaceId, WikiSpaceMemberSubjectType type, Long subjectId,
                                                  List<WikiSpaceAclAction> actions) {
        WikiSpaceMemberSaveCmd cmd = new WikiSpaceMemberSaveCmd();
        cmd.setSpaceId(spaceId);
        cmd.setSubjectType(type);
        cmd.setSubjectId(subjectId);
        cmd.setActions(actions);
        return cmd;
    }
}
