package online.yudream.base.interfaces.system.user.res;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.util.List;

public record PermissionOverviewRes(List<RoleManageRes> roles, List<PermissionRes> permissions,
                                    List<UserNodeRes> users, List<MenuNodeRes> menus, StatisticsRes stats,
                                    int userPage, int userSize,
                                    @JsonSerialize(as = Number.class) Long userTotal,
                                    boolean canViewUsers, boolean canViewMenus, List<String> idleRoleIds) {

    public record UserNodeRes(String id, String username, String nickname, List<String> roleIds,
                              List<String> deptIds, UserStatus status, boolean emailVerified,
                              String currentRoleId, String currentDeptId, String contextSource) {
    }

    public record MenuNodeRes(String code, String name, MenuNodeType type, String parentCode,
                              String permission, boolean runtimeAvailable, MenuStatus status,
                              String path, List<String> roleIds) {
    }

    public record StatisticsRes(
            @JsonSerialize(as = Number.class) Long userCount, int roleCount,
            @JsonSerialize(as = Number.class) Long menuCount,
            @JsonSerialize(as = Number.class) Long idleRoleCount,
            @JsonSerialize(as = Number.class) Long unassignedUserCount,
            @JsonSerialize(as = Number.class) Long ungrantedMenuCount,
            @JsonSerialize(as = Number.class) Long assignmentCount) {
    }
}
