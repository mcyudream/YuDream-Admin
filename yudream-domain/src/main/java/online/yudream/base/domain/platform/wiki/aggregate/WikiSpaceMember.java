package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceMemberSubjectType;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库空间成员授权（实例 ACL 数据，不写入 sysPermission，也不把 spaceId 拼进权限码）。
 * <p>
 * 主体支持 USER（登录用户 ID）与 ROLE（当前生效角色 ID）；动作按
 * {@link WikiSpaceAclAction} 的包含关系生效。更新必须携带期望版本（CAS），
 * 避免并发覆盖其他人的授权调整。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiSpaceMember extends BaseDomain {

    private Long spaceId;
    private WikiSpaceMemberSubjectType subjectType;
    private Long subjectId;
    private List<WikiSpaceAclAction> actions;
    private Long createdByUserId;

    public static WikiSpaceMember create(Long spaceId, WikiSpaceMemberSubjectType subjectType, Long subjectId,
                                         Collection<WikiSpaceAclAction> actions, Long createdByUserId) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (subjectType == null) {
            throw new BizException("成员主体类型不能为空");
        }
        if (subjectId == null) {
            throw new BizException("成员主体不能为空");
        }
        return WikiSpaceMember.builder()
                .spaceId(spaceId)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .actions(normalizeActions(actions))
                .createdByUserId(createdByUserId)
                .build();
    }

    /** CAS 更新动作集合：期望版本不一致表示授权已被他人调整，拒绝覆盖。 */
    public void replaceActions(Collection<WikiSpaceAclAction> actions, int expectedVersion) {
        if (expectedVersion != getVersion()) {
            throw new BizException("成员授权已被调整，请刷新后重试");
        }
        List<WikiSpaceAclAction> next = normalizeActions(actions);
        List<WikiSpaceAclAction> current = normalizeActions(this.actions);
        if (current.equals(next)) {
            return;
        }
        this.actions = next;
    }

    /** 主体是否命中该动作（含高阶动作的包含语义）。 */
    public boolean permits(WikiActor actor, WikiSpaceAclAction action) {
        if (actor == null || action == null || !matchSubject(actor)) {
            return false;
        }
        return normalizeActions(actions).stream().anyMatch(granted -> granted.implies(action));
    }

    public boolean matchSubject(WikiActor actor) {
        if (actor == null || subjectType == null) {
            return false;
        }
        return switch (subjectType) {
            case USER -> actor.matchesUser(subjectId);
            case ROLE -> actor.matchesRole(subjectId);
        };
    }

    /** 是否持有 MANAGE 能力（用于「受限空间必须保留可管理成员」不变量判定）。 */
    public boolean manages() {
        return normalizeActions(actions).contains(WikiSpaceAclAction.MANAGE);
    }

    private static List<WikiSpaceAclAction> normalizeActions(Collection<WikiSpaceAclAction> actions) {
        if (actions == null || actions.isEmpty()) {
            throw new BizException("成员动作不能为空");
        }
        Set<WikiSpaceAclAction> normalized = new LinkedHashSet<>();
        for (WikiSpaceAclAction action : actions) {
            if (action == null) {
                throw new BizException("成员动作不能为空");
            }
            normalized.add(action);
        }
        return new ArrayList<>(normalized);
    }
}
