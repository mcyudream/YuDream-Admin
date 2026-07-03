package online.yudream.base.domain.system.security.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.List;
import java.util.Objects;

public record PermissionScope(List<String> permissions) {

    public PermissionScope {
        permissions = permissions == null ? List.of() : permissions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(permission -> !permission.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (permissions.isEmpty()) {
            throw new BizException("API Key 权限范围不能为空");
        }
    }

    public boolean contains(String permission) {
        return permission != null && permissions.contains(permission);
    }

    public boolean isWithin(List<String> availablePermissions) {
        if (availablePermissions == null || availablePermissions.isEmpty()) {
            return false;
        }
        return availablePermissions.contains("*") || availablePermissions.containsAll(permissions);
    }
}
