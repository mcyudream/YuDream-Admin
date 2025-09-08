package online.yudream.spring.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.PermissionService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.entity.entity.Permission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permission")
public class PermissionController {
    @Resource
    private PermissionService permissionService;

    @SaCheckPermission("permission.findByRole")
    @GetMapping("/{role}")
    public R<List<String>> findByRole(@PathVariable(name = "role") String roleId){
        return R.success(permissionService.findByRole(roleId));
    }

    @SaCheckPermission("permission.selectAll")
    @GetMapping
    public R<List<Permission>> findAll(){
        return R.success(permissionService.getAllPermissions());
    }


    @SaCheckPermission("permission.set")
    @PostMapping("/{role}")
    public R<List<Permission>> setPermissions(@RequestBody List<String> permissions, @PathVariable(name = "role")  String roleId){
        permissionService.setPermissions(permissions,roleId);
        return R.success();
    }

    @SaCheckPermission("permission.add")
    @PutMapping
    public R<Permission> addPermission(@RequestBody Permission permission){
        return R.success(permissionService.addPermission(permission));
    }
}
