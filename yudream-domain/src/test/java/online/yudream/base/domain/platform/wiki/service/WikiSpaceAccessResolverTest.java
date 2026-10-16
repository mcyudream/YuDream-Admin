package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空间访问解析：LEGACY 保持既有行为、RESTRICTED fail closed、动作包含与超管语义。
 */
class WikiSpaceAccessResolverTest {

    private static final WikiActor MEMBER = WikiActor.of(100L, 7L, "成员", false);
    private static final WikiActor OTHER = WikiActor.of(200L, 8L, "其他人", false);
    private static final WikiActor OUTSIDER = WikiActor.of(300L, 9L, "外部人", false);

    @Test
    void legacySpaceKeepsGlobalPermissionBehaviourEvenWithoutMembers() {
        assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.LEGACY, List.of(), OUTSIDER, WikiSpaceAclAction.MANAGE));
        assertTrue(WikiSpaceAccessResolver.permits(null, List.of(), null, WikiSpaceAclAction.READ));
    }

    @Test
    void restrictedSpaceDeniesActorWithoutMemberGrant() {
        List<WikiSpaceMember> members = List.of(user(1L, 100L, WikiSpaceAclAction.READ));

        assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, WikiSpaceAclAction.READ));
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, WikiSpaceAclAction.COMMENT));
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, WikiSpaceAclAction.MANAGE));
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, OUTSIDER, WikiSpaceAclAction.READ));
    }

    @Test
    void restrictedSpaceFailsClosedForUnauthenticatedActor() {
        List<WikiSpaceMember> members = List.of(user(1L, 100L, WikiSpaceAclAction.MANAGE));

        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, null, WikiSpaceAclAction.READ));
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members,
                WikiActor.of(null, null, "", false), WikiSpaceAclAction.READ));
    }

    @Test
    void roleSubjectMatchesCurrentEffectiveRoleOnly() {
        List<WikiSpaceMember> members = List.of(role(1L, 7L, WikiSpaceAclAction.EDIT));

        assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, WikiSpaceAclAction.EDIT));
        assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, WikiSpaceAclAction.READ));
        // 同用户但当前生效角色不同：角色主体不命中。
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, OTHER, WikiSpaceAclAction.READ));
    }

    @Test
    void manageImpliesLowerActions() {
        List<WikiSpaceMember> members = List.of(user(1L, 100L, WikiSpaceAclAction.MANAGE));

        for (WikiSpaceAclAction action : WikiSpaceAclAction.values()) {
            assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, members, MEMBER, action));
        }
        assertFalse(WikiSpaceAclAction.READ.implies(WikiSpaceAclAction.EDIT));
        assertFalse(WikiSpaceAclAction.COMMENT.implies(WikiSpaceAclAction.EDIT));
        assertTrue(WikiSpaceAclAction.EDIT.implies(WikiSpaceAclAction.COMMENT));
    }

    @Test
    void superAdminKeepsExistingWildcardSemanticsOnlyForRestrictedSpaces() {
        WikiActor superAdmin = WikiActor.of(999L, 1L, "超管", true);

        assertTrue(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, List.of(), superAdmin,
                WikiSpaceAclAction.MANAGE));
        // 未认证请求即使用于超管标记也不放行。
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, List.of(),
                WikiActor.of(null, null, "超管", true), WikiSpaceAclAction.READ));
        // 动作缺失时一律拒绝，不做隐式动作补全。
        assertFalse(WikiSpaceAccessResolver.permits(WikiSpaceAclMode.RESTRICTED, List.of(), superAdmin, null));
    }

    @Test
    void managerInvariantDetectsLockoutRisk() {
        assertFalse(WikiSpaceAccessResolver.hasManager(List.of(user(1L, 100L, WikiSpaceAclAction.EDIT))));
        assertFalse(WikiSpaceAccessResolver.hasManager(List.of()));
        assertTrue(WikiSpaceAccessResolver.hasManager(List.of(role(2L, 7L, WikiSpaceAclAction.MANAGE))));
    }

    private static WikiSpaceMember user(Long id, Long userId, WikiSpaceAclAction action) {
        WikiSpaceMember member = WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.USER, userId, List.of(action), 1L);
        member.setId(id);
        return member;
    }

    private static WikiSpaceMember role(Long id, Long roleId, WikiSpaceAclAction action) {
        WikiSpaceMember member = WikiSpaceMember.create(1L, WikiSpaceMemberSubjectType.ROLE, roleId, List.of(action), 1L);
        member.setId(id);
        return member;
    }
}
