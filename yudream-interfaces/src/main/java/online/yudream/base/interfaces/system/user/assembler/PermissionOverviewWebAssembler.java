package online.yudream.base.interfaces.system.user.assembler;

import online.yudream.base.application.system.user.dto.PermissionOverviewDTO;
import online.yudream.base.interfaces.system.user.res.PermissionOverviewRes;
import online.yudream.base.interfaces.system.user.res.PermissionOverviewRes.MenuNodeRes;
import online.yudream.base.interfaces.system.user.res.PermissionOverviewRes.StatisticsRes;
import online.yudream.base.interfaces.system.user.res.PermissionOverviewRes.UserNodeRes;

public final class PermissionOverviewWebAssembler {

    private PermissionOverviewWebAssembler() {
    }

    public static PermissionOverviewRes toRes(PermissionOverviewDTO dto) {
        var stats = dto.stats();
        return new PermissionOverviewRes(dto.roles().stream().map(UserManageWebAssembler::toRes).toList(),
                UserManageWebAssembler.toPermissionResList(dto.permissions()),
                dto.users().stream().map(user -> new UserNodeRes(user.id(), user.username(), user.nickname(),
                        user.roleIds(), user.deptIds(), user.status(), user.emailVerified(), user.currentRoleId(), user.currentDeptId(), user.contextSource())).toList(),
                dto.menus().stream().map(menu -> new MenuNodeRes(menu.code(), menu.name(), menu.type(), menu.parentCode(),
                        menu.permission(), menu.runtimeAvailable(), menu.status(), menu.path(), menu.roleIds())).toList(),
                new StatisticsRes(stats.userCount(), stats.roleCount(), stats.menuCount(), stats.idleRoleCount(),
                        stats.unassignedUserCount(), stats.ungrantedMenuCount(), stats.assignmentCount()),
                dto.userPage(), dto.userSize(), dto.userTotal(), dto.canViewUsers(), dto.canViewMenus(), dto.idleRoleIds());
    }
}
