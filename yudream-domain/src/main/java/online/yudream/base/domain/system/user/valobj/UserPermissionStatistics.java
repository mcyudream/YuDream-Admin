package online.yudream.base.domain.system.user.valobj;

import java.util.Set;

/** 用户配置的角色关系统计，包含停用用户，不代表同时生效的会话权限。 */
public record UserPermissionStatistics(long userCount, long unassignedUserCount,
                                       long assignmentCount, Set<Long> assignedRoleIds) {
}
