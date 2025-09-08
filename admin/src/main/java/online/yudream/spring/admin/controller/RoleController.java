package online.yudream.spring.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.RoleService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.entity.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/role")
public class RoleController {
    @Resource
    private RoleService roleService;

    @SaCheckPermission("permission.selectAll")
    @PostMapping("/page")
    public R<Page<Role>> getAllRoles(@RequestBody SearchPageDto searchPageDto) {
        return R.success(roleService.getAllRoles(searchPageDto));
    }

    @SaCheckPermission("permission.add")
    @PutMapping
    public R<Role> createRole(@RequestBody Role role) {
        return R.success(roleService.createRole(role));
    }

    @SaCheckPermission("permission.delete")
    @DeleteMapping("/{id}")
    public R<String> deleteRole(@PathVariable(name = "id") String id) {
        roleService.deleteRole(id);
        return R.success();
    }

    @SaCheckPermission("permission.update")
    @PostMapping
    public R<String > updateRole(@RequestBody Role role) {
        roleService.editRole(role);
        return R.success();
    }
}
