package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleColor;
import online.yudream.base.infra.system.user.dataobj.RoleDO;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 角色领域对象与数据对象转换器。
 */
@NoArgsConstructor
public class RoleInfraMapper {

    public static RoleDO toDataObj(Role role) {
        if (role == null) return null;
        RoleDO roleDO = new RoleDO();
        roleDO.setId(role.getId());
        roleDO.setName(role.getName());
        roleDO.setDeptId(role.getDeptId() == null ? null : role.getDeptId().getValue());
        roleDO.setCode(role.getCode());
        roleDO.setLevel(role.getLevel());
        roleDO.setSystemRole(role.isSystemRole());
        roleDO.setSystemType(role.getSystemType());
        roleDO.setIcon(role.getIcon());
        roleDO.setColor(role.getColor());
        roleDO.setPermissions(role.getPermissions() == null ? Collections.emptyList() :
                role.getPermissions().stream().map(PermissionID::getCode).collect(Collectors.toList()));
        roleDO.setStatus(role.getStatus());
        roleDO.setVersion(role.getVersion());
        roleDO.setCreateTime(role.getCreateTime());
        roleDO.setUpdateTime(role.getUpdateTime());
        return roleDO;
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
                .icon(roleDO.getIcon())
                // 旧数据可能仍是 info/success 等语义键，统一经 RoleColor 规范化为 hex
                .color(RoleColor.normalize(roleDO.getColor()))
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
