package online.yudream.spring.admin.controller;

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

    @GetMapping("/{role}")
    public R<List<String>> findByRole(@PathVariable(name = "role") String roleId){
        return R.success(permissionService.findByRole(roleId));
    }

    @GetMapping
    public R<List<Permission>> findAll(){
        return R.success(permissionService.getAllPermissions());
    }

    @PostMapping("/{role}")
    public R<List<Permission>> setPermissions(@RequestBody List<String> permissions, @PathVariable(name = "role")  String roleId){
        permissionService.setPermissions(permissions,roleId);
        return R.success();
    }

    @PutMapping
    public R<Permission> addPermission(@RequestBody Permission permission){
        return R.success(permissionService.addPermission(permission));
    }
}
