package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.aggregate.WikiSpaceMember;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclMode;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;

import java.util.Collection;
import java.util.List;

/**
 * 空间实例 ACL 判定（纯领域规则，无框架依赖）。
 * <p>
 * fail closed：LEGACY 空间保持既有全局权限行为；RESTRICTED 空间必须命中成员授权，
 * 主体缺失（未登录/无生效角色）、成员缺失、动作不覆盖一律拒绝。
 * 超级管理员按既有 {@code *} 语义放行，不额外扩大权限。
 */
public final class WikiSpaceAccessResolver {

    private WikiSpaceAccessResolver() {
    }

    public static boolean permits(WikiSpaceAclMode mode, Collection<WikiSpaceMember> members, WikiActor actor,
                                  WikiSpaceAclAction action) {
        if (action == null) {
            return false;
        }
        if (WikiSpaceAclMode.of(mode) == WikiSpaceAclMode.LEGACY) {
            // 兼容模式：实例 ACL 不参与判定，调用方仍需通过全局权限码闸门。
            return true;
        }
        if (actor == null || !actor.authenticated()) {
            return false;
        }
        if (actor.superAdmin()) {
            return true;
        }
        return members(actor, members).stream().anyMatch(member -> member.permits(actor, action));
    }

    /** 过滤出与本主体相关（用户或当前生效角色）的成员记录。 */
    public static List<WikiSpaceMember> members(WikiActor actor, Collection<WikiSpaceMember> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().filter(member -> member.matchSubject(actor)).toList();
    }

    /** RESTRICTED 空间是否仍有可管理成员（避免启用 ACL 后锁死）。 */
    public static boolean hasManager(Collection<WikiSpaceMember> members) {
        return members != null && members.stream().anyMatch(WikiSpaceMember::manages);
    }
}
