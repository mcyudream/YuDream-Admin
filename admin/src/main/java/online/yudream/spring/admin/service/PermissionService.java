package online.yudream.spring.admin.service;

import online.yudream.spring.entity.entity.Permission;

import java.util.List;

public interface PermissionService {
    List<String> findByRole(String roleId);

    List<Permission> getAllPermissions();

    void setPermissions(List<String> permissionIds, String roleId);

    Permission addPermission(Permission permission);
}
