package online.yudream.base.application.system.user.assembler;

import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.dto.PermissionDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.UserDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserAssembler {

    private UserAssembler() {
    }

    public static UserDTO toDTO(User user) {
        return UserDTO.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .build();
    }

    public static UserProfileDTO toProfileDTO(User user, String avatar) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .avatarFileId(user.getAvatarFileId())
                .avatar(avatar)
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    public static UserRegisterDTO toRegisterDTO(User user) {
        return UserRegisterDTO.builder()
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    public static UserManageDTO toManageDTO(User user, Map<Long, Role> roleMap, Map<Long, Dept> deptMap) {
        List<Long> deptIds = user.getDepts().stream().map(d -> d.id().getValue()).toList();
        return UserManageDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .roleIds(user.getRoles().stream().map(RoleID::getValue).toList())
                .roleNames(user.getRoles().stream().map(r -> roleMap.get(r.getValue())).filter(r -> r != null).map(Role::getName).toList())
                .deptIds(deptIds)
                .deptNames(deptIds.stream().map(deptMap::get).filter(d -> d != null).map(Dept::getName).toList())
                .defaultDeptId(user.getDefaultDeptID() == null ? null : user.getDefaultDeptID().getValue())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

    public static RoleManageDTO toRoleManageDTO(Role role, Map<Long, Dept> deptMap) {
        Dept dept = role.getDeptId() == null ? null : deptMap.get(role.getDeptId().getValue());
        return RoleManageDTO.builder()
                .id(role.getId())
                .name(role.getName())
                .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                .deptName(dept == null ? null : dept.getName())
                .code(role.getCode())
                .level(role.getLevel())
                .systemRole(role.isSystemRole())
                .systemType(role.getSystemType())
                .permissions(role.getPermissions() == null ? new ArrayList<>() : role.getPermissions().stream().map(PermissionID::getCode).toList())
                .permissionCount(role.getPermissions() == null ? 0 : role.getPermissions().size())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    public static DeptManageDTO toDeptManageDTO(Dept dept, User leader) {
        return DeptManageDTO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .description(dept.getDescription())
                .leaderId(dept.getLeader() == null ? null : dept.getLeader().getValue())
                .leaderName(leader == null ? null : leader.getNickname())
                .phone(dept.getPhone() == null ? null : dept.getPhone().getValue())
                .parentId(dept.getParentId() == null ? null : dept.getParentId().getValue())
                .sortOrder(dept.getSortOrder())
                .deptType(dept.getDeptType())
                .status(dept.getStatus())
                .systemDept(dept.isSystem() || dept.isRoot())
                .createTime(dept.getCreateTime())
                .updateTime(dept.getUpdateTime())
                .build();
    }

    public static OptionDTO toOptionDTO(Long id, String label) {
        return OptionDTO.builder()
                .id(id)
                .label(label)
                .value(String.valueOf(id))
                .build();
    }

    public static OptionDTO toRoleOptionDTO(Role role, Dept dept) {
        return OptionDTO.builder()
                .id(role.getId())
                .label(role.getName())
                .value(String.valueOf(role.getId()))
                .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                .deptName(dept == null ? null : dept.getName())
                .build();
    }

    public static PermissionDTO toPermissionDTO(Permission permission) {
        return PermissionDTO.builder()
                .code(permission.getId().getCode())
                .name(permission.getName())
                .module(permission.getModule())
                .desc(permission.getDescription())
                .status(permission.getStatus() == null ? PermissionStatus.ACTIVE : permission.getStatus())
                .build();
    }
}
