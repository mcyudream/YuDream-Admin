package online.yudream.base.application.system.user.assembler;

import online.yudream.base.application.system.user.dto.PermissionOverviewDTO.MenuNodeDTO;
import online.yudream.base.application.system.user.dto.PermissionOverviewDTO.UserNodeDTO;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;

import java.util.List;
import java.util.Objects;

public final class PermissionOverviewAssembler {

    private PermissionOverviewAssembler() {
    }

    public static UserNodeDTO toUserDTO(User user, List<Role> roles, Long selectedDeptId, Long selectedRoleId) {
        DeptID deptId = user.resolveContextDeptID(selectedDeptId);
        RoleID roleId = user.resolveContextRole(deptId, selectedRoleId, roles);
        return new UserNodeDTO(String.valueOf(user.getId()), user.getUsername(), user.getNickname(),
                user.getRoles() == null ? List.of() : user.getRoles().stream().filter(Objects::nonNull)
                        .map(RoleID::getValue).filter(Objects::nonNull).map(String::valueOf).distinct().toList(),
                user.getDepts() == null ? List.of() : user.getDepts().stream().filter(Objects::nonNull)
                        .map(item -> String.valueOf(item.id().getValue())).distinct().toList(),
                user.getStatus(), user.isEmailVerified(),
                roleId == null ? null : String.valueOf(roleId.getValue()),
                deptId == null ? null : String.valueOf(deptId.getValue()),
                selectedDeptId != null || selectedRoleId != null ? "SESSION" : "DEFAULT");
    }

    public static MenuNodeDTO toMenuDTO(Menu menu, boolean runtimeAvailable, List<String> roleIds) {
        return new MenuNodeDTO(menu.getCode(), menu.getName(), menu.getType(), menu.getParentCode(),
                menu.getPermissionCode(), runtimeAvailable, menu.getStatus(), menu.getPath(), roleIds);
    }
}
