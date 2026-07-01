package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.infra.system.user.dataobj.RoleDO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 角色领域对象与数据对象转换器。
 */
@NoArgsConstructor
public class RoleInfraMapper {

    public static RoleDO toDataObj(Role role) {
        if (role == null) return null;
        return RoleDO.builder()
                .id(role.getId())
                .name(role.getName())
                .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                .code(role.getCode())
                .level(role.getLevel())
                .systemRole(role.isSystemRole())
                .systemType(role.getSystemType())
                .permissions(role.getPermissions() == null ? Collections.emptyList() :
                        role.getPermissions().stream().map(PermissionID::getCode).collect(Collectors.toList()))
                .status(role.getStatus())
                .version(role.getVersion())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    public static Role toDomain(RoleDO roleDO) {
        if (roleDO == null) return null;
        return Role.builder()
                .id(roleDO.getId())
                .name(roleDO.getName())
                .deptId(roleDO.getDeptId() == null ? null : DeptID.of(roleDO.getDeptId()))
                .code(roleDO.getCode())
                .level(roleDO.getLevel())
                .systemRole(roleDO.isSystemRole())
                .systemType(roleDO.getSystemType())
                .permissions(Optional.ofNullable(roleDO.getPermissions()).orElse(Collections.emptyList()).stream()
                        .map(PermissionID::of)
                        .collect(Collectors.toList()))
                .status(roleDO.getStatus())
                .version(roleDO.getVersion())
                .createTime(roleDO.getCreateTime())
                .updateTime(roleDO.getUpdateTime())
                .build();
    }
}
