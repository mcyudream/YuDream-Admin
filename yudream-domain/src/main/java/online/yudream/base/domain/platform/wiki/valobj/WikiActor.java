package online.yudream.base.domain.platform.wiki.valobj;

import java.util.Objects;

/**
 * 当前请求的认证主体快照（ACL 判定输入）。
 * <p>
 * {@code userId} 与 {@code roleId}（当前生效角色）是成员授权的匹配依据，只能由认证上下文提供，
 * 禁止由请求参数推断；{@code superAdmin} 只表达既有 {@code *} 语义，不额外放大权限。
 *
 * @param userId     登录用户 ID；未登录为 null
 * @param roleId     当前生效角色 ID；无法解析为 null
 * @param displayName 展示名快照（评论作者名等），不得作为权限判定依据
 * @param superAdmin 当前生效角色是否为超级管理员（对应既有 {@code *} 权限语义）
 */
public record WikiActor(Long userId, Long roleId, String displayName, boolean superAdmin) {

    public WikiActor {
        displayName = displayName == null ? "" : displayName.trim();
    }

    /** 已认证但无有效角色的主体：仍可用于按用户 ID 精确匹配的成员授权。 */
    public static WikiActor of(Long userId, Long roleId, String displayName, boolean superAdmin) {
        return new WikiActor(userId, roleId, displayName, superAdmin);
    }

    public boolean authenticated() {
        return userId != null;
    }

    public boolean matchesUser(Long subjectId) {
        return subjectId != null && Objects.equals(subjectId, userId);
    }

    public boolean matchesRole(Long subjectId) {
        return subjectId != null && Objects.equals(subjectId, roleId);
    }
}
