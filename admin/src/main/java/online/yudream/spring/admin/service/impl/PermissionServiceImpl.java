package online.yudream.spring.admin.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.PermissionService;
import online.yudream.spring.base.exception.NotFoundException;
import online.yudream.spring.entity.entity.Permission;
import online.yudream.spring.entity.entity.Role;
import online.yudream.spring.entity.mapper.PermissionMapper;
import online.yudream.spring.entity.mapper.RoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {
    @Resource
    private PermissionMapper permissionMapper;
    @Resource
    private RoleMapper roleMapper;

    @Override
    public List<String> findByRole(String roleId){
        Role role = roleMapper.findById(roleId).orElse(null);
        if (role == null) {
            throw new NotFoundException();
        }
        return role.getPermissionId();
    }

    @Override
    public List<Permission> getAllPermissions(){
        return permissionMapper.findAll();
    }

    @Override
    public void setPermissions(List<String> permissionIds, String roleId){
        Role role = roleMapper.findById(roleId).orElse(null);
        if (role == null) {
            throw new NotFoundException();
        }
        role.setPermissionId(permissionIds);
        roleMapper.save(role);
    }

    @Override
    public Permission addPermission(Permission permission){
        permissionMapper.save(permission);
        return permission;
    }
}
