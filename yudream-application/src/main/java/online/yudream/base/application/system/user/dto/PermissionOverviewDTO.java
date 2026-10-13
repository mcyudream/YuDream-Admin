package online.yudream.base.application.system.user.dto;

import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.util.List;

public record PermissionOverviewDTO(List<RoleManageDTO> roles, List<PermissionDTO> permissions,
                                    List<UserNodeDTO> users, List<MenuNodeDTO> menus, StatisticsDTO stats,
                                    int userPage, int userSize, Long userTotal,
                                    boolean canViewUsers, boolean canViewMenus, List<String> idleRoleIds) {

    public record UserNodeDTO(String id, String username, String nickname, List<String> roleIds,
                              List<String> deptIds, UserStatus status, boolean emailVerified,
                              String currentRoleId, String currentDeptId, String contextSource) {
    }

    public record MenuNodeDTO(String code, String name, MenuNodeType type, String parentCode,
                              String permission, boolean runtimeAvailable, MenuStatus status,
                              String path, List<String> roleIds) {
    }

    public record StatisticsDTO(Long userCount, int roleCount, Long menuCount, Long idleRoleCount,
                                Long unassignedUserCount, Long ungrantedMenuCount, Long assignmentCount) {
    }
}
