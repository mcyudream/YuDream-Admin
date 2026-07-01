package online.yudream.base.infra.system.user.mapper;

import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.infra.system.user.dataobj.PermissionDO;

/**
 * 权限领域对象与数据对象转换器。
 */
@NoArgsConstructor
public class PermissionInfraMapper {

    public static PermissionDO toDataObj(Permission permission) {
        if (permission == null) return null;
        String code = permission.getId() == null ? null : permission.getId().getCode();
        return PermissionDO.builder()
                .code(code)
                .name(permission.getName())
                .module(permission.getModule())
                .description(permission.getDescription())
                .status(permission.getStatus())
                .build();
    }

    public static Permission toDomain(PermissionDO permissionDO) {
        if (permissionDO == null) return null;
        Permission permission = Permission.create(
                permissionDO.getCode(),
                permissionDO.getName(),
                permissionDO.getModule(),
                permissionDO.getDescription()
        );
        permission.setStatus(permissionDO.getStatus());
        return permission;
    }
}
